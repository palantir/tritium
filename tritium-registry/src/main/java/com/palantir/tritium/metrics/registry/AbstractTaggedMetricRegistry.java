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
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

public abstract class AbstractTaggedMetricRegistry implements TaggedMetricRegistry {

    private final Map<MetricName, Metric> registry = new ConcurrentHashMap<>();
    private final Map<Map.Entry<String, String>, TaggedMetricSet> taggedRegistries = new ConcurrentHashMap<>();
    private final Supplier<Reservoir> reservoirSupplier;
    private final MultiTaggedMetricRegistryListener listeners = new MultiTaggedMetricRegistryListener();

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
        return getOrAdd(metricName, Counter.class, counterSupplier, listeners::onCounterAdded);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> Gauge<T> gauge(MetricName metricName, Gauge<T> gauge) {
        return getOrAdd(metricName, Gauge.class, () -> gauge, listeners::onGaugeAdded);
    }

    @Override
    public final Histogram histogram(MetricName metricName) {
        return histogram(metricName, histogramSupplier());
    }

    @Override
    public final Histogram histogram(MetricName metricName, Supplier<Histogram> histogramSupplier) {
        return getOrAdd(metricName, Histogram.class, histogramSupplier, listeners::onHistogramAdded);
    }

    @Override
    public final Meter meter(MetricName metricName) {
        return meter(metricName, meterSupplier());
    }

    @Override
    public final Meter meter(MetricName metricName, Supplier<Meter> meterSupplier) {
        return getOrAdd(metricName, Meter.class, meterSupplier, listeners::onMeterAdded);
    }

    @Override
    public final Timer timer(MetricName metricName) {
        return timer(metricName, timerSupplier());
    }

    @Override
    public final Timer timer(MetricName metricName, Supplier<Timer> timerSupplier) {
        return getOrAdd(metricName, Timer.class, timerSupplier, listeners::onTimerAdded);
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
        existingMetric.ifPresent(metric -> fireMetricRemoved(metric, metricName));
        return existingMetric;
    }

    private void fireMetricRemoved(Metric metric, MetricName metricName) {
        visitMetric(metric, new MetricVisitor<Void>() {
            @Override
            public Void visitGauge(Gauge<?> gauge) {
                listeners.onGaugeRemoved(metricName);
                return null;
            }

            @Override
            public Void visitMeter(Meter meter) {
                listeners.onMeterRemoved(metricName);
                return null;
            }

            @Override
            public Void visitHistogram(Histogram histogram) {
                listeners.onHistogramRemoved(metricName);
                return null;
            }

            @Override
            public Void visitTimer(Timer timer) {
                listeners.onTimerRemoved(metricName);
                return null;
            }

            @Override
            public Void visitCounter(Counter counter) {
                listeners.onCounterRemoved(metricName);
                return null;
            }
        });
    }

    private void fireMetricAdded(MetricName metricName, Metric metric) {
        visitMetric(metric, new MetricVisitor<Void>() {
            @Override
            public Void visitGauge(Gauge<?> gauge) {
                listeners.onGaugeAdded(metricName, (Gauge<?>) metric);
                return null;
            }

            @Override
            public Void visitMeter(Meter meter) {
                listeners.onMeterAdded(metricName, (Meter) metric);
                return null;
            }

            @Override
            public Void visitHistogram(Histogram histogram) {
                listeners.onHistogramAdded(metricName, (Histogram) metric);
                return null;
            }

            @Override
            public Void visitTimer(Timer timer) {
                listeners.onTimerAdded(metricName, (Timer) metric);
                return null;
            }

            @Override
            public Void visitCounter(Counter counter) {
                listeners.onCounterAdded(metricName, (Counter) metric);
                return null;
            }
        });
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
            Supplier<T> metricSupplier,
            BiConsumer<MetricName, T> onAddedHook) {

        // TODO(callumr): Is it ok to do so much work in a computeIfAbsent
        Metric metric = registry.computeIfAbsent(metricName, name -> {
            T newMetric = metricSupplier.get();
            onAddedHook.accept(name, newMetric);
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

    private <T> T visitMetric(Metric metric, MetricVisitor<T> visitor) {
        if (metric instanceof Gauge) {
            return visitor.visitGauge((Gauge<?>) metric);
        } else if (metric instanceof Meter) {
            return visitor.visitMeter((Meter) metric);
        } else if (metric instanceof Histogram) {
            return visitor.visitHistogram((Histogram) metric);
        } else if (metric instanceof Timer) {
            return visitor.visitTimer((Timer) metric);
        } else if (metric instanceof Counter) {
            return visitor.visitCounter((Counter) metric);
        }
        throw new SafeIllegalArgumentException("Unknown metric class", SafeArg.of("metricClass", metric.getClass()));
    }

    private interface MetricVisitor<T> {
        T visitGauge(Gauge<?> gauge);
        T visitMeter(Meter meter);
        T visitHistogram(Histogram histogram);
        T visitTimer(Timer timer);
        T visitCounter(Counter counter);
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
