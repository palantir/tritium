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
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.tritium.samples.HistogramWithSamples;
import com.palantir.tritium.samples.LockFreeSampler;
import com.palantir.tritium.tracing.Tracer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTaggedMetricRegistry implements TaggedMetricRegistry {

    // Logger must be initialized lazily, otherwise it's possible SharedTaggedMetricRegistries.getSingleton
    // can cause logger initialization, and singleton registry accessors in the logging framework can fail.
    private static final Supplier<Logger> log =
            Suppliers.memoize(() -> LoggerFactory.getLogger(AbstractTaggedMetricRegistry.class));
    private final Map<MetricName, Metric> registry = new ConcurrentHashMap<>();
    private final Map<Map.Entry<String, String>, TaggedMetricSet> taggedRegistries = new ConcurrentHashMap<>();
    private final Supplier<Reservoir> reservoirSupplier;

    @SuppressWarnings("PublicConstructorForAbstractClass") // public API (e.g. used by Dialogue TaggedMetrics)
    public AbstractTaggedMetricRegistry(Supplier<Reservoir> reservoirSupplier) {
        this.reservoirSupplier = checkNotNull(reservoirSupplier, "reservoirSupplier");
    }

    /**
     * Supplies counter instances for this registry.
     *
     * @return counter supplier
     */
    @Nonnull
    @SuppressWarnings("NoFunctionalReturnType") // metric factory
    protected Supplier<Counter> counterSupplier() {
        return Counter::new;
    }

    /**
     * Supplies histogram instances for this registry.
     *
     * @return histogram supplier
     */
    @Nonnull
    @SuppressWarnings("NoFunctionalReturnType") // metric factory
    protected Supplier<Histogram> histogramSupplier() {
        return () -> new HistogramWithSamples(createReservoir(), new LockFreeSampler(Tracer::getTraceIfObservable));
    }

    /**
     * Supplies meter instances for this registry.
     *
     * @return meter supplier
     */
    @Nonnull
    @SuppressWarnings("NoFunctionalReturnType") // metric factory
    protected Supplier<Meter> meterSupplier() {
        return Meter::new;
    }

    /**
     * Supplies timer instances for this registry.
     *
     * @return timer supplier
     */
    @Nonnull
    @SuppressWarnings("NoFunctionalReturnType") // metric factory
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
    public final <T> Optional<Gauge<T>> gauge(MetricName metricName) {
        return Optional.ofNullable(checkMetricType(metricName, Gauge.class, registry.get(metricName)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> Gauge<T> gauge(MetricName metricName, Gauge<T> gauge) {
        return getOrAdd(metricName, Gauge.class, () -> gauge);
    }

    @Override
    public final void registerWithReplacement(MetricName metricName, Gauge<?> gauge) {
        Metric existing = registry.put(metricName, gauge);
        if (existing instanceof Gauge) {
            log.get().debug("Removed previously registered gauge", SafeArg.of("metricName", metricName));
        } else if (existing != null) {
            // Existing should be a gauge
            registry.replace(metricName, existing);
            throw invalidMetric(metricName, gauge.getClass(), existing);
        }
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
    @SuppressWarnings("MutableMethodReturnType") // API method
    public final Map<MetricName, Metric> getMetrics() {
        ImmutableMap.Builder<MetricName, Metric> result = ImmutableMap.builder();
        result.putAll(registry);
        taggedRegistries.forEach((tag, metrics) -> metrics.getMetrics()
                .forEach((metricName, metric) ->
                        result.put(RealMetricName.create(metricName, tag.getKey(), tag.getValue()), metric)));

        return result.buildKeepingLast();
    }

    @Override
    public final void forEachMetric(BiConsumer<MetricName, Metric> consumer) {
        registry.forEach(consumer);
        taggedRegistries.forEach((tag, metrics) -> metrics.forEachMetric((metricName, metric) ->
                consumer.accept(RealMetricName.create(metricName, tag.getKey(), tag.getValue()), metric)));
    }

    @Override
    public final Optional<Metric> remove(MetricName metricName) {
        return Optional.ofNullable(registry.remove(metricName));
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
    public final boolean removeMetrics(String safeTagName, String safeTagValue, TaggedMetricSet metrics) {
        return taggedRegistries.remove(Maps.immutableEntry(safeTagName, safeTagValue), metrics);
    }

    protected final <T extends Metric> T getOrAdd(
            MetricName metricName, Class<T> metricClass, Supplier<T> metricSupplier) {
        Metric metric = registry.computeIfAbsent(metricName, _name -> metricSupplier.get());
        return checkNotNull(checkMetricType(metricName, metricClass, metric), "metric");
    }

    @Nullable
    static <T extends Metric> T checkMetricType(MetricName metricName, Class<T> metricClass, @Nullable Metric metric) {
        if (metric == null || metricClass.isInstance(metric)) {
            return metricClass.cast(metric);
        }
        throw invalidMetric(metricName, metricClass, metric);
    }

    private static <T extends Metric> SafeIllegalArgumentException invalidMetric(
            MetricName metricName, Class<T> metricClass, Metric metric) {
        return new SafeIllegalArgumentException(
                "Metric name already used for different metric type",
                SafeArg.of("metricName", metricName.safeName()),
                SafeArg.of("existingMetricType", metric.getClass().getSimpleName()),
                SafeArg.of("newMetricType", metricClass.getSimpleName()),
                SafeArg.of("safeTags", metricName.safeTags()));
    }
}
