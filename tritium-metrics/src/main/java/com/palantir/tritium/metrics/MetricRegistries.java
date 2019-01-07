/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.metrics;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reservoir;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableSet;
import com.palantir.logsafe.SafeArg;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for working with {@link MetricRegistry} instances.
 */
public final class MetricRegistries {

    private static final Logger logger = LoggerFactory.getLogger(MetricRegistries.class);

    static final String RESERVOIR_TYPE_METRIC_NAME = MetricRegistry.name(MetricRegistries.class, "reservoir.type");

    private MetricRegistries() {
        throw new UnsupportedOperationException();
    }

    /**
     * Create metric registry which produces timers and histograms backed by
     * high dynamic range histograms and timers, that accumulates internal state forever.
     *
     * @return metric registry
     */
    public static MetricRegistry createWithHdrHistogramReservoirs() {
        // Use HDR Histogram reservoir histograms and timers, instead of default exponentially decaying reservoirs,
        // see http://taint.org/2014/01/16/145944a.html
        return createWithReservoirType(Reservoirs.hdrHistogramReservoirSupplier());
    }

    @VisibleForTesting
    static MetricRegistry createWithReservoirType(Supplier<Reservoir> reservoirSupplier) {
        MetricRegistry metrics = new MetricRegistryWithReservoirs(reservoirSupplier);
        String name = reservoirSupplier.get().getClass().getCanonicalName();
        registerSafe(metrics, RESERVOIR_TYPE_METRIC_NAME, (Gauge<String>) () -> name);
        registerDefaultMetrics(metrics);
        return metrics;
    }

    private static MetricRegistry registerDefaultMetrics(MetricRegistry metrics) {
        registerSafe(metrics, MetricRegistry.name(MetricRegistries.class.getPackage().getName(), "snapshot", "begin"),
                new Gauge<String>() {
                    private final String start = nowIsoTimestamp();
                    @Override
                    public String getValue() {
                        return start;
                    }
                });
        registerSafe(metrics, MetricRegistry.name(MetricRegistries.class.getPackage().getName(), "snapshot", "now"),
                (Gauge<String>) MetricRegistries::nowIsoTimestamp);
        return metrics;
    }

