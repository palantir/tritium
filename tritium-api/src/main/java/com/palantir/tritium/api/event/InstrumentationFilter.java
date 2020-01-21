/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.api.event;

import java.lang.reflect.Method;
import javax.annotation.Nonnull;

@FunctionalInterface
public interface InstrumentationFilter {

    /**
     * Invoked prior to a method invocation, allowing control whether the invocation will be instrumented.
     *
     * @param method the {@code Method} corresponding to the interface method invoked on the instance.
     * @param args an array of objects containing the values of the arguments passed in the method invocation on the
     *     instance, or empty array if interface method takes no arguments. Arguments of primitive types are wrapped in
     *     instances of the appropriate primitive wrapper class, such as {@code java.lang.Integer} or
     *     {@code java.lang.Boolean}.
     * @return true if invocation should be instrumented, false if invocation should not be instrumented
     */
    boolean shouldInstrument(@Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args);
}
