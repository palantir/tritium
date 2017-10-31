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

package com.palantir.tritium.event.metrics;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.palantir.tritium.api.event.InvocationContext;
import com.palantir.tritium.api.event.InvocationEventHandler;
import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.metrics.MetricName;
import com.palantir.tritium.metrics.TaggedMetricRegistry;
import com.palantir.tritium.metrics.Tags;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link InvocationEventHandler} that records method timing and failures using Dropwizard metrics.
 */
public final class MetricsInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final Logger logger = LoggerFactory.getLogger(MetricsInvocationEventHandler.class);

    private final TaggedMetricRegistry metrics;
    private final MetricName metric;
    private final InvocationContextTagsFunction enrichWithContext;

    private MetricsInvocationEventHandler(TaggedMetricRegistry metrics,
            MetricName metric,
            InvocationContextTagsFunction enrichWithContext) {
        super(getEnabledSupplier(checkNotNull(metric, "metric").safeName()));
        this.metrics = checkNotNull(metrics, "metrics");
        this.metric = metric;
        this.enrichWithContext = enrichWithContext;
    }

    /**
     * Creates a metrics invocation event handler that reports to the specified metrics registry.
     *
     * @param metrics metrics registry
     * @param baseMetricNameSupplier supplier for base metric name
     * @return metrics invocation handler
     */
    // TODO (davids): JavaDoc
    public static MetricsInvocationEventHandler create(TaggedMetricRegistry metrics,
            Supplier<MetricName> baseMetricNameSupplier) {
        return create(metrics, baseMetricNameSupplier, methodNameTag());
    }

    public static MetricsInvocationEventHandler create(TaggedMetricRegistry metrics,
            Supplier<MetricName> baseMetricNameSupplier,
            InvocationContextTagsFunction enrichWithContext) {
        return new MetricsInvocationEventHandler(metrics, baseMetricNameSupplier.get(), enrichWithContext);
    }

    public static InvocationContextTagsFunction methodNameTag() {
        return (context) -> ImmutableMap.of(Tags.METHOD.key(), context.getMethod().getName());
    }

    static BooleanSupplier getEnabledSupplier(final String serviceName) {
        return InstrumentationProperties.getSystemPropertySupplier(serviceName);
    }

    @Override
    public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
        return DefaultInvocationContext.of(instance, method, args);
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        if (context == null) {
            logger.debug("Encountered null metric context likely due to exception in preInvocation");
            return;
        }
        long elapsedNanoseconds = System.nanoTime() - context.getStartTimeNanos();
        MetricName metricName = enrichWithTags(context);
        metrics.timer(metricName)
                .update(elapsedNanoseconds, TimeUnit.NANOSECONDS);
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
        metrics.meter(exceptionMetricName(cause)).mark();
        if (context == null) {
            logger.debug("Encountered null metric context likely due to exception in preInvocation: {}", cause, cause);
            return;
        }

        long elapsedNanoseconds = System.nanoTime() - context.getStartTimeNanos();
        MetricName metricName = MetricName.builder()
                .from(metric)
                .putSafeTags(Tags.METHOD.key(), context.getMethod().getName())
                .putSafeTags(Tags.ERROR.key(), cause.getClass().getName())
                .build();
        metrics.timer(metricName)
                .update(elapsedNanoseconds, TimeUnit.NANOSECONDS);
    }

    public static MetricName metricNameWithTags(MetricName metricName, Map<String, String> tags) {
        Preconditions.checkNotNull(metricName, "metricName");
        return tags.isEmpty()
                ? metricName
                : MetricName.builder()
                        .from(metricName)
                        .putAllSafeTags(tags)
                        .build();
    }

    private MetricName enrichWithTags(@Nonnull InvocationContext context) {
        Map<String, String> additionalTags = enrichWithContext.apply(context);
        return metricNameWithTags(metric, additionalTags);
    }

    private MetricName exceptionMetricName(@Nonnull Throwable cause) {
        return MetricName.builder()
                .from(metric)
                .putSafeTags(Tags.ERROR.key(), cause.getClass().getName())
                .build();
    }

}
