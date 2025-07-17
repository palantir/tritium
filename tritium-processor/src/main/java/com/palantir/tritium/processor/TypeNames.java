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

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import com.palantir.javapoet.WildcardTypeName;
import java.util.List;

final class TypeNames {

    static TypeName erased(TypeName input) {
        if (input instanceof ParameterizedTypeName parameterizedTypeName) {

            return parameterizedTypeName.rawType();
        }
        if (input instanceof WildcardTypeName || input instanceof TypeVariableName) {
            return ClassName.OBJECT;
        }
        if (input instanceof ArrayTypeName arrayTypeName) {

            return ArrayTypeName.of(erased(arrayTypeName.componentType()));
        }
        return input;
    }

    static List<TypeName> typeParameters(TypeName typeName) {
        if (typeName instanceof ParameterizedTypeName parameterized) {

            return parameterized.typeArguments();
        }
        return List.of();
    }

    private TypeNames() {}
}
