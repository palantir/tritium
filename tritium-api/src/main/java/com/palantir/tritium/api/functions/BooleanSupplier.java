/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.api.functions;

/**
 * {@code boolean} supplier.
 * Back-compat bridge type, will be removed in future major version bump.
 * @deprecated use {@link java.util.function.BooleanSupplier}
 */
@Deprecated
@FunctionalInterface
public interface BooleanSupplier extends java.util.function.BooleanSupplier {

    /**
     * {@link BooleanSupplier} that always returns true.
     * @deprecated prefer simple inlined lambda for clarity
     */
    @Deprecated
    @SuppressWarnings("MemberName") // public API
    BooleanSupplier TRUE = () -> true;

    /**
     * {@link BooleanSupplier} that always returns false.
     * @deprecated prefer simple inlined lambda for clarity
     */
    @Deprecated
    @SuppressWarnings("MemberName") // public API
    BooleanSupplier FALSE = () -> false;

    /**
     * Supply a boolean.
     *
     * @return a boolean result
     */
    default boolean asBoolean() {
        return getAsBoolean();
    }
}
