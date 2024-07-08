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

import com.palantir.delegate.processors.AnnotatedType;
import com.palantir.delegate.processors.AnnotatedTypeMethod;
import com.palantir.delegate.processors.DelegateProcessorStrategy;
import com.palantir.delegate.processors.LocalVariable;
import com.palantir.tritium.annotations.Instrument;
import com.palantir.tritium.annotations.internal.InstrumentationBuilder;
import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.event.Handlers;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

enum TritiumAnnotationProcessorStrategy implements DelegateProcessorStrategy {
    INSTANCE;

    private static final String FILTER_NAME = "filter";
    private static final String HANDLER_NAME = "handler";
    private static final String CONTEXT_NAME = "_invocationContext";

    @Override
    public Set<String> supportedAnnotations() {
        return Set.of(Instrument.class.getName());
    }

    @Override
    public String generatedTypeName(String annotatedTypeName) {
        return "Instrumented" + annotatedTypeName;
    }

    @Override
    public TypeName delegateType(DelegateTypeArguments arguments) {
        TypeElement type = arguments.type().type();
        TypeName typeName = TypeName.get(type.asType());

        if (type.getKind() != ElementKind.INTERFACE) {
            arguments
                    .context()
                    .messager()
                    .printMessage(
                            Kind.ERROR,
                            "Only interfaces may be instrumented using @" + Instrument.class.getSimpleName(),
                            arguments.type().type());
            return typeName;
        }

        List<? extends TypeMirror> interfaces = type.getInterfaces();
        if (interfaces.size() != 1) {
            return typeName;
        }

        if (type.getEnclosedElements().stream().anyMatch(element -> !isOverride(element))) {
            return typeName;
        }

        TypeMirror superType = interfaces.get(0);
        TypeName superTypeName = TypeName.get(superType);
        if (!Objects.equals(TypeNames.typeParameters(typeName), TypeNames.typeParameters(superTypeName))) {
            return typeName;
        }

        return superTypeName;
    }

    @Override
    public Optional<CodeBlock> before(DelegateMethodArguments arguments) {
        if (!isInstrumented(arguments)) {
            return Optional.empty();
        }

        ExecutableElement method = arguments.method().implementation();

        CodeBlock parameters = method.getParameters().stream()
                .map(param -> CodeBlock.of("$N", param.getSimpleName()))
                .collect(CodeBlock.joining(","));

        return Optional.of(CodeBlock.builder()
                .addStatement(CodeBlock.builder()
                        .add("$T $N = this.$N.isEnabled()", InvocationContext.class, CONTEXT_NAME, HANDLER_NAME)
                        .add(
                                "? $T.pre(this.$N, this.$N, this, $N, new Object[]{$L})",
                                Handlers.class,
                                HANDLER_NAME,
                                FILTER_NAME,
                                Names.methodFieldName(method),
                                parameters)
                        .add(": $T.disabled()", Handlers.class)
                        .build())
                .build());
    }

    @Override
    public Optional<CodeBlock> onSuccess(DelegateMethodArguments arguments, Optional<LocalVariable> result) {
        if (!isInstrumented(arguments)) {
            return Optional.empty();
        }

        CodeBlock.Builder builder = CodeBlock.builder();
        result.ifPresentOrElse(
                variable -> {
                    builder.addStatement(
                            "$T.onSuccess(this.$N, $N, $N)",
                            Handlers.class,
                            HANDLER_NAME,
                            CONTEXT_NAME,
                            variable.name());
                },
                () -> {
                    builder.addStatement("$T.onSuccess(this.$N, $N)", Handlers.class, HANDLER_NAME, CONTEXT_NAME);
                });
        return Optional.of(builder.build());
    }

    @Override
    public Optional<CodeBlock> onFailure(DelegateMethodArguments arguments, LocalVariable throwable) {
        if (!isInstrumented(arguments)) {
            return Optional.empty();
        }

        return Optional.of(CodeBlock.builder()
                .addStatement(
                        "$T.onFailure(this.$N, $N, $N)", Handlers.class, HANDLER_NAME, CONTEXT_NAME, throwable.name())
                .build());
    }

    @Override
    public List<FieldSpec> additionalFields(AdditionalFieldsArguments arguments) {
        List<AnnotatedTypeMethod> instrumentedMethods = instrumentedMethods(arguments.type());

        List<FieldSpec> fields = new ArrayList<>();

        fields.add(FieldSpec.builder(
                        ParameterizedTypeName.get(InvocationEventHandler.class, InvocationContext.class), HANDLER_NAME)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build());
        fields.add(FieldSpec.builder(InstrumentationFilter.class, FILTER_NAME)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build());

        instrumentedMethods.forEach(method -> {
            fields.add(FieldSpec.builder(Method.class, Names.methodFieldName(method.implementation()))
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .build());
        });

        return fields;
    }

