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

package com.palantir.tritium.annotations.internal;

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.event.CompositeInvocationEventHandler;
import com.palantir.tritium.event.InstrumentationFilters;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import com.palantir.tritium.event.log.LoggingInvocationEventHandler;
import com.palantir.tritium.event.log.LoggingLevel;
import com.palantir.tritium.event.metrics.TaggedMetricsServiceInvocationEventHandler;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.tracing.TracingInvocationEventHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InstrumentationBuilder<T, U extends T> {

    private final Class<? super T> interfaceClass;
    private final T delegate;
    private final InstrumentedTypeFactory<T, U> factory;
    private final List<InvocationEventHandler<InvocationContext>> handlers = new ArrayList<>();
    private InstrumentationFilter filter = InstrumentationFilters.INSTRUMENT_ALL;

    public InstrumentationBuilder(Class<? super T> interfaceClass, T delegate, InstrumentedTypeFactory<T, U> factory) {
        this.interfaceClass = checkNotNull(interfaceClass, "class");
        this.factory = checkNotNull(factory, "factory");
        this.delegate = checkNotNull(delegate, "delegate");
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
    @CanIgnoreReturnValue
    public InstrumentationBuilder<T, U> withTaggedMetrics(
            TaggedMetricRegistry metricRegistry, @Nullable String prefix) {
        checkNotNull(metricRegistry, "metricRegistry");
        String serviceName = prefix == null || prefix.isEmpty() ? interfaceClass.getName() : prefix;
        return withHandler(new TaggedMetricsServiceInvocationEventHandler(metricRegistry, serviceName));
    }

    @CanIgnoreReturnValue
    public InstrumentationBuilder<T, U> withTaggedMetrics(TaggedMetricRegistry metricRegistry) {
        return withTaggedMetrics(metricRegistry, "");
    }

    @CanIgnoreReturnValue
    public InstrumentationBuilder<T, U> withPerformanceTraceLogging() {
        return withLogging(
                getPerformanceLoggerForInterface(interfaceClass),
                LoggingLevel.TRACE,
                LoggingInvocationEventHandler.LOG_DURATIONS_GREATER_THAN_1_MICROSECOND);
    }

    private static <T> Logger getPerformanceLoggerForInterface(Class<T> serviceInterface) {
        return LoggerFactory.getLogger("performance." + serviceInterface.getName());
    }

    @CanIgnoreReturnValue
    public InstrumentationBuilder<T, U> withLogging(
            Logger logger, LoggingLevel loggingLevel, LongPredicate durationPredicate) {
        return withHandler(new LoggingInvocationEventHandler(logger, loggingLevel, durationPredicate));
    }

    @CanIgnoreReturnValue
    public InstrumentationBuilder<T, U> withTracing() {
        return withHandler(TracingInvocationEventHandler.create(interfaceClass.getName()));
    }

    @CanIgnoreReturnValue
    public InstrumentationBuilder<T, U> withHandler(InvocationEventHandler<InvocationContext> handler) {
        checkNotNull(handler, "handler");
        this.handlers.add(handler);
        return this;
    }

    @CanIgnoreReturnValue
    public InstrumentationBuilder<T, U> withHandlers(
            Iterable<InvocationEventHandler<InvocationContext>> additionalHandlers) {
        checkNotNull(additionalHandlers, "additionalHandlers");
        for (InvocationEventHandler<InvocationContext> handler : additionalHandlers) {
            withHandler(handler);
        }
        return this;
    }

    @CanIgnoreReturnValue
    public InstrumentationBuilder<T, U> withFilter(InstrumentationFilter instrumentationFilter) {
        this.filter = checkNotNull(instrumentationFilter, "instrumentationFilter");
        return this;
    }

    @CheckReturnValue
    public U build() {
        return factory.create(delegate, CompositeInvocationEventHandler.of(handlers), filter);
    }
}
