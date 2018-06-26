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

package com.palantir.tritium.event;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;

/**
 * Abstract invocation event handler implementation.
 *
 * @param <C> invocation context
 */
public abstract class AbstractInvocationEventHandler<C extends InvocationContext>
        implements InvocationEventHandler<C> {

    private static final Object[] NO_ARGS = {};

    private final java.util.function.BooleanSupplier isEnabledSupplier;

    /**
     * Always enabled instrumentation handler.
     */
    protected AbstractInvocationEventHandler() {
        this((java.util.function.BooleanSupplier) () -> true);
    }

    /**
     * Bridge for backward compatibility.
     * @deprecated Use {@link #AbstractInvocationEventHandler(java.util.function.BooleanSupplier)}
     */
    @Deprecated
    @SuppressWarnings("FunctionalInterfaceClash") // back compat
    protected AbstractInvocationEventHandler(com.palantir.tritium.api.functions.BooleanSupplier isEnabledSupplier) {
        this((java.util.function.BooleanSupplier) isEnabledSupplier);
    }

    /**
     * Constructs {@link AbstractInvocationEventHandler} with specified enabled supplier.
     * @param isEnabledSupplier enabled supplier
     */
    @SuppressWarnings("FunctionalInterfaceClash")
    protected AbstractInvocationEventHandler(java.util.function.BooleanSupplier isEnabledSupplier) {
        this.isEnabledSupplier = checkNotNull(isEnabledSupplier, "isEnabledSupplier");
    }

    /**
     * Returns true if instrumentation handling is enabled, otherwise false.
     *
     * @return whether instrumentation handling is enabled
     */
    @Override
    public final boolean isEnabled() {
        return isEnabledSupplier.getAsBoolean();
    }

    /**
     * Returns system property based boolean supplier for the specified class.
     *
     * @param clazz
     *        instrumentation handler class
     * @return false if "instrument.fully.qualified.class.Name" is set to "false", otherwise true
     */
    protected static com.palantir.tritium.api.functions.BooleanSupplier getSystemPropertySupplier(
            Class<? extends InvocationEventHandler<InvocationContext>> clazz) {
        checkNotNull(clazz, "clazz");
        return InstrumentationProperties.getSystemPropertySupplier(clazz.getName());
    }

    public static Object[] nullToEmpty(@Nullable Object[] args) {
        return (args == null) ? NO_ARGS : args;
    }

}
