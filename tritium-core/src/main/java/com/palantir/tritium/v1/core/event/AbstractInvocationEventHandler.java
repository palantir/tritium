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

package com.palantir.tritium.v1.core.event;

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.palantir.logsafe.SafeArg;
import com.palantir.tritium.v1.api.event.InvocationContext;
import com.palantir.tritium.v1.api.event.InvocationEventHandler;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract invocation event handler implementation.
 *
 * @param <C> invocation context
 */
public abstract class AbstractInvocationEventHandler<C extends InvocationContext> implements InvocationEventHandler<C> {

    private static final Logger log = LoggerFactory.getLogger(AbstractInvocationEventHandler.class);

    private final BooleanSupplier isEnabledSupplier;

    /** Always enabled instrumentation handler. */
    protected AbstractInvocationEventHandler() {
        this(() -> true);
    }

    /**
     * Constructs {@link AbstractInvocationEventHandler} with specified enabled supplier.
     *
     * @param isEnabledSupplier enabled supplier
     */
    protected AbstractInvocationEventHandler(BooleanSupplier isEnabledSupplier) {
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
     * Logs debug information if the specified invocation context is not null.
     *
     * @param context invocation context
     */
    protected final void debugIfNullContext(@Nullable InvocationContext context) {
        if (context == null) {
            log.debug(
                    "{} encountered null metric context, likely due to exception in preInvocation",
                    safeClassName(getClass()));
        }
    }

    private static SafeArg<String> safeClassName(@Nullable Object obj) {
        return SafeArg.of("class", (obj == null) ? "" : obj.getClass().getName());
    }
}
