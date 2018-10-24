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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.palantir.tracing.Tracers;
import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class TracingInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final Logger logger = LoggerFactory.getLogger(TracingInvocationEventHandler.class);

    private final String component;

    /**
     * Constructs new tracing event handler.
     * @param component component name
     * @deprecated use {@link #create(String)}
     */
    @Deprecated
    public TracingInvocationEventHandler(String component) {
        super((java.util.function.BooleanSupplier) getEnabledSupplier(component));
        this.component = Preconditions.checkNotNull(component, "component");
    }

    /**
     * Constructs new tracing event handler.
     * @param component component name
     * @return tracing event handler
     */
    @SuppressWarnings("unchecked")
    public static InvocationEventHandler<InvocationContext> create(String component) {
        if (RemotingCompatibleTracingInvocationEventHandler.requiresRemotingFallback()) {
            return RemotingCompatibleTracingInvocationEventHandler.create(component);
        }
        //noinspection deprecation
        return new TracingInvocationEventHandler(component);
    }

    @Override
    public TracingInvocationContext preInvocation(Object instance, Method method, Object[] args) {
        boolean rootSpan = MDC.get(Tracers.TRACE_ID_KEY) == null;
        TracingInvocationContext context = TracingInvocationContext.of(instance, method, args, rootSpan);
        String operationName = getOperationName(method);
        com.palantir.tracing.Tracer.startSpan(operationName);
        return context;
    }

    private String getOperationName(Method method) {
        return Strings.isNullOrEmpty(component) ? method.getName() : component + '.' + method.getName();
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        complete(context);
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
        // TODO(davids): add Error event
        complete(context);
    }

    private static void complete(@Nullable InvocationContext context) {
        debugIfNullContext(context);
        // Context is null if no span was created, in which case the existing span should not be completed
        if (context != null) {
            com.palantir.tracing.Tracer.fastCompleteSpan();
            if (context instanceof TracingInvocationContext && ((TracingInvocationContext) context).isRootSpan()) {
                com.palantir.tracing.Tracer.getAndClearTrace();
            }
        }
    }

    private static void debugIfNullContext(@Nullable InvocationContext context) {
        if (context == null) {
            logger.debug("Encountered null metric context likely due to exception in preInvocation");
        }
    }

    static BooleanSupplier getEnabledSupplier(String component) {
        return InstrumentationProperties.getSystemPropertySupplier(component);
    }

    static final class TracingInvocationContext implements InvocationContext {

        private static final Object[] NO_ARGS = {};

        private final long startTimeNanos;
        private final Object instance;
        private final Method method;
        private final Object[] args;
        private final boolean rootSpan;

        private TracingInvocationContext(
                long startTimeNanos, Object instance, Method method, @Nullable Object[] args, boolean rootSpan) {
            this.startTimeNanos = startTimeNanos;
            this.instance = instance;
            this.method = method;
            this.args = toNonNullClone(args);
            this.rootSpan = rootSpan;
        }

        private static Object[] toNonNullClone(@Nullable Object[] args) {
            return args == null ? NO_ARGS : args.clone();
        }

        public static TracingInvocationContext of(
                Object instance, Method method, @Nullable Object[] args, boolean rootSpan) {
            return new TracingInvocationContext(
                    System.nanoTime(),
                    checkNotNull(instance, "instance"),
                    checkNotNull(method, "method"),
                    args,
                    rootSpan);
        }

        @Override
        public long getStartTimeNanos() {
            return startTimeNanos;
        }

        @Override
        public Object getInstance() {
            return instance;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getArgs() {
            return args;
        }

        boolean isRootSpan() {
            return rootSpan;
        }

        @Override
        @SuppressWarnings("DesignForExtension")
        public String toString() {
            return "TracingInvocationContext [startTimeNanos=" + startTimeNanos + ", instance=" + instance + ", method="
                    + method + ", args=" + Arrays.toString(args) + ", rootSpan=" + rootSpan + "]";
        }
    }
}
