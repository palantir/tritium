/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.tritium.proxy;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.palantir.tritium.api.functions.LongPredicate;
import com.palantir.tritium.event.InstrumentationFilter;
import com.palantir.tritium.event.InstrumentationFilters;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import com.palantir.tritium.event.log.LoggingInvocationEventHandler;
import com.palantir.tritium.event.log.LoggingLevel;
import com.palantir.tritium.event.metrics.MetricsInvocationEventHandler;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import java.util.List;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import org.objenesis.ObjenesisHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrument arbitrary service interfaces with optional metrics and invocation logging.
 */
public final class Instrumentation {

    private Instrumentation() {
        throw new UnsupportedOperationException();
    }

    static <T, U extends T> T wrap(
            Class<T> type,
            final U delegate,
            final InstrumentationFilter instrumentationFilter,
            final List<InvocationEventHandler<InvocationContext>> handlers) {

        checkNotNull(type, "type");
        checkNotNull(delegate, "delegate");
        checkNotNull(instrumentationFilter, "instrumentationFilter");
        checkNotNull(handlers, "handlers");

        if (handlers.isEmpty() || instrumentationFilter == InstrumentationFilters.INSTRUMENT_NONE) {
            return delegate;
        }

        if (type.isInterface()) {
            return createInterfaceProxy(type, delegate, instrumentationFilter, handlers);
        }
        return createConcreteProxy(type, delegate, instrumentationFilter, handlers);
    }

    /**
     * Wraps delegate with instrumentation.
     *
     * @deprecated Use {@link #wrap(Class, Object, InstrumentationFilter, List)}
     */
    @Deprecated
    static <T, U extends T> T wrap(
            Class<T> interfaceClass,
            final U delegate,
            final List<InvocationEventHandler<InvocationContext>> handlers) {
        return wrap(interfaceClass, delegate, InstrumentationFilters.INSTRUMENT_ALL, handlers);
    }

    private static <T, U extends T> T createInterfaceProxy(Class<T> interfaceClass,
            U delegate,
            final InstrumentationFilter instrumentationFilter,
            List<InvocationEventHandler<InvocationContext>> handlers) {
        checkArgument(interfaceClass.isInterface(), "Must specify interface class to proxy");
        return Proxies.newProxy(interfaceClass, delegate,
                new InstrumentationProxy<U>(instrumentationFilter, handlers, delegate));
    }

    private static <T, U extends T> T createConcreteProxy(Class<T> interfaceClass,
            final U delegate,
            InstrumentationFilter instrumentationFilter,
            final List<InvocationEventHandler<InvocationContext>> handlers) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(interfaceClass);
        enhancer.setCallbackType(InvocationEventHandlerAdapter.class);
        // use objenesis so that we can proxy classes which do not have a null constructor
        @SuppressWarnings("unchecked")
        Class<T> newClass = (Class<T>) enhancer.createClass();
        Enhancer.registerCallbacks(newClass, new Callback[] {
                InvocationEventHandlerAdapter.create(delegate, instrumentationFilter, handlers)
        });
        return ObjenesisHelper.newInstance(newClass);
    }

    /**
     * Return an instrumented proxy of the specified service interface and delegate that records aggregated invocation
     * metrics and performance trace logging.
     *
     * @param serviceInterface service interface
     * @param delegate delegate to instrument
     * @param metricRegistry metric registry
     * @return instrumented proxy implementing specified service interface
     * @deprecated use {@link com.palantir.tritium.Tritium#instrument(Class, Object, MetricRegistry)}
     */
    @Deprecated
    public static <T, U extends T> T instrument(Class<T> serviceInterface, U delegate, MetricRegistry metricRegistry) {
        return builder(serviceInterface, delegate)
                .withFilter(InstrumentationFilters.INSTRUMENT_ALL)
                .withMetrics(metricRegistry)
                .withPerformanceTraceLogging()
                .build();
    }

    public static <T> Logger getPerformanceLoggerForInterface(Class<T> serviceInterface) {
        return LoggerFactory.getLogger("performance." + serviceInterface.getName());
    }

    @SuppressFBWarnings(justification = "Catch-22: Checkstyle wants final builder, but FindBugs doesn't")
    public static <T, U extends T> Builder<T, U> builder(Class<T> interfaceClass, U delegate) {
        return new Builder<T, U>(interfaceClass, delegate);
    }

    @SuppressWarnings("WeakerAccess") // intended for public consumption
    @SuppressFBWarnings(justification = "Catch-22: Checkstyle wants final builder, but FindBugs doesn't")
    public static final class Builder<T, U extends T> {

        private final Class<T> interfaceClass;
        private final U delegate;
        private final ImmutableList.Builder<InvocationEventHandler<InvocationContext>> handlers = ImmutableList
                .builder();
        private InstrumentationFilter filter = InstrumentationFilters.INSTRUMENT_ALL;

        private Builder(Class<T> interfaceClass, U delegate) {
            this.interfaceClass = checkNotNull(interfaceClass, "class");
            this.delegate = checkNotNull(delegate, "delegate");
        }

        public Builder<T, U> withMetrics(MetricRegistry metricRegistry) {
            checkNotNull(metricRegistry, "metricRegistry");
            this.handlers.add(new MetricsInvocationEventHandler(
                    metricRegistry,
                    MetricRegistry.name(interfaceClass.getName())));
            return this;
        }

        public Builder<T, U> withPerformanceTraceLogging() {
            return withLogging(
                    getPerformanceLoggerForInterface(interfaceClass),
                    LoggingLevel.TRACE,
                    LoggingInvocationEventHandler.LOG_DURATIONS_GREATER_THAN_1_MICROSECOND);
        }

        public Builder<T, U> withLogging(Logger logger, LoggingLevel loggingLevel, LongPredicate durationPredicate) {
            this.handlers.add(new LoggingInvocationEventHandler(logger, loggingLevel, durationPredicate));
            return this;
        }

        public Builder<T, U> withHandler(InvocationEventHandler<InvocationContext> handler) {
            checkNotNull(handler, "handler");
            return withHandlers(Collections.singleton(handler));
        }

        public Builder<T, U> withHandlers(Iterable<InvocationEventHandler<InvocationContext>> additionalHandlers) {
            checkNotNull(additionalHandlers, "additionalHandlers");
            this.handlers.addAll(additionalHandlers);
            return this;
        }

        public Builder<T, U> withFilter(InstrumentationFilter instrumentationFilter) {
            this.filter = checkNotNull(instrumentationFilter, "instrumentationFilter");
            return this;
        }

        public T build() {
            return wrap(interfaceClass, delegate, filter, handlers.build());
        }
    }

}
