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

import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.palantir.tritium.api.event.InvocationContext;
import com.palantir.tritium.api.event.InvocationEventHandler;
import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.tags.TaggedMetric;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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

    public static final String TAG_METHOD = "method";

    private final MetricRegistry metrics;
    private final TaggedMetric metric;
    private final Function<InvocationContext, TaggedMetric> enrichWithContext;

    private MetricsInvocationEventHandler(MetricRegistry metrics, TaggedMetric metric,
            Function<InvocationContext, TaggedMetric> enrichWithContext) {
        super(getEnabledSupplier(checkNotNull(metric, "metric").name()));
        this.metrics = checkNotNull(metrics, "metrics");
        this.metric = metric;
        this.enrichWithContext = enrichWithContext;
    }

    @VisibleForTesting
    static MetricsInvocationEventHandler create(MetricRegistry metrics, String metricName) {
        return create(metrics, () -> TaggedMetric.from(metricName));
    }

    // TODO (davids): JavaDoc
    public static MetricsInvocationEventHandler create(MetricRegistry metrics, Supplier<TaggedMetric> metricSupplier) {
        TaggedMetric baseMetric = metricSupplier.get();
        return create(metrics, () -> baseMetric, contextToMetric(baseMetric));
    }

    public static MetricsInvocationEventHandler create(MetricRegistry metrics, Supplier<TaggedMetric> metricSupplier,
            Function<InvocationContext, TaggedMetric> enrichWithContext) {
        TaggedMetric baseMetric = metricSupplier.get();
        return new MetricsInvocationEventHandler(metrics, baseMetric, enrichWithContext);
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
        String canonicalMetricName = enrichWithContext.apply(context).canonicalName();
        metrics.timer(canonicalMetricName).update(elapsedNanoseconds, TimeUnit.NANOSECONDS);
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
        if (context == null) {
            metrics.meter(
                    TaggedMetric.builder()
                            .from(metric)
                            .putTags("error", cause.getClass().getName())
                            .build()
                            .canonicalName())
                    .mark();
            logger.debug("Encountered null metric context likely due to exception in preInvocation: {}", cause, cause);
            return;
        }

        String canonicalMetricName = TaggedMetric.builder()
                .from(metric)
                .putTags("method", context.getMethod().getName())
                .putTags("error", cause.getClass().getName())
                .build()
                .canonicalName();
        long elapsedNanoseconds = System.nanoTime() - context.getStartTimeNanos();
        metrics.timer(canonicalMetricName).update(elapsedNanoseconds, TimeUnit.NANOSECONDS);
    }

    private static Function<InvocationContext, TaggedMetric> contextToMetric(TaggedMetric taggedMetric) {
        return (context) -> TaggedMetric.builder()
                .from(taggedMetric)
                .putTags(TAG_METHOD, context.getMethod().getName())
                // TODO (davids): tag args?
                .build();
    }

}
