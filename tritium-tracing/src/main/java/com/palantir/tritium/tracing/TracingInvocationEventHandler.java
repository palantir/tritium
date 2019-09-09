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

package com.palantir.tritium.tracing;

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.palantir.tracing.CloseableTracer;
import com.palantir.tracing.Tracer;
import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.reflect.Method;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TracingInvocationEventHandler extends AbstractInvocationEventHandler<CloseableTracer> {

    private final String component;

    /**
     * Constructs new tracing event handler.
     * @param component component name
     * @deprecated use {@link #create(String)}
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed") // used by static factory
    public TracingInvocationEventHandler(String component) {
        super((java.util.function.BooleanSupplier) getEnabledSupplier(component));
        this.component = checkNotNull(component, "component");
    }

    /**
     * Constructs new tracing event handler.
     * @param component component name
     * @return tracing event handler
     */
    public static InvocationEventHandler<?> create(String component) {
        if (RemotingCompatibleTracingInvocationEventHandler.requiresRemotingFallback()) {
            return RemotingCompatibleTracingInvocationEventHandler.create(component);
        }
        //noinspection deprecation
        return new TracingInvocationEventHandler(component);
    }

    @Override
    public CloseableTracer preInvocation(
            @SuppressWarnings("unused") @Nonnull Object instance,
            @Nonnull Method method,
            @SuppressWarnings("unused") @Nonnull Object[] args) {
        return CloseableTracer.startSpan(getOperationName(method));
    }

    private String getOperationName(Method method) {
        return Strings.isNullOrEmpty(component) ? method.getName() : component + '.' + method.getName();
    }

    @Override
    public void onSuccess(@Nullable CloseableTracer context, @Nullable Object unusedResult) {
        complete(context);
    }

    @Override
    public void onFailure(@Nullable CloseableTracer context, @Nonnull Throwable unusedCause) {
        complete(context);
    }

    private void complete(@Nullable CloseableTracer context) {
        debugIfNullContext(context);
        // Context is null if no span was created, in which case the existing span should not be completed
        if (context != null) {
            context.close();
        }
    }

    static BooleanSupplier getEnabledSupplier(String component) {
        BooleanSupplier systemPropertyEnabled = InstrumentationProperties.getSystemPropertySupplier(component);
        return () -> systemPropertyEnabled.getAsBoolean() && !inUnsampledTrace();
    }

    /**
     * In an unsampled trace, there's no reason to record additional spans. Note that this will create new root spans
     * if not present in order to create a new traceId regardless of sampling state to preserve behavior.
     */
    static boolean inUnsampledTrace() {
        return Tracer.hasTraceId() && !Tracer.isTraceObservable();
    }
}
