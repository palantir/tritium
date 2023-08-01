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

import com.google.common.base.CaseFormat;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;

enum Names {
    ;

    // Avoid prefix collisions by choosing a delimiter that does not appear in valid UTF-8
    private static final byte DELIMITER = (byte) 0xff;

    /**
     * Returns {@code requestedName} unless it's included in {@code reservedNames}, in which case the {@code
     * requestedName} is modified to avoid overlapping.
     */
    static String disambiguate(String requestedName, Set<String> reservedNames) {
        String name = requestedName;
        while (reservedNames.contains(name)) {
            name = name + "_";
        }
        return name;
    }

    static String methodFieldName(ExecutableElement method) {
        String upperMethodName = CaseFormat.LOWER_CAMEL
                .converterTo(CaseFormat.UPPER_UNDERSCORE)
                .convert(method.getSimpleName().toString());

        Hasher hasher = Hashing.murmur3_32_fixed().newHasher();
        Stream.concat(
                        Stream.of(method.getSimpleName().toString()),
                        method.getParameters().stream().map(parameter -> simpleName(TypeName.get(parameter.asType()))))
                .forEach(value -> {
                    hasher.putString(value, StandardCharsets.UTF_8);
                    hasher.putByte(DELIMITER);
                });

        return upperMethodName + "_" + hasher.hash();
    }

    private static String simpleName(TypeName input) {
        if (input.isPrimitive()) {
            return input.toString();
        }
        if (input instanceof ClassName) {
            return ((ClassName) input).simpleName();
        }
        if (input instanceof ParameterizedTypeName) {
            ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) input;
            return simpleName(parameterizedTypeName.rawType);
        }
        if (input instanceof WildcardTypeName || input instanceof TypeVariableName) {
            return Object.class.getSimpleName();
        }
        if (input instanceof ArrayTypeName) {
            ArrayTypeName arrayTypeName = (ArrayTypeName) input;
            return simpleName(arrayTypeName.componentType) + "[]";
        }
        throw new IllegalArgumentException("Unknown type-name: " + input);
    }
}
