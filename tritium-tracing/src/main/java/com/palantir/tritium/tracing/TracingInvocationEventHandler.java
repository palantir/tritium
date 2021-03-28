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
import com.palantir.tracing.Tracer;
import com.palantir.tritium.v1.api.event.InvocationContext;
import com.palantir.tritium.v1.core.event.InstrumentationProperties;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Invocation event handler that instruments using tracing-java {@link Tracer}.
 * @deprecated use {@link com.palantir.tritium.v1.tracing.event.TracingInvocationEventHandler}
 */
@Deprecated // remove post 1.0
@SuppressWarnings("UnnecessarilyFullyQualified") // deprecated types
public final class TracingInvocationEventHandler
        extends com.palantir.tritium.event.AbstractInvocationEventHandler<
                com.palantir.tritium.event.InvocationContext> {

    private final String component;

    /**
     * Constructs new tracing event handler.
     *
     * @param component component name
     * @deprecated use {@link com.palantir.tritium.v1.tracing.event.TracingInvocationEventHandler#create(String)}
     */
    @Deprecated // remove post 1.0
    @SuppressWarnings("DeprecatedIsStillUsed") // used by static factory
    public TracingInvocationEventHandler(String component) {
        super(getEnabledSupplier(component));
        this.component = checkNotNull(component, "component");
    }

    /**
     * Constructs new tracing event handler.
     *
     * @param component component name
     * @return tracing event handler
     */
    @SuppressWarnings("unchecked")
    public static com.palantir.tritium.event.InvocationEventHandler<com.palantir.tritium.event.InvocationContext>
            create(String component) {
        if (RemotingCompatibleTracingInvocationEventHandler.requiresRemotingFallback()) {
            return RemotingCompatibleTracingInvocationEventHandler.create(component);
        }
        return new TracingInvocationEventHandler(component);
    }

    @Override
    public com.palantir.tritium.event.InvocationContext preInvocation(
            @Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
        com.palantir.tritium.event.InvocationContext context =
                com.palantir.tritium.event.DefaultInvocationContext.of(instance, method, args);
        String operationName = getOperationName(method);
        Tracer.fastStartSpan(operationName);
        return context;
    }

    private String getOperationName(Method method) {
        return Strings.isNullOrEmpty(component) ? method.getName() : component + '.' + method.getName();
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object _result) {
        complete(context);
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable _cause) {
        complete(context);
    }

    private void complete(@Nullable InvocationContext context) {
        debugIfNullContext(context);
        // Context is null if no span was created, in which case the existing span should not be completed
        if (context != null) {
            Tracer.fastCompleteSpan();
        }
    }

    @SuppressWarnings("NoFunctionalReturnType") // lazy eval
    static BooleanSupplier getEnabledSupplier(String component) {
        BooleanSupplier systemPropertyEnabled = InstrumentationProperties.getSystemPropertySupplier(component);
        return () -> systemPropertyEnabled.getAsBoolean()
                // In an unsampled trace, there's no reason to record additional spans. Note that this will create
                // new root spans if not present in order to create a new traceId regardless of sampling state to
                // preserve behavior.
                && !Tracer.hasUnobservableTrace();
    }
}
