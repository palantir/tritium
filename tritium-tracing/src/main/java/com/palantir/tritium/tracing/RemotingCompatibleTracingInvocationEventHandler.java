/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.base.Suppliers;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("TryFailRefactoring") // work-around https://github.com/google/error-prone/issues/1447
public final class RemotingCompatibleTracingInvocationEventHandler
        extends AbstractInvocationEventHandler<InvocationContext> {

    private static final SafeLogger log = SafeLoggerFactory.get(RemotingCompatibleTracingInvocationEventHandler.class);

    private static final AtomicBoolean shouldLogFallbackError = new AtomicBoolean();

    private static final Supplier<Boolean> requiresFallback = Suppliers.memoize(() -> {
        try {
            return isUsingMultipleTracers(loadRemoting3TracersClass());
        } catch (ReflectiveOperationException e) {
            // expected case when remoting3 is not on classpath
            return false;
        }
    });

    private static final Supplier<Tracer> tracerFactory = Suppliers.memoize(() -> {
        // Ugly reflection based check to determine what implementation of tracing to use to avoid duplicate
        // traces as remoting3's tracing classes delegate functionality to tracing-java in remoting 3.43.0+
        // and remoting3.tracing.Tracers.wrap now returns a "com.palantir.tracing.Tracers" implementation.
        //
        // a) remoting3 prior to 3.43.0+ -> use reflection based remoting3 tracer
        // b) remoting3 3.43.0+ -> use tracing-java
        // c) no remoting3 -> use tracing-java

        try {
            Class<?> tracersClass = loadRemoting3TracersClass();
            if (isUsingMultipleTracers(tracersClass)) {
                Class<?> tracerClass = tracersClass.getClassLoader().loadClass("com.palantir.remoting3.tracing.Tracer");
                Method startSpanMethod = tracerClass.getMethod("startSpan", String.class);
                Method completeSpanMethod = tracerClass.getMethod("fastCompleteSpan");
                if (startSpanMethod != null && completeSpanMethod != null) {
                    return new ReflectiveTracer(startSpanMethod, completeSpanMethod);
                }
            }
        } catch (ReflectiveOperationException e) {
            // expected case when remoting3 is not on classpath
            log.debug("Remoting3 unavailable, using Java tracing", e);
        }

        return JavaTracingTracer.INSTANCE;
    });

    private final String component;
    private final Tracer tracer;

    public RemotingCompatibleTracingInvocationEventHandler(String component, Tracer tracer) {
        super((BooleanSupplier) InstrumentationProperties.getSystemPropertySupplier(component));
        this.component = checkNotNull(component, "component");
        this.tracer = checkNotNull(tracer, "tracer");
    }

    static InvocationEventHandler<InvocationContext> create(String component) {
        return new RemotingCompatibleTracingInvocationEventHandler(component, tracerFactory.get());
    }

    @Override
    public InvocationContext preInvocation(@Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
        InvocationContext context = DefaultInvocationContext.of(instance, method, args);
        String operationName = getOperationName(method);
        tracer.startSpan(operationName);
        return context;
    }

    private String getOperationName(Method method) {
        return Strings.isNullOrEmpty(component) ? method.getName() : component + '.' + method.getName();
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object _result) {
        debugIfNullContext(context);
        if (context != null) {
            tracer.completeSpan();
        }
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable _cause) {
        debugIfNullContext(context);
        if (context != null) {
            tracer.completeSpan();
        }
    }

    private static Class<?> loadRemoting3TracersClass() throws ClassNotFoundException {
        return Thread.currentThread().getContextClassLoader().loadClass("com.palantir.remoting3.tracing.Tracers");
    }

    /**
     * Reflection based check to determine what implementation of tracing to use to avoid duplicate traces as
     * remoting3's tracing classes delegate functionality to tracing-java in remoting 3.43.0+ and
     * remoting3.tracing.Tracers.wrap now returns a "com.palantir.tracing.Tracers" implementation.
     *
     * <ol>
     *   <li>a) remoting3 prior to 3.43.0+ -> use reflection based remoting3 tracer
     *   <li>b) remoting3 3.43.0+ -> use tracing-java
     *   <li>c) no remoting3 -> use tracing-java
     * </ol>
     *
     * @return true if we detect that remoting3's tracing does not delegate to tracing-java
     */
    static boolean requiresRemotingFallback() {
        return Boolean.TRUE.equals(requiresFallback.get());
    }

    @SuppressWarnings("UnnecessarilyFullyQualified") // explicitly qualifying given remoting3 vs. tracing
    private static boolean isUsingMultipleTracers(Class<?> tracersClass)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        if (tracersClass != null) {
            Method wrapMethod = tracersClass.getMethod("wrap", Runnable.class);
            if (wrapMethod != null) {
                Object wrappedTrace = wrapMethod.invoke(null, (Runnable) () -> {});
                String expectedTracingPackage =
                        com.palantir.tracing.Tracers.class.getPackage().getName();
                String actualTracingPackage =
                        wrappedTrace.getClass().getPackage().getName();
                if (!Objects.equals(expectedTracingPackage, actualTracingPackage)) {
                    if (shouldLogFallbackError.compareAndSet(/* expectedValue= */ false, /* newValue= */ true)) {
                        log.error(
                                "Multiple tracing implementations detected, expected '{}' but found '{}',"
                                        + " using legacy remoting3 tracing for backward compatibility",
                                SafeArg.of("expectedPackage", expectedTracingPackage),
                                SafeArg.of("actualPackage", actualTracingPackage),
                                new SafeIllegalStateException("Multiple tracing implementations detected"));
                    }
                    return true;
                }
            }
        }

        return false;
    }
}
