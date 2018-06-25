/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@AutoService(TaggedMetricRegistry.class)
public final class DefaultTaggedMetricRegistry implements TaggedMetricRegistry {

    private static final TaggedMetricRegistry DEFAULT = new DefaultTaggedMetricRegistry();

    private final Map<MetricName, Metric> registry = new ConcurrentHashMap<>();
    private final Map<Map.Entry<String, String>, TaggedMetricSet> taggedRegistries = new ConcurrentHashMap<>();

    public DefaultTaggedMetricRegistry() {}

    /**
     * Get the global default {@link TaggedMetricRegistry}.
     */
    public static TaggedMetricRegistry getDefault() {
        return DefaultTaggedMetricRegistry.DEFAULT;
    }

    @Override
    public Counter counter(MetricName metricName) {
        return counter(metricName, Counter::new);
    }

    @Override
    public Counter counter(MetricName metricName, Supplier<Counter> counterSupplier) {
        return getOrAdd(metricName, Counter.class, counterSupplier);
    }

    @Override
    public Gauge gauge(MetricName metricName, Gauge gauge) {
        return getOrAdd(metricName, Gauge.class, () -> gauge);
    }

    @Override
    public Histogram histogram(MetricName metricName) {
        return histogram(metricName, () -> new Histogram(new ExponentiallyDecayingReservoir()));
    }

    @Override
    public Histogram histogram(MetricName metricName, Supplier<Histogram> histogramSupplier) {
        return getOrAdd(metricName, Histogram.class, histogramSupplier);
    }

    @Override
    public Meter meter(MetricName metricName) {
        return meter(metricName, Meter::new);
    }

    @Override
    public Meter meter(MetricName metricName, Supplier<Meter> meterSupplier) {
        return getOrAdd(metricName, Meter.class, meterSupplier);
    }

    @Override
    public Timer timer(MetricName metricName) {
        return timer(metricName, Timer::new);
    }

    @Override
    public Timer timer(MetricName metricName, Supplier<Timer> timerSupplier) {
        return getOrAdd(metricName, Timer.class, timerSupplier);
    }

    @Override
    public Map<MetricName, Metric> getMetrics() {
        ImmutableMap.Builder<MetricName, Metric> result = ImmutableMap.<MetricName, Metric>builder()
                .putAll(registry);
        taggedRegistries.forEach((tag, metrics) -> metrics.getMetrics()
                .forEach((metricName, metric) -> result.put(
                        MetricName.builder()
                                .from(metricName)
                                .putSafeTags(tag.getKey(), tag.getValue())
                                .build(),
                        metric)));

        return result.build();
    }

    @Override
    public Optional<Metric> remove(MetricName metricName) {
        return Optional.ofNullable(registry.remove(metricName));
    }

    @Override
    public void addMetrics(String safeTagName, String safeTagValue, TaggedMetricSet other) {
        taggedRegistries.put(Maps.immutableEntry(safeTagName, safeTagValue), other);
    }

    @Override
    public void removeMetrics(String safeTagName, String safeTagValue) {
        taggedRegistries.remove(Maps.immutableEntry(safeTagName, safeTagValue));
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
