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

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

final class TypeNames {

    static final ClassName ERROR = ClassName.get(Error.class);
    static final ClassName EXCEPTION = ClassName.get(Exception.class);
    static final ClassName RUNTIME_EXCEPTION = ClassName.get(RuntimeException.class);
    static final ClassName THROWABLE = ClassName.get(Throwable.class);

    static TypeName erased(TypeName input) {
        if (input instanceof ParameterizedTypeName) {
            ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) input;
            return parameterizedTypeName.rawType;
        }
        if (input instanceof WildcardTypeName || input instanceof TypeVariableName) {
            return TypeName.OBJECT;
        }
        if (input instanceof ArrayTypeName) {
            ArrayTypeName arrayTypeName = (ArrayTypeName) input;
            return ArrayTypeName.of(erased(arrayTypeName.componentType));
        }
        return input;
    }

    private TypeNames() {}
}
