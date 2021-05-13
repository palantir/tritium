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

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

final class TypeElements {

    /**
     * if interface {@code A} extends {@code B}, adding no additional methods or specificity, B is returned. This
     * allows external interfaces to be instrumented without adding upstream code changes.
     */
    static Optional<TypeName> unwrapEmptyInterface(Elements elements, TypeElement typeElement) {
        List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
        if (interfaces.size() != 1) {
            return Optional.empty();
        }
        if (typeElement.getEnclosedElements().stream()
                .anyMatch(element -> Methods.isInstrumentable(elements, element))) {
            return Optional.empty();
        }
        TypeMirror superInterface = interfaces.get(0);
        TypeName superTypeName = TypeName.get(superInterface);
        TypeName thisTypeName = TypeName.get(typeElement.asType());
        if (!Objects.equals(typeParameters(thisTypeName), typeParameters(superTypeName))) {
            return Optional.empty();
        }
        return Optional.of(superTypeName);
    }

    private static List<TypeName> typeParameters(TypeName typeName) {
        if (typeName instanceof ParameterizedTypeName) {
            ParameterizedTypeName parameterized = (ParameterizedTypeName) typeName;
            return parameterized.typeArguments;
        }
        return ImmutableList.of();
    }

    private TypeElements() {}
}
