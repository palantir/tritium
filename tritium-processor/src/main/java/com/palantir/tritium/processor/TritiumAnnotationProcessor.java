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

import com.palantir.delegate.processors.AdditionalFieldsStrategy.AdditionalFieldsStrategyArguments;
import com.palantir.delegate.processors.CustomizationStrategy.CustomizationStrategyArguments;
import com.palantir.delegate.processors.DelegateProcessor;
import com.palantir.delegate.processors.DelegateProcessorConfig;
import com.palantir.delegate.processors.DelegationStrategy.DelegationStrategyArguments;
import com.palantir.delegate.processors.TypingStrategy.TypingStrategyArguments;
import com.palantir.delegate.processors.model.AnnotatedType;
import com.palantir.delegate.processors.model.AnnotatedTypeMethod;
import com.palantir.tritium.annotations.Instrument;
import com.palantir.tritium.annotations.internal.InstrumentationBuilder;
import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.event.Handlers;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

public final class TritiumAnnotationProcessor extends DelegateProcessor {

    private static final String DELEGATE_NAME = "delegate";
    private static final String FILTER_NAME = "filter";
    private static final String HANDLER_NAME = "handler";

    public TritiumAnnotationProcessor() {
        super(DelegateProcessorConfig.builder()
                .naming(annotated -> "Instrumented" + annotated)
                .typing(TritiumAnnotationProcessor::typing)
                .fields(TritiumAnnotationProcessor::fields)
                .delegation(TritiumAnnotationProcessor::delegation)
                .customization(TritiumAnnotationProcessor::customization)
                .addSupportedAnnotations(Instrument.class.getName())
                .build());
    }

    private static Optional<TypeName> typing(TypingStrategyArguments args) {
        if (args.type().type().getKind() != ElementKind.INTERFACE) {
            args.context()
                    .messager()
                    .printMessage(
                            Kind.ERROR,
                            "Only interfaces may be instrumented using @" + Instrument.class.getSimpleName(),
                            args.type().type());
            return Optional.empty();
        }

        return TypeElements.unwrapEmptyInterface(args.type().type());
    }

