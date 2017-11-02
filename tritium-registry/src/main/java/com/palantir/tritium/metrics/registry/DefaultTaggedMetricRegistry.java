/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.tritium.metrics.registry;

import com.codahale.metrics.Counter;
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class DefaultTaggedMetricRegistry implements TaggedMetricRegistry {

    private static final TaggedMetricRegistry DEFAULT = new DefaultTaggedMetricRegistry();

    private final Map<MetricName, Metric> registry = new ConcurrentHashMap<>();

    public DefaultTaggedMetricRegistry() {}

    /**
     * Get the global default {@link TaggedMetricRegistry}.
     */
    public static TaggedMetricRegistry getDefault() {
        return DefaultTaggedMetricRegistry.DEFAULT;
    }

    @Override
    public Counter counter(MetricName metric) {
        return getOrAdd(metric, Counter.class, Counter::new);
    }

    @Override
    public Gauge gauge(MetricName metric, Gauge gauge) {
        return getOrAdd(metric, Gauge.class, () -> gauge);
    }

    @Override
    public Histogram histogram(MetricName metric) {
        return getOrAdd(metric, Histogram.class, () -> new Histogram(new ExponentiallyDecayingReservoir()));
    }

    @Override
    public Meter meter(MetricName metric) {
        return getOrAdd(metric, Meter.class, Meter::new);
    }

    @Override
    public Timer timer(MetricName metric) {
        return getOrAdd(metric, Timer.class, Timer::new);
    }

    @Override
    public Map<MetricName, Metric> getMetrics() {
        return Collections.unmodifiableMap(registry);
    }

    private <T extends Metric> T getOrAdd(MetricName metricName, Class<T> metricClass, Supplier<T> metricSupplier) {
        Metric metric = registry.computeIfAbsent(metricName, name -> metricSupplier.get());
        if (!metricClass.isInstance(metric)) {
            throw new IllegalArgumentException(String.format(
                    "'%s' already used for a metric of type '%s' but wanted type '%s'. tags: %s",
                    metricName.safeName(), metric.getClass().getSimpleName(),
                    metricClass.getSimpleName(), metricName.safeTags()));
        }
        return metricClass.cast(metric);
    }
}
