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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.palantir.logsafe.SafeArg;
import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.event.InvocationContext;
import java.lang.reflect.Method;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TracingInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final Logger logger = LoggerFactory.getLogger(TracingInvocationEventHandler.class);

    private final String component;
    private final Tracer tracer;

    public TracingInvocationEventHandler(String component) {
        super((java.util.function.BooleanSupplier) getEnabledSupplier(component));
        this.component = component;
        this.tracer = createTracer();
    }

    @Override
    public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
        InvocationContext context = DefaultInvocationContext.of(instance, method, args);
        String operationName = getOperationName(method);
        tracer.startSpan(operationName);

        return context;
    }

    private String getOperationName(Method method) {
        return Strings.isNullOrEmpty(component) ? method.getName() : component + '.' + method.getName();
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        debugIfNullContext(context);
        tracer.completeSpan();
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
        debugIfNullContext(context);
        // TODO(davids): add Error event
        tracer.completeSpan();
    }

    private static void debugIfNullContext(@Nullable InvocationContext context) {
        if (context == null) {
            logger.debug("Encountered null metric context likely due to exception in preInvocation");
        }
    }

    static BooleanSupplier getEnabledSupplier(String component) {
        return InstrumentationProperties.getSystemPropertySupplier(component);
    }

    private static Tracer createTracer() {
        // Ugly reflection based check to determine what implementation of tracing to use to avoid duplicate
        // traces as remoting3's tracing classes delegate functionality to tracing-java in remoting 3.43.0+
        // and remoting3.tracing.Tracers.wrap now returns a "com.palantir.tracing.Tracers" implementation.
        //
        // a) remoting3 prior to 3.43.0+ -> use reflection based remoting3 tracer
        // b) remoting3 3.43.0+ -> use tracing-java
        // c) no remoting3 -> use tracing-java

        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class<?> tracersClass = classLoader.loadClass("com.palantir.remoting3.tracing.Tracers");
            if (tracersClass != null) {
                Method wrapMethod = tracersClass.getMethod("wrap", Runnable.class);
                if (wrapMethod != null) {
                    Object wrappedTrace = wrapMethod.invoke(null, (Runnable) () -> {
                    });
                    String expectedTracingPackage = com.palantir.tracing.Tracers.class.getPackage().getName();
                    String actualTracingPackage = wrappedTrace.getClass().getPackage().getName();
                    if (!Objects.equals(expectedTracingPackage, actualTracingPackage)) {
                        Class<?> tracerClass = classLoader.loadClass("com.palantir.remoting3.tracing.Tracer");
                        Method startSpanMethod = tracerClass.getMethod("startSpan", String.class);
                        Method completeSpanMethod = tracerClass.getMethod("fastCompleteSpan");
                        if (startSpanMethod != null && completeSpanMethod != null) {
                            logger.error("Multiple tracing implementations detected, expected '{}' but found '{}',"
                                            + " using legacy remoting3 tracing for backward compatibility",
                                    SafeArg.of("expectedPackage", expectedTracingPackage),
                                    SafeArg.of("actualPackage", actualTracingPackage));
                            return new Remoting3Tracer(startSpanMethod, completeSpanMethod);
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            // expected case when remoting3 is not on classpath
            logger.debug("Remoting3 unavailable, using Java tracing", e);
        }

        return JavaTracingTracer.INSTANCE;
    }

    private interface Tracer {
        void startSpan(String operationName);
        void completeSpan();
    }

    private enum JavaTracingTracer implements TracingInvocationEventHandler.Tracer {
        INSTANCE;

        @Override
        public void startSpan(String operationName) {
            com.palantir.tracing.Tracer.startSpan(operationName);
        }

        @Override
        public void completeSpan() {
            com.palantir.tracing.Tracer.fastCompleteSpan();
        }
    }

    private static final class Remoting3Tracer implements TracingInvocationEventHandler.Tracer {

        private final Method startSpanMethod;
        private final Method completeSpanMethod;

        Remoting3Tracer(Method startSpanMethod, Method completeSpanMethod) {
            this.startSpanMethod = Preconditions.checkNotNull(startSpanMethod, "startSpanMethod");
            this.completeSpanMethod = Preconditions.checkNotNull(completeSpanMethod, "completeSpanMethod");
        }

        @Override
        public void startSpan(String operationName) {
            try {
                startSpanMethod.invoke(null, operationName);
            } catch (ReflectiveOperationException e) {
                Throwable cause = e.getCause();
                Throwables.propagateIfPossible(cause, RuntimeException.class);
                throw new RuntimeException(cause);
            }
        }

        @Override
        public void completeSpan() {
            try {
                completeSpanMethod.invoke(null);
            } catch (ReflectiveOperationException e) {
                Throwable cause = e.getCause();
                Throwables.propagateIfPossible(cause, RuntimeException.class);
                throw new RuntimeException(cause);
            }
        }
    }

}
