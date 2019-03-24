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

import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
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
public class TaggedMetricsServiceInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final String FAILURES_METRIC_NAME = "failures";
    private static final MetricName FAILURES_METRIC = MetricName.builder().safeName(FAILURES_METRIC_NAME).build();

    private final TaggedMetricRegistry taggedMetricRegistry;
    private final String serviceName;

    public TaggedMetricsServiceInvocationEventHandler(
            TaggedMetricRegistry taggedMetricRegistry,
            String serviceName) {
        super(getEnabledSupplier(serviceName));
        this.taggedMetricRegistry = checkNotNull(taggedMetricRegistry, "metricRegistry");
        this.serviceName = checkNotNull(serviceName, "serviceName");
    }

    private static BooleanSupplier getEnabledSupplier(final String serviceName) {
        return InstrumentationProperties.getSystemPropertySupplier(serviceName);
    }

    @Override
    public final InvocationContext preInvocation(
            @Nonnull Object instance,
            @Nonnull Method method,
            @Nonnull Object[] args) {
        return DefaultInvocationContext.of(instance, method, args);
    }

    @Override
    public final void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        if (isNonNullContext(context)) {
            long nanos = System.nanoTime() - context.getStartTimeNanos();
            MetricName finalMetricName = MetricName.builder()
                    .safeName(serviceName)
                    .putSafeTags("service-name", context.getMethod().getDeclaringClass().getSimpleName())
                    .putSafeTags("endpoint", context.getMethod().getName())
                    .build();
            taggedMetricRegistry.timer(finalMetricName)
                    .update(nanos, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public final void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
        markGlobalFailure();
        if (isNonNullContext(context)) {
            MetricName failuresMetricName = MetricName.builder()
                    .safeName(serviceName + "-" + FAILURES_METRIC_NAME)
                    .putSafeTags("service-name", context.getMethod().getDeclaringClass().getSimpleName())
                    .putSafeTags("endpoint", context.getMethod().getName())
                    .putSafeTags("cause", cause.getClass().getName())
                    .build();
            taggedMetricRegistry.meter(failuresMetricName).mark();
        }
    }

    private void markGlobalFailure() {
        taggedMetricRegistry.meter(FAILURES_METRIC).mark();
    }
}
