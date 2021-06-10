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

import com.google.common.base.CaseFormat;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

final class Methods {

    static boolean isObjectMethod(Elements elements, ExecutableElement methodElement) {
        TypeElement object = elements.getTypeElement(Object.class.getName());
        for (Element element : object.getEnclosedElements()) {
            if (element instanceof ExecutableElement) {
                ExecutableElement executableElement = (ExecutableElement) element;
                if (elements.overrides(methodElement, executableElement, object)
                        || Objects.equals(methodElement, executableElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isInstrumentable(Elements elements, Element element) {
        if (element instanceof ExecutableElement) {
            ExecutableElement executable = (ExecutableElement) element;
            return !executable.getModifiers().contains(Modifier.PRIVATE)
                    && !executable.getModifiers().contains(Modifier.STATIC)
                    && !Methods.isObjectMethod(elements, executable);
        }
        return false;
    }

    /**
     * Returns a map of {@link MethodElements} to the name of the generated static field added to
     * the {@link TypeSpec.Builder instrumentationBuilder}.
     */
    static IdentityHashMap<MethodElements, String> methodStaticFieldName(
            List<MethodElements> instrumentedMethods, TypeSpec.Builder typeBuilder, TypeName delegateType) {
        List<CodeBlock> initializers = new ArrayList<>();
        IdentityHashMap<MethodElements, String> result = new IdentityHashMap<>();
        for (MethodElements method : instrumentedMethods) {
            String methodName = method.element().getSimpleName().toString();
            String originalMethodFieldName = CaseFormat.LOWER_CAMEL
                    .converterTo(CaseFormat.UPPER_UNDERSCORE)
                    .convert(methodName);
            String methodFieldName = originalMethodFieldName;
            for (int i = 1; true; i++) {
                String finalName = methodFieldName;
                if (typeBuilder.fieldSpecs.stream().noneMatch(spec -> spec.name.equals(finalName))) {
                    break;
                }
                methodFieldName = originalMethodFieldName + i;
            }
            result.put(method, methodFieldName);
            typeBuilder.addField(
                    FieldSpec.builder(Method.class, methodFieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                            .build());
            initializers.add(CodeBlock.builder()
                    .addStatement(
                            "$N = $T.class.getMethod($S$L)",
                            methodFieldName,
                            TypeNames.erased(delegateType),
                            methodName,
                            method.element().getParameters().isEmpty()
                                    ? ""
                                    : method.element().getParameters().stream()
                                            .map(param -> CodeBlock.of(
                                                    "$T.class", TypeNames.erased(TypeName.get(param.asType()))))
                                            .collect(CodeBlock.joining(", ", ", ", "")))
                    .build());
        }
        CodeBlock.Builder staticInitializer = CodeBlock.builder().beginControlFlow("try");
        for (CodeBlock initializer : initializers) {
            staticInitializer.add(initializer);
        }
        typeBuilder.addStaticBlock(staticInitializer
                .nextControlFlow("catch ($T e)", NoSuchMethodException.class)
                .addStatement("throw new $T(e)", RuntimeException.class)
                .endControlFlow()
                .build());
        return result;
    }

    private Methods() {}
}
