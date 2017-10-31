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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.api.event.InvocationContext;
import com.palantir.tritium.api.event.InvocationEventHandler;
import com.palantir.tritium.event.InstrumentationFilters;
import com.palantir.tritium.event.log.LoggingInvocationEventHandler;
import com.palantir.tritium.event.log.LoggingLevel;
import com.palantir.tritium.event.metrics.MetricsInvocationEventHandler;
import com.palantir.tritium.event.metrics.ServiceMetricNameFunction;
import com.palantir.tritium.event.metrics.TagsFunction;
import com.palantir.tritium.metrics.MetricName;
import com.palantir.tritium.metrics.MetricNames;
import com.palantir.tritium.metrics.TaggedMetricRegistry;
import com.palantir.tritium.metrics.Tags;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.LongPredicate;
import java.util.function.Supplier;
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
            Class<T> interfaceClass,
            final U delegate,
            final InstrumentationFilter instrumentationFilter,
            final List<InvocationEventHandler<InvocationContext>> handlers) {

        checkNotNull(interfaceClass, "interfaceClass");
        checkNotNull(delegate, "delegate");
        checkNotNull(instrumentationFilter, "instrumentationFilter");
        checkNotNull(handlers, "handlers");

        if (handlers.isEmpty() || instrumentationFilter == InstrumentationFilters.INSTRUMENT_NONE) {
            return delegate;
        }

        return Proxies.newProxy(interfaceClass, delegate,
                new InstrumentationProxy<>(instrumentationFilter, handlers, delegate));
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

    public static <T> Logger getPerformanceLoggerForInterface(Class<T> serviceInterface) {
        return LoggerFactory.getLogger("performance." + serviceInterface.getName());
    }

    @SuppressFBWarnings(justification = "Catch-22: Checkstyle wants final builder, but FindBugs doesn't")
    public static <T, U extends T> Builder<T, U> builder(Class<T> interfaceClass, U delegate) {
        return new Builder<>(interfaceClass, delegate);
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

        public Builder<T, U> withMetrics(TaggedMetricRegistry metrics) {
            return withMetrics(metrics,
                    (serviceInterface, implementation) -> MetricName.builder()
                            .safeName(MetricNames.internalServiceResponse())
                            .putSafeTags(Tags.SERVICE.key(), serviceInterface.getSimpleName())
                            .build(),
                    (serviceInterface1, service) -> ImmutableMap.of());
        }

        public Builder<T, U> withMetrics(
                TaggedMetricRegistry metrics,
                ServiceMetricNameFunction serviceMetricNameFunction,
                TagsFunction tagsFunction) {

            checkNotNull(metrics, "metrics");
            checkNotNull(serviceMetricNameFunction, "serviceMetricNameFunction");
            checkNotNull(tagsFunction, "tagsFunction");

            BiFunction<Class<?>, Object, MetricName> metricFunction = serviceMetricNameFunction
                    .andThen(taggedMetric -> {
                        Map<String, String> tags = tagsFunction.apply(interfaceClass, delegate);
                        return MetricsInvocationEventHandler.metricNameWithTags(taggedMetric, tags);
                    });
            Supplier<MetricName> taggedMetricSupplier = () -> metricFunction.apply(interfaceClass, delegate);
            this.handlers.add(MetricsInvocationEventHandler.create(metrics, taggedMetricSupplier));
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