    @VisibleForTesting
    static String nowIsoTimestamp() {
        return DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now());
    }

    @SuppressWarnings("unchecked")
    static <T extends Metric> T getOrAdd(MetricRegistry metrics, String name, MetricBuilder<T> builder) {
        checkNotNull(metrics);
        checkNotNull(name);
        checkNotNull(builder);

        Metric metric = metrics.getMetrics().get(name);
        if (metric == null) {
            try {
                return metrics.register(name, builder.newMetric());
            } catch (IllegalArgumentException e) {
                Metric added = metrics.getMetrics().get(name);
                if (added != null && builder.isInstance(added)) {
                    return (T) added;
                }
            }
        } else if (builder.isInstance(metric)) {
            return (T) metric;
        }
        throw new IllegalArgumentException(name + " is already used for a different type of metric for " + metric);
    }

    /**
     * Creates a {@link MetricFilter} predicate to match metrics with names starting with the specified prefix.
     *
     * @param prefix metric name prefix
     * @return metric filter
     */
    public static MetricFilter metricsPrefixedBy(String prefix) {
        checkNotNull(prefix, "prefix");
        return (name, metric) -> name.startsWith(prefix);
    }

    /**
     * Returns a sorted map of metrics from the specified registry matching the specified filter.
     *
     * @param metrics metric registry
     * @param filter metric filter predicate
     * @return sorted map of metrics
     */
    public static SortedMap<String, Metric> metricsMatching(MetricRegistry metrics, MetricFilter filter) {
        SortedMap<String, Metric> matchingMetrics = new TreeMap<>();
        metrics.getMetrics().forEach((key, value) -> {
            if (filter.matches(key, value)) {
                matchingMetrics.put(key, value);
            }
        });
        return matchingMetrics;
    }

    /**
     * Register specified cache with the given metric registry.
     *
     * @param registry metric registry
     * @param cache cache to instrument
     * @param name cache name
     *
     * @throws IllegalArgumentException if name is blank
     */
    @SuppressWarnings("BanGuavaCaches") // this implementation is explicitly for Guava caches
    public static <C extends Cache<?, ?>> void registerCache(MetricRegistry registry, Cache<?, ?> cache, String name) {
        registerCache(registry, cache, name, Clock.defaultClock());
    }

    @VisibleForTesting
    @SuppressWarnings("BanGuavaCaches") // this implementation is explicitly for Guava caches
    static void registerCache(MetricRegistry registry, Cache<?, ?> cache, String name, Clock clock) {
        checkNotNull(registry, "metric registry");
        checkNotNull(cache, "cache");
        checkNotNull(name, "name");
        checkNotNull(clock, "clock");
        checkArgument(!name.trim().isEmpty(), "Cache name cannot be blank or empty");
        CacheMetricSet.create(cache, name, clock)
                .getMetrics()
                .forEach((key, value) -> registerWithReplacement(registry, key, value));
    }

    /**
     * Ensures a {@link Metric} is registered to a {@link MetricRegistry} with the supplied {@code name}. If there is an
     * existing {@link Metric} registered to {@code name} with the same implemented set of interfaces as {@code metric}
     * then it's returned. Otherwise {@code metric} is registered and returned.
     * <p>
     * This is intended to imitate the semantics of {@link MetricRegistry#counter(String)} and should only be used for
     * {@link Metric} implementations that can't be registered/created in that manner (because it does not actually
     * guarantee that the registered {@link Metric} matches the input {@code metric}).
     * <p>
     * For example, this may be useful for registering {@link Gauge}s which might cause issues from being added multiple
     * times to a static {@link MetricRegistry} in a unit test
     *
     * @throws IllegalArgumentException if there is already a {@link Metric} registered that doesn't implement the same
     *         interfaces as {@code metric}
     */
    public static <T extends Metric> T registerSafe(MetricRegistry registry, String name, T metric) {
        return registerOrReplace(registry, name, metric, /* replace= */false);
    }

    public static <T extends Metric> T registerWithReplacement(MetricRegistry registry, String name, T metric) {
        return registerOrReplace(registry, name, metric, /* replace= */true);
    }

    private static <T extends Metric> T registerOrReplace(MetricRegistry registry, String name, T metric,
            boolean replace) {

        synchronized (registry) {
            Map<String, Metric> metrics = registry.getMetrics();
            Metric existingMetric = metrics.get(name);
            if (existingMetric == null) {
                return registry.register(name, metric);
            } else {
                Set<Class<?>> existingMetricInterfaces = ImmutableSet.copyOf(existingMetric.getClass().getInterfaces());
                Set<Class<?>> newMetricInterfaces = ImmutableSet.copyOf(metric.getClass().getInterfaces());
                if (!existingMetricInterfaces.equals(newMetricInterfaces)) {
                    throw new IllegalArgumentException(
                            "Metric already registered at this name that implements a different set of interfaces."
                                    + " Name: " + name + ", existing metric: " + existingMetric);
                }

                if (replace && registry.remove(name)) {
                    logger.info("Removed existing registered metric with name {}: {}",
                            SafeArg.of("name", name),
                            SafeArg.of("existingMetric", existingMetric));
                    registry.register(name, metric);
                    return metric;
                } else {
                    logger.warn("Metric already registered at this name. Name: {}, existing metric: {}",
                            SafeArg.of("name", name),
                            SafeArg.of("existingMetric", existingMetric));
                    @SuppressWarnings("unchecked")
                    T registeredMetric = (T) existingMetric;
                    return registeredMetric;
                }
            }
        }
    }
}
