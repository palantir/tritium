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

package com.palantir.tritium.proxy.processor;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.palantir.tritium.proxy.annotations.Proxy;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;

public final class ProxyAnnotationProcessor extends AbstractProcessor {
    private static final String HANDLER_PARAMETER_NAME = "handler";
    private static final ImmutableSet<String> ANNOTATIONS = ImmutableSet.of(Proxy.class.getName());

    private Messager messager;
    private Filer filer;
    private Elements elements;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.elements = processingEnv.getElementUtils();
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
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(Proxy.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                messager.printMessage(
                        Kind.ERROR, "Only interfaces may be proxied using @" + Proxy.class.getSimpleName(), element);
                continue;
            }
            TypeElement typeElement = (TypeElement) element;
            try {
                JavaFile generatedFile = generate(typeElement);
                try {
                    generatedFile.writeTo(filer);
                } catch (IOException e) {
                    messager.printMessage(
                            Kind.ERROR, "Failed to write proxy class: " + Throwables.getStackTraceAsString(e));
                }
            } catch (RuntimeException e) {
                messager.printMessage(
                        Kind.ERROR, "Failed to generate proxy class: " + Throwables.getStackTraceAsString(e));
            }
        }
        return false;
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private JavaFile generate(TypeElement typeElement) {
        TypeName annotatedType = TypeName.get(typeElement.asType());
        String packageName =
                elements.getPackageOf(typeElement).getQualifiedName().toString();
        String className = typeElement.getSimpleName() + "Proxy";
        Visibility visibility =
                typeElement.getModifiers().contains(Modifier.PUBLIC) ? Visibility.PUBLIC : Visibility.PACKAGE_PRIVATE;

        TypeSpec.Builder specBuilder = TypeSpec.classBuilder(className)
                .addOriginatingElement(typeElement)
                .addModifiers(visibility.modifiers(Modifier.FINAL))
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", getClass().getName())
                        .build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PRIVATE)
                        .build())
                .addMethod(MethodSpec.methodBuilder("of")
                        .addTypeVariables(Lists.transform(typeElement.getTypeParameters(), TypeVariableName::get))
                        .addAnnotations(
                                typeElement.getTypeParameters().isEmpty()
                                        ? ImmutableList.of()
                                        : ImmutableList.of(AnnotationSpec.builder(SuppressWarnings.class)
                                                .addMember("value", "$S", "unchecked")
                                                .build()))
                        .addModifiers(visibility.modifiers(Modifier.STATIC))
                        .returns(annotatedType)
                        .addParameter(ParameterSpec.builder(InvocationHandler.class, HANDLER_PARAMETER_NAME)
                                .build())
                        .addStatement(
                                "return ($T) $T.newProxyInstance("
                                        + "$N.class.getClassLoader(), "
                                        + "new $T<?>[] {$T.class}, $N)",
                                annotatedType,
                                java.lang.reflect.Proxy.class,
                                className,
                                Class.class,
                                TypeNames.erased(annotatedType),
                                HANDLER_PARAMETER_NAME)
                        .build());

        if (typeElement.getAnnotation(Deprecated.class) != null) {
            specBuilder.addAnnotation(Deprecated.class);
        }

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
}
