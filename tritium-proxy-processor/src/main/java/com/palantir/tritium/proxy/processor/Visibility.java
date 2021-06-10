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

import com.google.common.collect.ImmutableSet;
import javax.lang.model.element.Modifier;

enum Visibility {
    PUBLIC() {
        @Override
        Modifier[] modifiers(Modifier... modifiers) {
            return ImmutableSet.<Modifier>builder()
                    .add(Modifier.PUBLIC)
                    .add(modifiers)
                    .build()
                    .toArray(new Modifier[0]);
        }
    },
    PACKAGE_PRIVATE() {
        @Override
        Modifier[] modifiers(Modifier... modifiers) {
            return modifiers;
        }
    };

    abstract Modifier[] modifiers(Modifier... modifiers);
}