    private static List<FieldSpec> fields(AdditionalFieldsStrategyArguments args) {
        List<AnnotatedTypeMethod> instrumentedMethods = instrumentedMethods(args.type());

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

    private static Optional<MethodSpec> delegation(DelegationStrategyArguments args) {
        if (!isInstrumented(args.type(), args.method())) {
            return Optional.empty();
        }

        ExecutableElement method = args.method().implementation();
        String methodName = method.getSimpleName().toString();

        List<TypeVariableName> typeVariables =
                method.getTypeParameters().stream().map(TypeVariableName::get).collect(Collectors.toUnmodifiableList());

        CodeBlock parameters = method.getParameters().stream()
                .map(param -> CodeBlock.of("$L", param.getSimpleName()))
                .collect(CodeBlock.joining(","));

        TypeName returnType = getReturnType(args);

        Set<String> parameterNames = method.getParameters().stream()
                .map(param -> param.getSimpleName().toString())
                .collect(Collectors.toUnmodifiableSet());
        String contextName = Names.disambiguate("invocationContext", parameterNames);
        String throwableName = Names.disambiguate("throwable", parameterNames);
        String returnValueName = Names.disambiguate("returnValue", parameterNames);

        MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariables(typeVariables)
                .returns(returnType);

        if (isDeprecated(method)) {
            builder.addAnnotation(Deprecated.class);
        }

        method.getParameters().forEach(parameter -> {
            builder.addParameter(
                    TypeName.get(parameter.asType()), parameter.getSimpleName().toString());
        });

        method.getThrownTypes().forEach(thrownType -> {
            builder.addException(TypeName.get(thrownType));
        });

        CodeBlock delegateInvocation = CodeBlock.of("this.$L.$L($L)", args.delegate().name, methodName, parameters);

        builder.beginControlFlow("if (this.$N.isEnabled())", HANDLER_NAME)
                .addStatement(
                        "$T $N = $T.pre(this.$N, this.$N, this, $N, new Object[]{$L})",
                        InvocationContext.class,
                        contextName,
                        Handlers.class,
                        HANDLER_NAME,
                        FILTER_NAME,
                        Names.methodFieldName(method),
                        parameters)
                .beginControlFlow("try");
        if (returnType.equals(TypeName.VOID)) {
            builder.addStatement(delegateInvocation)
                    .addStatement("$T.onSuccess(this.$N, $N)", Handlers.class, HANDLER_NAME, contextName);
        } else {
            builder.addStatement("$T $N = $L", returnType, returnValueName, delegateInvocation)
                    .addStatement(
                            "$T.onSuccess(this.$N, $N, $L)", Handlers.class, HANDLER_NAME, contextName, returnValueName)
                    .addStatement("return $N", returnValueName);
        }
        builder.nextControlFlow("catch ($T $N)", Throwable.class, throwableName)
                .addStatement("$T.onFailure(this.$N, $N, $N)", Handlers.class, HANDLER_NAME, contextName, throwableName)
                .addStatement("throw $N", throwableName)
                .endControlFlow()
                .nextControlFlow("else")
                .addStatement(returnType.equals(TypeName.VOID) ? "$L" : "return $L", delegateInvocation)
                .endControlFlow();

        return Optional.of(builder.build());
    }

    private static void customization(CustomizationStrategyArguments args, TypeSpec.Builder builder) {
        if (isDeprecated(args.type().type())) {
            builder.addAnnotation(Deprecated.class);
        }

        List<AnnotatedTypeMethod> instrumentedMethods = instrumentedMethods(args.type());

        if (!instrumentedMethods.isEmpty()) {
            CodeBlock.Builder staticBlock = CodeBlock.builder().beginControlFlow("try");
            instrumentedMethods.forEach(method -> {
                staticBlock.addStatement(
                        "$N = $T.class.getMethod($L)",
                        Names.methodFieldName(method.implementation()),
                        TypeNames.erased(args.delegateTypeName()),
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
            builder.addStaticBlock(staticBlock.build());
        }

        builder.addMethod(MethodSpec.methodBuilder("builder")
                .addTypeVariables(builder.typeVariables)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ParameterizedTypeName.get(
                        ClassName.get(InstrumentationBuilder.class), args.delegateTypeName(), args.delegateTypeName()))
                .addParameter(ParameterSpec.builder(args.delegateTypeName(), DELEGATE_NAME)
                        .build())
                .addStatement(
                        "return new $T<$T, $T>($T.class, delegate, $T::new)",
                        InstrumentationBuilder.class,
                        args.delegateTypeName(),
                        args.delegateTypeName(),
                        TypeNames.erased(args.delegateTypeName()),
                        args.generatedTypeName())
                .build());
        builder.addMethod(MethodSpec.methodBuilder("trace")
                .addTypeVariables(builder.typeVariables)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(args.delegateTypeName())
                .addParameter(ParameterSpec.builder(args.delegateTypeName(), DELEGATE_NAME)
                        .build())
                .addStatement(
                        "return $T.trace($T.class, delegate, $T::new)",
                        InstrumentationBuilder.class,
                        TypeNames.erased(args.delegateTypeName()),
                        args.generatedTypeName())
                .build());
        builder.addMethod(MethodSpec.methodBuilder("instrument")
                .addTypeVariables(builder.typeVariables)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(args.delegateTypeName())
                .addParameter(ParameterSpec.builder(args.delegateTypeName(), DELEGATE_NAME)
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

    private static boolean isInstrumented(AnnotatedType type, AnnotatedTypeMethod method) {
        return type.type().getAnnotation(Instrument.class) != null
                || method.implementation().getAnnotation(Instrument.class) != null;
    }

    private static boolean isDeprecated(Element element) {
        return element.getAnnotation(Deprecated.class) != null;
    }

    private static TypeName getReturnType(DelegationStrategyArguments args) {
        DeclaredType declaredType = args.context()
                .types()
                .getDeclaredType(
                        args.type().type(),
                        args.type().type().getTypeParameters().stream()
                                .map(TypeParameterElement::asType)
                                .toArray(TypeMirror[]::new));
        ExecutableType executableType = (ExecutableType)
                args.context().types().asMemberOf(declaredType, args.method().implementation());

        return TypeName.get(executableType.getReturnType());
    }
}
