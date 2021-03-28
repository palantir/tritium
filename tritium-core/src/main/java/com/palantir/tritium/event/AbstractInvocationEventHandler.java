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

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.palantir.tritium.v1.core.event.InstrumentationProperties;
import java.util.function.BooleanSupplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Abstract invocation event handler implementation.
 *
 * @param <C> invocation context
 * @deprecated use {@link com.palantir.tritium.v1.core.event.AbstractInvocationEventHandler}
 */
@Deprecated // remove post 1.0
@SuppressWarnings("deprecation") // binary backward compatibility bridge
public abstract class AbstractInvocationEventHandler<C extends com.palantir.tritium.event.InvocationContext>
        extends com.palantir.tritium.v1.core.event.AbstractInvocationEventHandler<C>
        implements com.palantir.tritium.event.InvocationEventHandler<C> {

    private static final Object[] NO_ARGS = {};

    /** Always enabled instrumentation handler. */
    protected AbstractInvocationEventHandler() {
        this((BooleanSupplier) () -> true);
    }

    /**
     * Bridge for backward compatibility.
     *
     * @deprecated Use {@link #AbstractInvocationEventHandler(BooleanSupplier)}
     */
    @Deprecated // remove post 1.0
    @SuppressWarnings("FunctionalInterfaceClash") // backward compatibility
    protected AbstractInvocationEventHandler(com.palantir.tritium.api.functions.BooleanSupplier isEnabledSupplier) {
        this((BooleanSupplier) isEnabledSupplier);
    }

    /**
     * Constructs {@link AbstractInvocationEventHandler} with specified enabled supplier.
     *
     * @param isEnabledSupplier enabled supplier
     */
    @SuppressWarnings("FunctionalInterfaceClash")
    protected AbstractInvocationEventHandler(BooleanSupplier isEnabledSupplier) {
        super(isEnabledSupplier);
    }

    @Override
    @Deprecated // remove post 1.0
    @SuppressWarnings("DesignForExtension") // binary backward compatibility bridge
    public void onSuccess(@Nullable com.palantir.tritium.event.InvocationContext context, @Nullable Object result) {
        onSuccess((com.palantir.tritium.v1.api.event.InvocationContext) context, result);
    }

    @Override
    @Deprecated // remove post 1.0
    @SuppressWarnings("DesignForExtension") // binary backward compatibility bridge
    public void onFailure(@Nullable com.palantir.tritium.event.InvocationContext context, @Nonnull Throwable cause) {
        onFailure((com.palantir.tritium.v1.api.event.InvocationContext) context, cause);
    }

    /**
     * Logs debug information if the specified invocation context is not null.
     *
     * @param context invocation context
     */
    @SuppressWarnings("DesignForExtension") // binary backward compatibility bridge
    protected final void debugIfNullContext(@Nullable com.palantir.tritium.event.InvocationContext context) {
        super.debugIfNullContext(context);
    }

    /**
     * Returns system property based boolean supplier for the specified class.
     *
     * @param clazz instrumentation handler class
     * @return false if "instrument.fully.qualified.class.Name" is set to "false", otherwise true
     * @deprecated use {@link InstrumentationProperties#getSystemPropertySupplier(Class)}
     */
    @Deprecated // remove post 1.0
    @SuppressWarnings("deprecation") // backward-compatibility return type for now
    protected static com.palantir.tritium.api.functions.BooleanSupplier getSystemPropertySupplier(
            Class<? extends com.palantir.tritium.event.InvocationEventHandler<InvocationContext>> clazz) {
        checkNotNull(clazz, "clazz");
        BooleanSupplier systemPropertySupplier = InstrumentationProperties.getSystemPropertySupplier(clazz);
        return systemPropertySupplier::getAsBoolean;
    }

    /**
     * Returns a non-null array for given arguments.
     * @return args if non-null otherwise non-null empty array
     * @deprecated will be removed in future release
     */
    @Deprecated // remove post 1.0
    @SuppressWarnings("WeakerAccess") // public API
    public static Object[] nullToEmpty(@Nullable Object[] args) {
        return (args == null) ? NO_ARGS : args;
    }
}
