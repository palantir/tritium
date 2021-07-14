/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.tritium.processor;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.palantir.goethe.Goethe;
import com.palantir.goethe.GoetheException;
import com.palantir.logsafe.Preconditions;
import com.palantir.tritium.annotations.Instrument;
import com.palantir.tritium.annotations.internal.InstrumentationBuilder;
import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.event.Handlers;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor8;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

public final class TritiumAnnotationProcessor extends AbstractProcessor {

    private static final String DELEGATE_NAME = "delegate";
    private static final String FILTER_NAME = "filter";
    private static final String HANDLER_NAME = "handler";
    private static final ImmutableSet<String> ANNOTATIONS = ImmutableSet.of(Instrument.class.getName());

    private final Set<Name> invalidElements = new HashSet<>();
    private Messager messager;
    private Filer filer;
    private Elements elements;
    private Types types;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public ImmutableSet<String> getSupportedAnnotationTypes() {
        return ANNOTATIONS;
    }

    @Override
    public boolean process(Set<? extends TypeElement> _annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            if (!invalidElements.isEmpty()) {
                messager.printMessage(Kind.ERROR, "Processing completed with unresolved elements: " + invalidElements);
            }
            return false;
        }
        for (Element element : getElementsToProcess(roundEnv)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                messager.printMessage(
                        Kind.ERROR,
                        "Only interfaces may be instrumented using @" + Instrument.class.getSimpleName(),
                        element);
                continue;
            }
            TypeElement typeElement = (TypeElement) element;
            List<DeclaredType> allInterfaces = new ArrayList<>();
            List<DeclaredType> minimalInterfaces = new ArrayList<>();
            if (isInvalid(element.asType(), allInterfaces, minimalInterfaces)) {
                invalidElements.add(typeElement.getQualifiedName());
                continue;
            }
            if (allInterfaces.isEmpty()) {
                messager.printMessage(
                        Kind.ERROR,
                        "Cannot generate a instrumented implementation. "
                                + "The annotated class implements no interfaces.",
                        typeElement);
                continue;
            }
            try {
                JavaFile generatedFile = generate(typeElement, allInterfaces, minimalInterfaces);
                try {
                    Goethe.formatAndEmit(generatedFile, filer);
                } catch (GoetheException e) {
                    messager.printMessage(
                            Kind.ERROR, "Failed to write instrumented class: " + Throwables.getStackTraceAsString(e));
                }
            } catch (RuntimeException e) {
                messager.printMessage(
                        Kind.ERROR, "Failed to generate instrumented class: " + Throwables.getStackTraceAsString(e));
            }
        }
        return false;
    }

    private Set<Element> getElementsToProcess(RoundEnvironment env) {
        Set<Element> currentElements = new HashSet<>(env.getElementsAnnotatedWith(Instrument.class));
        for (Name name : invalidElements) {
            currentElements.add(elements.getTypeElement(name));
        }
        invalidElements.clear();
        return currentElements;
    }

    private boolean isInvalid(
            List<? extends TypeMirror> mirrors,
            List<DeclaredType> allInterfaces,
            @Nullable List<DeclaredType> minimalInterfaces) {
        for (TypeMirror mirror : mirrors) {
            if (isInvalid(mirror, allInterfaces, minimalInterfaces)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the given {@link TypeMirror} has sufficient type information, otherwise it may need to be
     * deferred for another annotation processor to complete.
     */
    private boolean isInvalid(
            TypeMirror mirror, List<DeclaredType> allInterfaces, @Nullable List<DeclaredType> minimalInterfacesFinal) {
        if (mirror.getKind() == TypeKind.ERROR) {
            return true;
        }
        List<DeclaredType> minimalInterfaces = minimalInterfacesFinal;
        if (mirror.getKind() == TypeKind.DECLARED && types.asElement(mirror).getKind() == ElementKind.INTERFACE) {
            allInterfaces.add((DeclaredType) mirror);
            if (minimalInterfaces != null) {
                minimalInterfaces.add((DeclaredType) mirror);
                minimalInterfaces = null;
            }
        }
        return isInvalid(((TypeElement) types.asElement(mirror)).getInterfaces(), allInterfaces, minimalInterfaces);
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private JavaFile generate(
            TypeElement typeElement, List<DeclaredType> allInterfaces, List<DeclaredType> minimalInterfaces) {
        TypeName annotatedType = TypeName.get(typeElement.asType());
        TypeName delegateType =
                TypeElements.unwrapEmptyInterface(elements, typeElement).orElse(annotatedType);
        String packageName =
                elements.getPackageOf(typeElement).getQualifiedName().toString();
        String className = "Instrumented" + typeElement.getSimpleName();
        List<TypeName> interfaceNames = new ArrayList<>(minimalInterfaces.size());
        List<TypeVariableName> typeVarNames = new ArrayList<>();
        for (DeclaredType type : minimalInterfaces) {
            interfaceNames.add(TypeName.get(type));
            for (TypeMirror typeArg : type.getTypeArguments()) {
                if (typeArg instanceof TypeVariable) {
                    typeVarNames.add(TypeVariableName.get((TypeVariable) typeArg));
                }
            }
        }

        TypeSpec.Builder specBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addOriginatingElement(typeElement)
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", getClass().getName())
                        .build());

        if (typeElement.getAnnotation(Deprecated.class) != null) {
            specBuilder.addAnnotation(Deprecated.class);
        }

        specBuilder
                .addSuperinterfaces(interfaceNames)
                .addTypeVariables(typeVarNames)
                .addField(FieldSpec.builder(delegateType, DELEGATE_NAME, Modifier.PRIVATE, Modifier.FINAL)
                        .build())
                .addField(FieldSpec.builder(
                                ParameterizedTypeName.get(InvocationEventHandler.class, InvocationContext.class),
                                HANDLER_NAME,
                                Modifier.PRIVATE,
                                Modifier.FINAL)
                        .build())
                .addField(FieldSpec.builder(InstrumentationFilter.class, FILTER_NAME, Modifier.PRIVATE, Modifier.FINAL)
                        .build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PRIVATE)
                        .addParameter(ParameterSpec.builder(delegateType, DELEGATE_NAME)
                                .build())
                        .addParameter(ParameterSpec.builder(
                                        ParameterizedTypeName.get(
                                                InvocationEventHandler.class, InvocationContext.class),
                                        HANDLER_NAME)
                                .build())
                        .addParameter(ParameterSpec.builder(InstrumentationFilter.class, FILTER_NAME)
                                .build())
                        .addStatement("this.$1N = $1N", DELEGATE_NAME)
                        .addStatement("this.$1N = $1N", HANDLER_NAME)
                        .addStatement("this.$1N = $1N", FILTER_NAME)
                        .build());

        Map<String, List<MethodElements>> methodsByName = new HashMap<>();
        for (DeclaredType mirror : allInterfaces) {
            for (Element methodElement : types.asElement(mirror).getEnclosedElements()) {
                if (!methodElement.getModifiers().contains(Modifier.STATIC)
                        && !methodElement.getModifiers().contains(Modifier.PRIVATE)) {
                    methodElement.accept(
                            new SimpleElementVisitor8<Void, Void>() {
                                @Override
                                public Void visitExecutable(ExecutableElement method, Void _param) {
                                    if (!Methods.isObjectMethod(elements, method)) {
                                        List<MethodElements> methods = methodsByName.computeIfAbsent(
                                                method.getSimpleName().toString(), _key -> new ArrayList<>(1));
                                        methods.add(
                                                new MethodElements(asMemberOf(typeElement, mirror, method), method));
                                    }
                                    return null;
                                }
                            },
                            null);
                }
            }
        }
        List<MethodElements> instrumentedMethods = new ArrayList<>();
        for (List<MethodElements> methods : methodsByName.values()) {
            for (int i = 0; i < methods.size(); i++) {
                if (isMostSpecific(i, methods)) {
                    instrumentedMethods.add(methods.get(i));
                }
            }
        }
        if (instrumentedMethods.isEmpty()) {
            messager.printMessage(
                    Kind.ERROR,
                    "Cannot generate an instrumented implementation. The annotated interface has no methods",
                    typeElement);
        }
        IdentityHashMap<MethodElements, String> methodStaticFields =
                Methods.methodStaticFieldName(instrumentedMethods, specBuilder, annotatedType);
        for (MethodElements method : instrumentedMethods) {
            createMethod(method, specBuilder, methodStaticFields);
        }

        specBuilder
                .addMethod(MethodSpec.methodBuilder("toString")
                        .addAnnotation(Override.class)
                        .returns(String.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return $S + $N + '}'", className + "{", DELEGATE_NAME)
                        .build())
                .addMethod(MethodSpec.methodBuilder("builder")
                        .addTypeVariables(typeVarNames)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(ParameterizedTypeName.get(
                                ClassName.get(InstrumentationBuilder.class), delegateType, annotatedType))
                        .addParameter(ParameterSpec.builder(delegateType, DELEGATE_NAME)
                                .build())
                        .addStatement(
                                "return new $T<$T, $T>($T.class, delegate, $N::new)",
                                InstrumentationBuilder.class,
                                delegateType,
                                annotatedType,
                                TypeNames.erased(delegateType),
                                className)
                        .build())
                .addMethod(MethodSpec.methodBuilder("instrument")
                        .addTypeVariables(typeVarNames)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(annotatedType)
                        .addParameter(ParameterSpec.builder(delegateType, DELEGATE_NAME)
                                .build())
                        .addParameter(ParameterSpec.builder(TaggedMetricRegistry.class, "registry")
                                .build())
                        .addStatement("return builder(delegate).withTaggedMetrics(registry).withTracing().build()")
                        .build());

        if (specBuilder.originatingElements.size() != 1) {
            messager.printMessage(
                    Kind.ERROR,
                    "The generated type must have exactly one originating element: " + specBuilder.originatingElements,
                    typeElement);
        }
        return JavaFile.builder(packageName, specBuilder.build())
                .skipJavaLangImports(true)
                .indent("    ")
                .build();
    }

    private boolean isMostSpecific(int index, List<MethodElements> methods) {
        ExecutableType method = methods.get(index).type();
        for (int j = 0; j < methods.size(); j++) {
            if (index == j) {
                continue;
            }
            ExecutableType other = methods.get(j).type();
            if (types.isSubsignature(other, method)) {
                if (types.isSameType(other.getReturnType(), method.getReturnType())) {
                    return index < j;
                }
                if (types.isSubtype(other.getReturnType(), method.getReturnType())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void createMethod(
            MethodElements method,
            TypeSpec.Builder typeBuilder,
            IdentityHashMap<MethodElements, String> methodToStaticFieldName) {
        String methodName = method.element().getSimpleName().toString();
        TypeName returnType = TypeName.get(method.type().getReturnType());
        boolean isVoidMethod = method.type().getReturnType().getKind() == TypeKind.VOID;
        String parameterString = method.element().getParameters().stream()
                .map(param -> param.getSimpleName().toString())
                .collect(Collectors.joining(", "));

        ImmutableSet<String> parameters = method.element().getParameters().stream()
                .map(param -> param.getSimpleName().toString())
                .collect(ImmutableSet.toImmutableSet());
        String throwableName = Parameters.disambiguate("throwable", parameters);
        String contextName = Parameters.disambiguate("invocationContext", parameters);
        String returnValueName = Parameters.disambiguate("returnValue", parameters);
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariables(Lists.transform(method.element().getTypeParameters(), TypeVariableName::get))
                .returns(returnType)
                .beginControlFlow("if (this.$N.isEnabled())", HANDLER_NAME)
                .addStatement(
                        "$T $N = $T.pre(this.$N, this.$N, this, $N, new Object[]{$L})",
                        InvocationContext.class,
                        contextName,
                        Handlers.class,
                        HANDLER_NAME,
                        FILTER_NAME,
                        Preconditions.checkNotNull(methodToStaticFieldName.get(method), "missing name"),
                        parameterString)
                .beginControlFlow("try");

        if (method.element().getAnnotation(Deprecated.class) != null) {
            methodBuilder = methodBuilder.addAnnotation(Deprecated.class);
        }
        for (int i = 0; i < method.type().getThrownTypes().size(); i++) {
            TypeMirror type = method.type().getThrownTypes().get(i);
            methodBuilder.addException(TypeName.get(type));
        }
        StringBuilder statement = new StringBuilder()
                .append("this.")
                .append(DELEGATE_NAME)
                .append(".")
                .append(methodName)
                .append("(");
        for (int i = 0; i < method.element().getParameters().size(); i++) {
            TypeMirror param = method.type().getParameterTypes().get(i);
            String paramName =
                    method.element().getParameters().get(i).getSimpleName().toString();
            methodBuilder.addParameter(TypeName.get(param), paramName);
        }
        String delegateInvocation =
                statement.append(parameterString).append(")").toString();
        if (isVoidMethod) {
            methodBuilder
                    .addStatement(delegateInvocation)
                    .addStatement("$T.onSuccess(this.$N, $N)", Handlers.class, HANDLER_NAME, contextName);
        } else {
            methodBuilder
                    .addStatement("$T $N = $L", returnType, returnValueName, delegateInvocation)
                    .addStatement("$T.onSuccess(this.$N, $N, returnValue)", Handlers.class, HANDLER_NAME, contextName)
                    .addStatement("return $N", returnValueName);
        }
        methodBuilder
                .nextControlFlow("catch ($T $N)", Throwable.class, throwableName)
                .addStatement("$T.onFailure(this.$N, $N, $N)", Handlers.class, HANDLER_NAME, contextName, throwableName)
                .addStatement("throw $N", throwableName)
                .endControlFlow()
                .nextControlFlow("else")
                .addStatement(isVoidMethod ? "$L" : "return $L", delegateInvocation)
                .endControlFlow();
        typeBuilder.addMethod(methodBuilder.build());
    }

    private ExecutableType asMemberOf(TypeElement typeElement, final DeclaredType mirror, ExecutableElement method) {
        try {
            return (ExecutableType) types.asMemberOf(mirror, method);
        } catch (IllegalArgumentException e) {
            // see
            // https://github.com/google/auto/blob/master/value/src/main/java/com/google/auto/value/processor/EclipseHack.java#L95
            List<? extends Element> allMembers = elements.getAllMembers(typeElement);
            for (Element element : allMembers) {
                if (element.getKind() == ElementKind.METHOD) {
                    ExecutableElement otherMethod = (ExecutableElement) element;
                    if (elements.overrides(otherMethod, method, typeElement)) {
                        return (ExecutableType) otherMethod.asType();
                    }
                }
            }
            throw new IllegalArgumentException(e);
        }
    }
}