    @Override
    public void customize(CustomizeArguments arguments, TypeSpec.Builder generatedType) {
        TypeElement type = arguments.type().type();
        if (isDeprecated(type)) {
            generatedType.addAnnotation(Deprecated.class);
        }

        SuppressWarnings suppressWarnings = type.getAnnotation(SuppressWarnings.class);
        if (suppressWarnings != null) {
            generatedType.addAnnotation(AnnotationSpec.get(suppressWarnings));
        }

        TypeName annotatedTypeName = TypeName.get(type.asType());
        List<AnnotatedTypeMethod> instrumentedMethods = instrumentedMethods(arguments.type());

        if (!instrumentedMethods.isEmpty()) {
            CodeBlock.Builder staticBlock = CodeBlock.builder().beginControlFlow("try");
            instrumentedMethods.forEach(method -> {
                staticBlock.addStatement(
                        "$N = $T.class.getMethod($L)",
                        Names.methodFieldName(method.implementation()),
                        TypeNames.erased(arguments.delegateTypeName()),
                        Stream.concat(
                                        Stream.of(CodeBlock.of(
                                                "$S", method.implementation().getSimpleName())),
                                        method.implementation().getParameters().stream()
                                                .map(parameter -> CodeBlock.of(
                                                        "$T.class",
                                                        TypeNames.erased(TypeName.get(parameter.asType())))))
                                .collect(CodeBlock.joining(",")));
            });
            staticBlock
                    .nextControlFlow("catch ($T e)", NoSuchMethodException.class)
                    .addStatement("throw new $T(e)", RuntimeException.class)
                    .endControlFlow();
            generatedType.addStaticBlock(staticBlock.build());
        }

        generatedType.addMethod(MethodSpec.methodBuilder("builder")
                .addTypeVariables(generatedType.typeVariables)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ParameterizedTypeName.get(
                        ClassName.get(InstrumentationBuilder.class), arguments.delegateTypeName(), annotatedTypeName))
                .addParameter(ParameterSpec.builder(arguments.delegateTypeName(), "delegate")
                        .build())
                .addStatement(
                        "return new $T<$T, $T>($T.class, delegate, $T::new)",
                        InstrumentationBuilder.class,
                        arguments.delegateTypeName(),
                        annotatedTypeName,
                        TypeNames.erased(arguments.delegateTypeName()),
                        arguments.generatedTypeName())
                .build());
        generatedType.addMethod(MethodSpec.methodBuilder("trace")
                .addTypeVariables(generatedType.typeVariables)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(annotatedTypeName)
                .addParameter(ParameterSpec.builder(arguments.delegateTypeName(), "delegate")
                        .build())
                .addStatement(
                        "return $T.trace($T.class, delegate, $T::new)",
                        InstrumentationBuilder.class,
                        TypeNames.erased(arguments.delegateTypeName()),
                        arguments.generatedTypeName())
                .build());
        generatedType.addMethod(MethodSpec.methodBuilder("instrument")
                .addTypeVariables(generatedType.typeVariables)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(annotatedTypeName)
                .addParameter(ParameterSpec.builder(arguments.delegateTypeName(), "delegate")
                        .build())
                .addParameter(ParameterSpec.builder(TaggedMetricRegistry.class, "registry")
                        .build())
                .addStatement("return builder(delegate).withTaggedMetrics(registry).withTracing().build()")
                .build());
    }

    private static List<AnnotatedTypeMethod> instrumentedMethods(AnnotatedType type) {
        return type.methods().stream()
                .filter(method -> isInstrumented(type, method))
                .collect(Collectors.toUnmodifiableList());
    }

    private static boolean isInstrumented(DelegateMethodArguments arguments) {
        return isInstrumented(arguments.type(), arguments.method());
    }

    private static boolean isInstrumented(AnnotatedType type, AnnotatedTypeMethod method) {
        return type.type().getAnnotation(Instrument.class) != null
                || method.implementation().getAnnotation(Instrument.class) != null;
    }

    private static boolean isOverride(Element element) {
        return element.getAnnotation(Override.class) != null;
    }

    private static boolean isDeprecated(Element element) {
        return element.getAnnotation(Deprecated.class) != null;
    }
}
