/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.tritium.metrics.registry.listeners.MultiTaggedMetricRegistryListener;
import com.palantir.tritium.metrics.registry.listeners.TaggedMetricRegistryListener;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

public abstract class AbstractTaggedMetricRegistry implements TaggedMetricRegistry {

    private final Map<MetricName, Metric> registry = new ConcurrentHashMap<>();
    private final Map<Map.Entry<String, String>, TaggedMetricSet> taggedRegistries = new ConcurrentHashMap<>();
    private final Supplier<Reservoir> reservoirSupplier;
    private final MultiTaggedMetricRegistryListener listeners = new MultiTaggedMetricRegistryListener();
    private final AutoDispatchingListener autoDispatchingListener =
            new AutoDispatchingListener(listeners);

    public AbstractTaggedMetricRegistry(Supplier<Reservoir> reservoirSupplier) {
        this.reservoirSupplier = checkNotNull(reservoirSupplier, "reservoirSupplier");
    }

    /**
     * Supplies counter instances for this registry.
     *
     * @return counter supplier
     */
    @Nonnull
    protected Supplier<Counter> counterSupplier() {
        return Counter::new;
    }

    /**
     * Supplies histogram instances for this registry.
     *
     * @return histogram supplier
     */
    @Nonnull
    protected Supplier<Histogram> histogramSupplier() {
        return () -> new Histogram(createReservoir());
    }

    /**
     * Supplies meter instances for this registry.
     *
     * @return meter supplier
     */
    @Nonnull
    protected Supplier<Meter> meterSupplier() {
        return Meter::new;
    }

    /**
     * Supplies timer instances for this registry.
     *
     * @return timer supplier
     */
    @Nonnull
    protected Supplier<Timer> timerSupplier() {
        return () -> new Timer(createReservoir());
    }

    /**
     * Supplies reservoir instances for this registry.
     *
     * @return reservoir supplier
     */
    @Nonnull
    protected final Reservoir createReservoir() {
        return this.reservoirSupplier.get();
    }

    @Override
    public final Counter counter(MetricName metricName) {
        return counter(metricName, counterSupplier());
    }

    @Override
    public final Counter counter(MetricName metricName, Supplier<Counter> counterSupplier) {
        return getOrAdd(metricName, Counter.class, counterSupplier);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> Gauge<T> gauge(MetricName metricName, Gauge<T> gauge) {
        return getOrAdd(metricName, Gauge.class, () -> gauge);
    }

    @Override
    public final Histogram histogram(MetricName metricName) {
        return histogram(metricName, histogramSupplier());
    }

    @Override
    public final Histogram histogram(MetricName metricName, Supplier<Histogram> histogramSupplier) {
        return getOrAdd(metricName, Histogram.class, histogramSupplier);
    }

    @Override
    public final Meter meter(MetricName metricName) {
        return meter(metricName, meterSupplier());
    }

    @Override
    public final Meter meter(MetricName metricName, Supplier<Meter> meterSupplier) {
        return getOrAdd(metricName, Meter.class, meterSupplier);
    }

    @Override
    public final Timer timer(MetricName metricName) {
        return timer(metricName, timerSupplier());
    }

    @Override
    public final Timer timer(MetricName metricName, Supplier<Timer> timerSupplier) {
        return getOrAdd(metricName, Timer.class, timerSupplier);
    }

    @Override
    public final Map<MetricName, Metric> getMetrics() {
        ImmutableMap.Builder<MetricName, Metric> result = ImmutableMap.builder();
        result.putAll(registry);
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
    public final Optional<Metric> remove(MetricName metricName) {
        Optional<Metric> existingMetric = Optional.ofNullable(registry.remove(metricName));
        existingMetric.ifPresent(metric -> autoDispatchingListener.metricRemoved(metric, metricName));
        return existingMetric;
    }

    @Override
    public final void addMetrics(String safeTagName, String safeTagValue, TaggedMetricSet other) {
        taggedRegistries.put(Maps.immutableEntry(safeTagName, safeTagValue), other);
    }

    @Override
    public final Optional<TaggedMetricSet> removeMetrics(String safeTagName, String safeTagValue) {
        return Optional.ofNullable(taggedRegistries.remove(Maps.immutableEntry(safeTagName, safeTagValue)));
    }

    @Override
    public final boolean removeMetrics(
            String safeTagName, String safeTagValue, TaggedMetricSet metrics) {
        return taggedRegistries.remove(Maps.immutableEntry(safeTagName, safeTagValue), metrics);
    }

    protected final <T extends Metric> T getOrAdd(
            MetricName metricName,
            Class<T> metricClass,
            Supplier<T> metricSupplier) {

        // TODO(callumr): Is it ok to do so much work in a computeIfAbsent
        Metric metric = registry.computeIfAbsent(metricName, name -> {
            T newMetric = metricSupplier.get();
            autoDispatchingListener.metricRemoved(metricName, newMetric);
            return newMetric;
        });
        if (!metricClass.isInstance(metric)) {
            throw new SafeIllegalArgumentException(
                    "Metric name already used for different metric type",
                    SafeArg.of("metricName", metricName.safeName()),
                    SafeArg.of("existingMetricType", metric.getClass().getSimpleName()),
                    SafeArg.of("newMetricType", metricClass.getSimpleName()),
                    SafeArg.of("safeTags", metricName.safeTags()));
        }
        return metricClass.cast(metric);
    }

    @Override
    public final void addListener(TaggedMetricRegistryListener listener) {
        listeners.addListener(listener);
    }

    @Override
    public final void removeListener(TaggedMetricRegistryListener listener) {
        listeners.removeListener(listener);
    }
}
