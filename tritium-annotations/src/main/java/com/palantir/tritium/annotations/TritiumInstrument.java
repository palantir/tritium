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

package com.palantir.tritium.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Interfaces annotated with {@link TritiumInstrument} will result in a generated concrete implementation
 * using the same package, and name prefixed with {@code Instrumented}.
 * <p/>
 * Externally defined interfaces may be instrumented by defining an interface with the requested interface
 * as a supertype. The generator will produce an instrumentation class which can wrap the super-interface.
 * When type parameters are present, the empty interface must define and pass along type parameters which
 * exactly match the target interface.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface TritiumInstrument {}
