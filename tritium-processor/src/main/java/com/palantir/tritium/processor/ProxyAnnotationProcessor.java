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

import com.google.auto.common.GeneratedAnnotations;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.palantir.tritium.annotations.Proxy;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collection;
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

public final class ProxyAnnotationProcessor extends AbstractProcessor {

    private static final String HANDLER_NAME = "handler";
    private static final ImmutableSet<String> ANNOTATIONS = ImmutableSet.of(Proxy.class.getName());

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
                        Kind.ERROR, "Only interfaces may be proxied using @" + Proxy.class.getSimpleName(), element);
                continue;
            }
            TypeElement typeElement = (TypeElement) element;
            List<DeclaredType> allInterfaces = new ArrayList<>();
            List<DeclaredType> minimalInterfaces = new ArrayList<>();
            if (isInvalid(element.asType(), allInterfaces, minimalInterfaces)) {
                invalidElements.add(typeElement.getQualifiedName());
                continue;
            }
            try {
                JavaFile generatedFile = generate(typeElement, allInterfaces, minimalInterfaces);
                try {
                    generatedFile.writeTo(filer);
                } catch (IOException e) {
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
        Set<Element> currentElements = new HashSet<>(env.getElementsAnnotatedWith(Proxy.class));
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
        String packageName =
                elements.getPackageOf(typeElement).getQualifiedName().toString();
        String className = typeElement.getSimpleName() + "Proxy";
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
                .addOriginatingElement(typeElement);

        GeneratedAnnotations.generatedAnnotation(elements, SourceVersion.latest())
                .ifPresent(te -> specBuilder.addAnnotation(AnnotationSpec.builder(ClassName.get(te))
                        .addMember("value", "$S", getClass().getName())
                        .build()));

        if (typeElement.getAnnotation(Deprecated.class) != null) {
            specBuilder.addAnnotation(Deprecated.class);
        }

        specBuilder
                .addSuperinterfaces(interfaceNames)
                .addTypeVariables(typeVarNames)
                .addField(FieldSpec.builder(InvocationHandler.class, HANDLER_NAME, Modifier.PRIVATE, Modifier.FINAL)
                        .build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PRIVATE)
                        .addParameter(ParameterSpec.builder(InvocationHandler.class, HANDLER_NAME)
                                .build())
                        .addStatement("this.$1N = $1N", HANDLER_NAME)
                        .build());

        Map<String, List<MethodElements>> methodsByName = new HashMap<>();
        allInterfaces.add(types.getDeclaredType(elements.getTypeElement(Object.class.getName())));
        for (DeclaredType mirror : allInterfaces) {
            for (Element methodElement : types.asElement(mirror).getEnclosedElements()) {
                if (!methodElement.getModifiers().contains(Modifier.STATIC)
                        && !methodElement.getModifiers().contains(Modifier.PRIVATE)) {
                    methodElement.accept(
                            new SimpleElementVisitor8<Void, Void>() {
                                @Override
                                public Void visitExecutable(ExecutableElement method, Void _param) {
                                    if (method.getKind() != ElementKind.CONSTRUCTOR
                                            // Avoid attempting to override final methods from Object e.g. wait/notify
                                            && !method.getModifiers().contains(Modifier.FINAL)
                                            // Only pubic methods (avoid clone, finalize)
                                            // Overriding finalize impacts the way objects are garbage collected, and
                                            // can have a dramatic impact on cost.
                                            && method.getModifiers().contains(Modifier.PUBLIC)) {
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
        IdentityHashMap<MethodElements, String> methodStaticFields =
                Methods.methodStaticFieldName(instrumentedMethods, specBuilder, annotatedType);
        for (MethodElements method : instrumentedMethods) {
            createMethod(method, specBuilder, methodStaticFields);
        }

        specBuilder.addMethod(MethodSpec.methodBuilder("of")
                .addTypeVariables(typeVarNames)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(annotatedType)
                .addParameter(ParameterSpec.builder(InvocationHandler.class, HANDLER_NAME)
                        .build())
                .addStatement(
                        typeVarNames.isEmpty() ? "return new $N($N)" : "return new $N<>($N)", className, HANDLER_NAME)
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

    @SuppressWarnings("PreferSafeLoggingPreconditions")
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
        String rethrownName = Parameters.disambiguate("rethrown", parameters);
        String returnedName = Parameters.disambiguate("returned", parameters);
        ImmutableList<TypeName> exceptions =
                method.element().getThrownTypes().stream().map(TypeName::get).collect(ImmutableList.toImmutableList());
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariables(Lists.transform(method.element().getTypeParameters(), TypeVariableName::get))
                .addExceptions(exceptions)
                .returns(returnType);

        if (method.element().getAnnotation(Deprecated.class) != null) {
            methodBuilder = methodBuilder.addAnnotation(Deprecated.class);
        }

        for (int i = 0; i < method.element().getParameters().size(); i++) {
            TypeMirror param = method.type().getParameterTypes().get(i);
            String paramName =
                    method.element().getParameters().get(i).getSimpleName().toString();
            methodBuilder.addParameter(TypeName.get(param), paramName);
        }

        methodBuilder
                .beginControlFlow("try")
                .addStatement(
                        "$T $N = this.$N.invoke(this, $N, new Object[]{$L})",
                        Object.class,
                        returnedName,
                        HANDLER_NAME,
                        Preconditions.checkNotNull(methodToStaticFieldName.get(method), "missing name"),
                        parameterString);
        if (isVoidMethod) {
            methodBuilder.addStatement("$T.class.cast($N)", void.class, returnedName);
        } else {
            methodBuilder.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "$S", "unchecked")
                    .build());
            methodBuilder.addStatement("return ($T) $N", returnType, returnedName);
        }
        CodeBlock rethrownExceptions = rethrowableExceptions(exceptions).stream()
                .map(exceptionType ->
                        CodeBlock.builder().add("$T", exceptionType).build())
                .collect(CodeBlock.joining(" | "));
        methodBuilder
                .nextControlFlow("catch ($L $N)", rethrownExceptions, rethrownName)
                .addStatement("throw $N", rethrownName);
        if (!exceptions.contains(TypeNames.THROWABLE)) {
            methodBuilder
                    .nextControlFlow("catch ($T $N)", Throwable.class, throwableName)
                    .addStatement("throw new $T($N)", UndeclaredThrowableException.class, throwableName);
        }
        methodBuilder.endControlFlow();
        typeBuilder.addMethod(methodBuilder.build());
    }

    private static ImmutableSet<TypeName> rethrowableExceptions(Collection<TypeName> declaredCheckedThrowables) {
        boolean declaresThrowable = declaredCheckedThrowables.contains(TypeNames.THROWABLE);
        boolean declaresException = declaredCheckedThrowables.contains(TypeNames.EXCEPTION);
        if (declaresThrowable) {
            return ImmutableSet.copyOf(declaredCheckedThrowables);
        }
        ImmutableSet.Builder<TypeName> builder = ImmutableSet.<TypeName>builder()
                .addAll(declaredCheckedThrowables)
                .add(TypeNames.ERROR);
        if (!declaresException) {
            builder.add(TypeNames.RUNTIME_EXCEPTION);
        }
        return builder.build();
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
