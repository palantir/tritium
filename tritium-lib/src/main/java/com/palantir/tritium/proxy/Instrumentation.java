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

package com.palantir.tritium.proxy;

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.Preconditions;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.v1.api.event.InstrumentationFilter;
import com.palantir.tritium.v1.api.event.InvocationContext;
import com.palantir.tritium.v1.api.event.InvocationEventHandler;
import com.palantir.tritium.v1.core.event.InstrumentationFilters;
import com.palantir.tritium.v1.core.event.InstrumentationProperties;
import com.palantir.tritium.v1.metrics.event.MetricsInvocationEventHandler;
import com.palantir.tritium.v1.metrics.event.TaggedMetricsServiceInvocationEventHandler;
import com.palantir.tritium.v1.slf4j.event.LoggingInvocationEventHandler;
import com.palantir.tritium.v1.slf4j.event.LoggingLevel;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.LongPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrument arbitrary service interfaces with optional metrics and invocation logging.
 * @deprecated use {@link com.palantir.tritium.v1.lib.Instrumentation}
 */
@Deprecated
public final class Instrumentation {

    private Instrumentation() {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("deprecation") // backward compatibility for InstrumentationFilters.INSTRUMENT_NONE
    static <T, U extends T> T wrap(
            Class<T> interfaceClass,
            U delegate,
            List<InvocationEventHandler<InvocationContext>> handlers,
            InstrumentationFilter instrumentationFilter) {
        checkNotNull(interfaceClass, "interfaceClass");
        checkNotNull(delegate, "delegate");
        checkNotNull(instrumentationFilter, "instrumentationFilter");
        checkNotNull(handlers, "handlers");

        if (handlers.isEmpty()
                || InstrumentationFilters.instrumentNone().equals(instrumentationFilter)
                || com.palantir.tritium.event.InstrumentationFilters.INSTRUMENT_NONE.equals(instrumentationFilter)) {
            return delegate;
        }

        if (InstrumentationProperties.isSpecificallyEnabled("dynamic-proxy", false)) {
            return Proxies.newProxy(
                    interfaceClass, delegate, new InstrumentationProxy<>(instrumentationFilter, handlers, delegate));
        } else {
            return ByteBuddyInstrumentation.instrument(interfaceClass, delegate, handlers, instrumentationFilter);
        }
    }

    /**
     * Wraps delegate with instrumentation.
     *
     * @deprecated Use {@link #wrap(Class, Object, List, InstrumentationFilter)}
     */
    @Deprecated // remove post 1.0
    static <T, U extends T> T wrap(
            Class<T> interfaceClass, U delegate, List<InvocationEventHandler<InvocationContext>> handlers) {
        return wrap(interfaceClass, delegate, handlers, InstrumentationFilters.instrumentAll());
    }

    /**
     * Return an instrumented proxy of the specified service interface and delegate that records aggregated invocation
     * metrics and performance trace logging.
     *
     * @param serviceInterface service interface
     * @param delegate delegate to instrument
     * @param metricRegistry metric registry
     * @return instrumented proxy implementing specified service interface
     * @deprecated use {@link com.palantir.tritium.v1.lib.Tritium#instrument(Class, Object, MetricRegistry)}
     */
    @Deprecated // remove post 1.0
    public static <T, U extends T> T instrument(Class<T> serviceInterface, U delegate, MetricRegistry metricRegistry) {
        return builder(serviceInterface, delegate)
                .withFilter(InstrumentationFilters.instrumentAll())
                .withMetrics(metricRegistry)
                .withPerformanceTraceLogging()
                .build();
    }

    public static <T> Logger getPerformanceLoggerForInterface(Class<T> serviceInterface) {
        return LoggerFactory.getLogger("performance." + serviceInterface.getName());
    }

    public static <T, U extends T> Builder<T, U> builder(Class<T> interfaceClass, U delegate) {
        return new Builder<>(interfaceClass, delegate);
    }

    @SuppressWarnings("WeakerAccess") // intended for public consumption
    public static final class Builder<T, U extends T> {

        private final Class<T> interfaceClass;
        private final U delegate;
        private final ImmutableList.Builder<InvocationEventHandler<InvocationContext>> handlers =
                ImmutableList.builder();
        private InstrumentationFilter filter = InstrumentationFilters.instrumentAll();

        private Builder(Class<T> interfaceClass, U delegate) {
            this.interfaceClass = checkNotNull(interfaceClass, "class");
            this.delegate = checkNotNull(delegate, "delegate");
        }

        /**
         * Supply additional metrics name prefix to be used across interfaces that use MetricGroup annotations.
         *
         * @param metricRegistry - MetricsRegistry used for this application
         * @param globalPrefix - Metrics name prefix to be used
         * @return - InstrumentationBuilder
         * @deprecated use {@link #withTaggedMetrics(TaggedMetricRegistry)} or {@link #withMetrics(MetricRegistry)}
         */
        @Deprecated // remove post 1.0
        public Builder<T, U> withMetrics(MetricRegistry metricRegistry, String globalPrefix) {
            checkNotNull(metricRegistry, "metricRegistry");
            if (Strings.isNullOrEmpty(globalPrefix)) {
                return withMetrics(metricRegistry);
            } else {
                // deprecated path
                return withHandler(new com.palantir.tritium.event.metrics.MetricsInvocationEventHandler(
                        metricRegistry, delegate.getClass(), interfaceClass.getName(), globalPrefix));
            }
        }

        public Builder<T, U> withMetrics(MetricRegistry metricRegistry) {
            checkNotNull(metricRegistry, "metricRegistry");
            return withHandler(new MetricsInvocationEventHandler(metricRegistry, interfaceClass.getName()));
        }

        /**
         * Supplies a TaggedMetricRegistry and a name prefix to be used across service invocations.
         *
         * <p>Uses a {@link TaggedMetricsServiceInvocationEventHandler} object for handling invocations, so metric names
         * are chosen based off of the interface name and invoked method.
         *
         * @param metricRegistry - TaggedMetricsRegistry used for this application.
         * @param prefix - Metrics name prefix to be used
         * @return - InstrumentationBuilder
         */
        public Builder<T, U> withTaggedMetrics(TaggedMetricRegistry metricRegistry, String prefix) {
            checkNotNull(metricRegistry, "metricRegistry");
            String serviceName = Strings.isNullOrEmpty(prefix) ? interfaceClass.getName() : prefix;
            return withHandler(new TaggedMetricsServiceInvocationEventHandler(metricRegistry, serviceName));
        }

        public Builder<T, U> withTaggedMetrics(TaggedMetricRegistry metricRegistry) {
            return withTaggedMetrics(metricRegistry, "");
        }

        public Builder<T, U> withPerformanceTraceLogging() {
            return withLogging(
                    getPerformanceLoggerForInterface(interfaceClass),
                    LoggingLevel.TRACE,
                    LoggingInvocationEventHandler.logDurationsGreaterThan(Duration.ofNanos(1000)));
        }

        /**
         * Bridge for backward compatibility.
         *
         * @deprecated use {@link #withLogging(Logger, LoggingLevel, LongPredicate)}
         */
        @Deprecated // remove post 1.0
        @SuppressWarnings("FunctionalInterfaceClash") // backward compatibility
        public Builder<T, U> withLogging(
                Logger logger,
                com.palantir.tritium.event.log.LoggingLevel loggingLevel,
                com.palantir.tritium.api.functions.LongPredicate durationPredicate) {
            Preconditions.checkNotNull(loggingLevel, "level");
            return withLogging(logger, loggingLevel.asV1(), durationPredicate);
        }

        /**
         * Bridge for backward compatibility.
         *
         * @deprecated use {@link #withLogging(Logger, LoggingLevel, LongPredicate)}
         */
        @Deprecated // remove post 1.0
        @SuppressWarnings("FunctionalInterfaceClash") // backward compatibility
        public Builder<T, U> withLogging(
                Logger logger,
                com.palantir.tritium.event.log.LoggingLevel loggingLevel,
                LongPredicate durationPredicate) {
            return withLogging(logger, loggingLevel.asV1(), durationPredicate);
        }

        @SuppressWarnings("FunctionalInterfaceClash") // backward compatibility
        public Builder<T, U> withLogging(Logger logger, LoggingLevel loggingLevel, LongPredicate durationPredicate) {
            return withHandler(new LoggingInvocationEventHandler(logger, loggingLevel, durationPredicate));
        }

        /**
         * Bridge for backward compatibility.
         * @deprecated use {@link #withHandler(InvocationEventHandler)}
         */
        @Deprecated // remove post 1.0
        public Builder<T, U> withHandler(
                com.palantir.tritium.event.InvocationEventHandler<com.palantir.tritium.event.InvocationContext>
                        handler) {
            checkNotNull(handler, "handler");
            return withHandlers(Collections.singleton(handler.asV1()));
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

        /**
         * Bridge for backward compatibility.
         * @deprecated use {@link #withFilter(InstrumentationFilter)}
         */
        @Deprecated // remove post 1.0
        @SuppressWarnings("FunctionalInterfaceClash") // backward compatibility
        public Builder<T, U> withFilter(com.palantir.tritium.api.event.InstrumentationFilter instrumentationFilter) {
            return withFilter((InstrumentationFilter) instrumentationFilter);
        }

        public Builder<T, U> withFilter(InstrumentationFilter instrumentationFilter) {
            this.filter = checkNotNull(instrumentationFilter, "instrumentationFilter");
            return this;
        }

        public T build() {
            return wrap(interfaceClass, delegate, handlers.build(), filter);
        }
    }
}
