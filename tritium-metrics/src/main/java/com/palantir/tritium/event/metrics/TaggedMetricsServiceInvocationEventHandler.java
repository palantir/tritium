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

package com.palantir.tritium.event.metrics;

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An implementation of {@link AbstractInvocationEventHandler} whose purpose is to provide tagged metrics for
 * classes which look like services.
 *
 * Specifically, this class will generate metrics with the following parameters:
 *
 * <ul>
 *     <li>Metric Name: the service name supplied to the constructor</li>
 *     <li>Tag - service-name: The simple name of the invoked class</li>
 *     <li>Tag - endpoint: The name of the method that was invoked</li>
 *     <li>Tag - cause: When an error is hit, this will be filled with the full class name of the cause.</li>
 * </ul>
 */
public class TaggedMetricsServiceInvocationEventHandler
        extends AbstractInvocationEventHandler<MetricsInvocationContext> {

    private static final String FAILURES_METRIC_NAME = "failures";
    private static final MetricName FAILURES_METRIC = MetricName.builder().safeName(FAILURES_METRIC_NAME).build();

    private final TaggedMetricRegistry taggedMetricRegistry;
    private final String serviceName;
    private final Meter globalFailureMeter;
    private final ConcurrentMap<Method, Timer> timerCache = new ConcurrentHashMap<>();
    private final Function<Method, Timer> onSuccessTimerMappingFunction;

    public TaggedMetricsServiceInvocationEventHandler(
            TaggedMetricRegistry taggedMetricRegistry,
            String serviceName) {
        super(getEnabledSupplier(serviceName));
        this.taggedMetricRegistry = checkNotNull(taggedMetricRegistry, "metricRegistry");
        this.serviceName = checkNotNull(serviceName, "serviceName");
        this.globalFailureMeter = taggedMetricRegistry.meter(FAILURES_METRIC);
        this.onSuccessTimerMappingFunction = method -> taggedMetricRegistry.timer(MetricName.builder()
                .safeName(serviceName)
                .putSafeTags("service-name", method.getDeclaringClass().getSimpleName())
                .putSafeTags("endpoint", method.getName())
                .build());
    }

    @SuppressWarnings("NoFunctionalReturnType") // helper
    private static BooleanSupplier getEnabledSupplier(final String serviceName) {
        return InstrumentationProperties.getSystemPropertySupplier(serviceName);
    }

    @Override
    public final MetricsInvocationContext preInvocation(
            @SuppressWarnings("unused") @Nonnull Object instance,
            @Nonnull Method method,
            @SuppressWarnings("unused") @Nonnull Object[] args) {
        return new MetricsInvocationContext(method, System.nanoTime());
    }

    @Override
    public final void onSuccess(@Nullable MetricsInvocationContext context, @Nullable Object unusedResult) {
        debugIfNullContext(context);
        if (context != null) {
            long nanos = System.nanoTime() - context.getStartTimeNanos();
            getSuccessTimer(context.getMethod()).update(nanos, TimeUnit.NANOSECONDS);
        }
    }

    private Timer getSuccessTimer(Method method) {
        return timerCache.computeIfAbsent(method, onSuccessTimerMappingFunction);
    }

    @Override
    public final void onFailure(@Nullable MetricsInvocationContext context, @Nonnull Throwable cause) {
        globalFailureMeter.mark();
        debugIfNullContext(context);
        if (context != null) {
            MetricName failuresMetricName = MetricName.builder()
                    .safeName(serviceName + "-" + FAILURES_METRIC_NAME)
                    .putSafeTags("service-name", context.getMethod().getDeclaringClass().getSimpleName())
                    .putSafeTags("endpoint", context.getMethod().getName())
                    .putSafeTags("cause", cause.getClass().getName())
                    .build();
            taggedMetricRegistry.meter(failuresMetricName).mark();
        }
    }
}
