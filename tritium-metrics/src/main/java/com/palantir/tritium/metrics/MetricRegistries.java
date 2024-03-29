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

import static com.palantir.logsafe.Preconditions.checkArgument;
import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.LockFreeExponentiallyDecayingReservoir;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocketFactory;

/** Utilities for working with {@link MetricRegistry} instances. */
public final class MetricRegistries {

    private static final SafeLogger log = SafeLoggerFactory.get(MetricRegistries.class);

    static final String RESERVOIR_TYPE_METRIC_NAME = MetricRegistry.name(MetricRegistries.class, "reservoir.type");

    private MetricRegistries() {}

    /**
     * Create metric registry which produces timers and histograms backed by high dynamic range histograms and timers,
     * that accumulates internal state forever.
     *
     * @return metric registry
     */
    public static MetricRegistry createWithHdrHistogramReservoirs() {
        // Use HDR Histogram reservoir histograms and timers, instead of default exponentially decaying reservoirs,
        // see http://taint.org/2014/01/16/145944a.html
        return createWithReservoirType(Reservoirs::hdrHistogramReservoir);
    }

    /**
     * Creates a {@link MetricRegistry} which produces timers and histograms backed by sliding time window array that
     * store measurements for the specified sliding time window.
     *
     * <p>See also:
     *
     * <ul>
     *   <li><a href="http://taint.org/2014/01/16/145944a.html">Discussion why this reservoir may make more sense than
     *       the HdrHistogram </a> <a
     *       href="https://metrics.dropwizard.io/4.0.0/manual/core.html#sliding-time-window-reservoirs">Improvements
     *       over the old dropwizard metrics SlidingTimeWindowReservoir implementation </a>
     * </ul>
     *
     * @param window window of time
     * @param windowUnit unit for window
     * @return metric registry
     */
    public static MetricRegistry createWithSlidingTimeWindowReservoirs(long window, TimeUnit windowUnit) {
        return createWithReservoirType(() -> Reservoirs.slidingTimeWindowArrayReservoir(window, windowUnit));
    }

    /**
     * Create a {@link MetricRegistry metric registry} which produces timers and histograms backed by
     * {@link LockFreeExponentiallyDecayingReservoir lock free expoentially decaying reservoirs}.
     *
     * @see LockFreeExponentiallyDecayingReservoir
     * @return metric registry
     */
    public static MetricRegistry createWithLockFreeExponentiallyDecayingReservoirs() {
        return createWithReservoirType(
                () -> LockFreeExponentiallyDecayingReservoir.builder().build());
    }

    @VisibleForTesting
    static MetricRegistry createWithReservoirType(Supplier<Reservoir> reservoirSupplier) {
        MetricRegistry metrics = new MetricRegistryWithReservoirs(reservoirSupplier);
        String name = reservoirSupplier.get().getClass().getCanonicalName();
        registerSafe(metrics, RESERVOIR_TYPE_METRIC_NAME, (Gauge<String>) () -> name);
        registerDefaultMetrics(metrics);
        return metrics;
    }

    private static void registerDefaultMetrics(MetricRegistry metrics) {
        registerSafe(
                metrics,
                MetricRegistry.name(MetricRegistries.class.getPackage().getName(), "snapshot", "begin"),
                new Gauge<String>() {
                    private final String start = nowIsoTimestamp();

                    @Override
                    public String getValue() {
                        return start;
                    }
                });
        registerSafe(
                metrics,
                MetricRegistry.name(MetricRegistries.class.getPackage().getName(), "snapshot", "now"),
                (Gauge<String>) MetricRegistries::nowIsoTimestamp);
    }

    @VisibleForTesting
    static String nowIsoTimestamp() {
        return DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
    }

    static <T extends Metric> T getOrAdd(MetricRegistry metrics, @Safe String name, MetricBuilder<T> builder) {
        Metric existingMetric = tryGetExistingMetric(metrics, name);
        if (existingMetric == null) {
            return addMetric(metrics, name, builder);
        }
        return getAndCheckExistingMetric(name, builder, existingMetric);
    }

    @Nullable
    private static Metric tryGetExistingMetric(MetricRegistry metrics, @Safe String name) {
        return checkNotNull(metrics, "metrics").getMetrics().get(checkNotNull(name));
    }

    private static <T extends Metric> T addMetric(MetricRegistry metrics, @Safe String name, MetricBuilder<T> builder) {
        checkNotNull(builder);
        T newMetric = builder.newMetric();
        try {
            return metrics.register(name, newMetric);
        } catch (IllegalArgumentException e) {
            // fall back to existing metric
            Metric existingMetric = metrics.getMetrics().get(name);
            return getAndCheckExistingMetric(name, builder, existingMetric);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Metric> T getAndCheckExistingMetric(
            @Safe String name, MetricBuilder<T> builder, @Nullable Metric existingMetric) {
        if (existingMetric != null && builder.isInstance(existingMetric)) {
            return (T) existingMetric;
        }
        throw invalidMetric(name, existingMetric, builder.newMetric());
    }

    private static SafeIllegalArgumentException invalidMetric(
            @Safe String name, @Nullable Metric existingMetric, Metric newMetric) {
        throw new SafeIllegalArgumentException(
                "Metric name already used for different metric type",
                SafeArg.of("metricName", name),
                SafeArg.of("existingMetricType", safeClassName(existingMetric)),
                SafeArg.of("newMetricType", safeClassName(newMetric)));
    }

    @Safe
    private static String safeClassName(@Nullable Object obj) {
        return (obj == null) ? "" : obj.getClass().getName();
    }

    /**
     * Creates a {@link MetricFilter} predicate to match metrics with names starting with the specified prefix.
     *
     * @param prefix metric name prefix
     * @return metric filter
     */
    public static MetricFilter metricsPrefixedBy(@Safe String prefix) {
        checkNotNull(prefix, "prefix");
        return (name, _metric) -> name.startsWith(prefix);
    }

    /**
     * Returns a sorted map of metrics from the specified registry matching the specified filter.
     *
     * @param metrics metric registry
     * @param filter metric filter predicate
     * @return sorted map of metrics
     */
    @SuppressWarnings("JdkObsolete") // SortedMap is part of Metrics API
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
     * Callers should ensure that they have {@link CacheBuilder#recordStats() enabled stats recording}
     * {@code CacheBuilder.newBuilder().recordStats()} otherwise there are no cache metrics to register.
     *
     * @param registry metric registry
     * @param cache cache to instrument
     * @param name cache name
     * @throws IllegalArgumentException if name is blank
     *
     * @deprecated use {@link #registerCache(TaggedMetricRegistry, Cache, String)}
     */
    @Deprecated
    @SuppressWarnings("BanGuavaCaches") // this implementation is explicitly for Guava caches
    public static void registerCache(MetricRegistry registry, Cache<?, ?> cache, @Safe String name) {
        registerCache(registry, cache, name, Clock.defaultClock());
    }

    @VisibleForTesting
    @SuppressWarnings("BanGuavaCaches") // this implementation is explicitly for Guava caches
    static void registerCache(MetricRegistry registry, Cache<?, ?> cache, @Safe String name, Clock clock) {
        checkNotNull(registry, "metric registry");
        checkNotNull(cache, "cache");
        checkNotNull(name, "name");
        checkNotNull(clock, "clock");
        checkArgument(!name.trim().isEmpty(), "Cache name cannot be blank or empty");

        CacheMetricSet.create(cache, name)
                .getMetrics()
                .forEach((key, value) -> registerWithReplacement(registry, key, value));
    }

    /**
     * Register specified cache with the given metric registry.
     *
     * Callers should ensure that they have {@link CacheBuilder#recordStats() enabled stats recording}
     * {@code CacheBuilder.newBuilder().recordStats()} otherwise there are no cache metrics to register.
     *
     * @param registry metric registry
     * @param cache cache to instrument
     * @param name cache name
     * @throws IllegalArgumentException if name is blank
     * @deprecated Do not use Guava caches, they are outperformed by and harder to use than Caffeine caches.
     * Prefer {@link Caffeine#recordStats(Supplier)} and {@link CacheStats#of(TaggedMetricRegistry, String)}.
     */
    @Deprecated // BanGuavaCaches
    @SuppressWarnings("BanGuavaCaches") // this implementation is explicitly for Guava caches
    public static void registerCache(TaggedMetricRegistry registry, Cache<?, ?> cache, @Safe String name) {
        checkNotNull(registry, "metric registry");
        checkNotNull(cache, "cache");
        checkNotNull(name, "name");
        checkArgument(!name.trim().isEmpty(), "Cache name cannot be blank or empty");

        CacheTaggedMetrics.create(cache, name).getMetrics().forEach(registry::registerWithReplacement);
    }

    /**
     * Adds Garbage Collection metrics to the given metric registry.
     *
     * <p>Registers gauges
     *
     * <pre>jvm.gc.count</pre>
     *
     * and
     *
     * <pre>jvm.gc.time</pre>
     *
     * tagged with
     *
     * <pre>{collector: NAME}</pre>
     *
     * .
     *
     * @param registry metric registry
     */
    public static void registerGarbageCollection(TaggedMetricRegistry registry) {
        GarbageCollectorMetrics.register(checkNotNull(registry, "TaggedMetricRegistry is required"));
    }

    /**
     * Adds memory pool metrics to the given metric registry.
     *
     * <p>Registers the following metrics, tagged with
     *
     * <pre>{memoryPool: NAME}</pre>
     *
     * .
     *
     * <ul>
     *   <li>jvm.memory.pools.max
     *   <li>jvm.memory.pools.used
     *   <li>jvm.memory.pools.committed
     *   <li>jvm.memory.pools.init
     *   <li>jvm.memory.pools.usage
     *   <li>jvm.memory.pools.used-after-gc (Only for supported pools)
     * </ul>
     *
     * @param registry metric registry
     */
    public static void registerMemoryPools(TaggedMetricRegistry registry) {
        MemoryPoolMetrics.register(checkNotNull(registry, "TaggedMetricRegistry is required"));
    }

    /**
     * Returns an instrumented {@link ScheduledExecutorService} that monitors the number of tasks submitted, running,
     * completed and also keeps a {@link com.codahale.metrics.Timer} for the task duration. Similar to
     * {@link com.codahale.metrics.InstrumentedScheduledExecutorService}, but produces tagged metrics to the specified
     * {@link TaggedMetricRegistry}.
     *
     * @param registry tagged metric registry
     * @param delegate executor service to instrument
     * @param name executor service name
     * @return instrumented executor service
     */
    public static ScheduledExecutorService instrument(
            TaggedMetricRegistry registry, ScheduledExecutorService delegate, @Safe String name) {
        return new TaggedMetricsScheduledExecutorService(
                checkNotNull(delegate, "delegate"), ExecutorMetrics.of(registry), checkNotNull(name, "name"));
    }

    /**
     * Returns an instrumented {@link ExecutorService} that monitors the number of tasks submitted, running, completed
     * and also keeps a {@link com.codahale.metrics.Timer} for the task duration. Similar to
     * {@link com.codahale.metrics.InstrumentedExecutorService}, but produces tagged metrics to the specified
     * {@link TaggedMetricRegistry}.
     *
     * @param registry tagged metric registry
     * @param delegate executor service to instrument
     * @param name executor service name
     * @return instrumented executor service
     */
    public static ExecutorService instrument(
            TaggedMetricRegistry registry, ExecutorService delegate, @Safe String name) {
        return executor().registry(registry).name(name).executor(delegate).build();
    }

    /**
     * Returns an instrumented {@link ThreadFactory} that monitors the number of created, running, and terminated
     * threads.
     *
     * @param registry tagged metric registry
     * @param delegate Thread factory to instrument
     * @param name Thread factory name
     * @return instrumented thread factory
     */
    public static ThreadFactory instrument(TaggedMetricRegistry registry, ThreadFactory delegate, @Safe String name) {
        return new TaggedMetricsThreadFactory(
                checkNotNull(delegate, "ThreadFactory is required"),
                ExecutorMetrics.of(registry),
                checkNotNull(name, "Name is required"));
    }

    /**
     * Returns an instrumented {@link SSLContext} that monitors handshakes and ciphers. A name may be reused across many
     * contexts.
     *
     * @param registry tagged metric registry
     * @param context ssl context to instrument
     * @param name context name
     * @return instrumented ssl context
     */
    public static SSLContext instrument(TaggedMetricRegistry registry, SSLContext context, @Safe String name) {
        return new InstrumentedSslContext(
                checkNotNull(context, "context"), TlsMetrics.of(registry), checkNotNull(name, "name"));
    }

    /**
     * Returns an instrumented {@link SSLSocketFactory} that monitors handshakes and ciphers. A name may be reused
     * across many factories.
     *
     * @param registry tagged metric registry
     * @param factory socket factory to instrument
     * @param name socket factory name
     * @return instrumented socket factory
     */
    public static SSLSocketFactory instrument(
            TaggedMetricRegistry registry, SSLSocketFactory factory, @Safe String name) {
        return new InstrumentedSslSocketFactory(
                checkNotNull(factory, "factory"), TlsMetrics.of(registry), checkNotNull(name, "name"));
    }

    /**
     * Extracts the wrapped delegate if the input {@link SSLEngine} is instrumented, otherwise returns the input. Some
     * libraries (Conscrypt, for example) use <code>instanceof</code> checks and casts to configure specific
     * {@link SSLEngine} implementations. In such cases, it may be necessary to unwrap the instrumented instance.
     *
     * @param engine Engine to unwrap
     * @return The delegate instrumented engine, or the input if it is not instrumented
     */
    public static SSLEngine unwrap(SSLEngine engine) {
        return InstrumentedSslEngine.extractDelegate(checkNotNull(engine, "engine"));
    }

    /**
     * Ensures a {@link Metric} is registered to a {@link MetricRegistry} with the supplied {@code name}. If there is an
     * existing {@link Metric} registered to {@code name} with the same implemented set of interfaces as {@code metric}
     * then it's returned. Otherwise {@code metric} is registered and returned.
     *
     * <p>This is intended to imitate the semantics of {@link MetricRegistry#counter(String)} and should only be used
     * for {@link Metric} implementations that can't be registered/created in that manner (because it does not actually
     * guarantee that the registered {@link Metric} matches the input {@code metric}).
     *
     * <p>For example, this may be useful for registering {@link Gauge}s which might cause issues from being added
     * multiple times to a static {@link MetricRegistry} in a unit test
     *
     * @throws IllegalArgumentException if there is already a {@link Metric} registered that doesn't implement the same
     *     interfaces as {@code metric}
     */
    public static <T extends Metric> T registerSafe(MetricRegistry registry, @Safe String name, T metric) {
        return registerOrReplace(registry, name, metric, /* replace= */ false);
    }

    public static <T extends Metric> T registerWithReplacement(MetricRegistry registry, @Safe String name, T metric) {
        return registerOrReplace(registry, name, metric, /* replace= */ true);
    }

    private static <T extends Metric> T registerOrReplace(
            MetricRegistry registry, @Safe String name, T metric, boolean replace) {
        synchronized (registry) {
            Map<String, Metric> metrics = registry.getMetrics();
            Metric existingMetric = metrics.get(name);
            if (existingMetric == null) {
                return registry.register(name, metric);
            } else {
                Set<Class<?>> existingMetricInterfaces = Collections.newSetFromMap(new IdentityHashMap<>());
                existingMetricInterfaces.addAll(
                        Arrays.asList(existingMetric.getClass().getInterfaces()));
                Set<Class<?>> newMetricInterfaces = Collections.newSetFromMap(new IdentityHashMap<>());
                newMetricInterfaces.addAll(Arrays.asList(metric.getClass().getInterfaces()));
                if (!existingMetricInterfaces.equals(newMetricInterfaces)) {
                    throw new SafeIllegalArgumentException(
                            "Metric already registered at this name that implements a different set of interfaces",
                            SafeArg.of("name", name),
                            SafeArg.of("existingMetric", String.valueOf(existingMetric)));
                }

                if (replace && registry.remove(name)) {
                    log.info(
                            "Removed existing registered metric with name {}: {}",
                            SafeArg.of("name", name),
                            // #256: Metric implementations are necessarily json serializable
                            SafeArg.of("existingMetric", String.valueOf(existingMetric)));
                    registry.register(name, metric);
                    return metric;
                } else {
                    log.warn(
                            "Metric already registered at this name. Name: {}, existing metric: {}",
                            SafeArg.of("name", name),
                            // #256: Metric implementations are necessarily json serializable
                            SafeArg.of("existingMetric", String.valueOf(existingMetric)));
                    @SuppressWarnings("unchecked")
                    T registeredMetric = (T) existingMetric;
                    return registeredMetric;
                }
            }
        }
    }

    /**
     * Registers a Dropwizard {@link MetricSet} with a Tritium {@link TaggedMetricRegistry}. Semantics match calling
     * {@link MetricRegistry#register(String, Metric)} with a {@link MetricSet}.
     *
     * @param registry Target Tritium registry
     * @param prefix Metric name prefix
     * @param metricSet Set to register with the tagged registry
     */
    public static void registerAll(TaggedMetricRegistry registry, @Safe String prefix, MetricSet metricSet) {
        Preconditions.checkNotNull(registry, "TaggedMetricRegistry is required");
        Preconditions.checkNotNull(prefix, "Prefix is required");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(prefix), "Prefix cannot be blank");
        Preconditions.checkNotNull(metricSet, "MetricSet is required");
        metricSet.getMetrics().forEach((name, metric) -> {
            String safeName = MetricRegistry.name(prefix, name);
            MetricName metricName = MetricName.builder().safeName(safeName).build();
            if (metric instanceof Gauge) {
                registry.registerWithReplacement(metricName, (Gauge<?>) metric);
            } else if (metric instanceof Counter) {
                registry.counter(metricName, () -> (Counter) metric);
            } else if (metric instanceof Histogram) {
                registry.histogram(metricName, () -> (Histogram) metric);
            } else if (metric instanceof Meter) {
                registry.meter(metricName, () -> (Meter) metric);
            } else if (metric instanceof Timer) {
                registry.timer(metricName, () -> (Timer) metric);
            } else if (metric instanceof MetricSet) {
                registerAll(registry, safeName, (MetricSet) metric);
            } else {
                throw new SafeIllegalArgumentException("Unknown Metric Type", SafeArg.of("type", metric.getClass()));
            }
        });
    }

    /** Returns a builder for {@link ExecutorService} instrumentation. */
    @CheckReturnValue
    public static ExecutorInstrumentationBuilderRegistryStage executor() {
        return new ExecutorInstrumentationBuilder();
    }

    private static final class ExecutorInstrumentationBuilder
            implements ExecutorInstrumentationBuilderRegistryStage,
                    ExecutorInstrumentationBuilderNameStage,
                    ExecutorInstrumentationBuilderExecutorStage,
                    ExecutorInstrumentationBuilderFinalStage {

        @Nullable
        private TaggedMetricRegistry registry;

        @Safe
        @Nullable
        private String name;

        @Nullable
        private ExecutorService executor;

        private boolean reportQueuedDuration = true;

        @Override
        @CheckReturnValue
        public ExecutorInstrumentationBuilderNameStage registry(TaggedMetricRegistry value) {
            this.registry = Preconditions.checkNotNull(value, "TaggedMetricRegistry");
            return this;
        }

        @Override
        @CheckReturnValue
        public ExecutorInstrumentationBuilderExecutorStage name(@Safe String value) {
            this.name = Preconditions.checkNotNull(value, "Name");
            return this;
        }

        @Override
        @CheckReturnValue
        public ExecutorInstrumentationBuilderFinalStage executor(ExecutorService value) {
            this.executor = Preconditions.checkNotNull(value, "ExecutorService");
            return this;
        }

        @Override
        @CheckReturnValue
        public ExecutorInstrumentationBuilderFinalStage reportQueuedDuration(boolean value) {
            this.reportQueuedDuration = value;
            return this;
        }

        @Override
        @CheckReturnValue
        public ExecutorService build() {
            if (executor instanceof ScheduledExecutorService) {
                return instrument(
                        checkNotNull(registry, "delegate"),
                        (ScheduledExecutorService) executor,
                        checkNotNull(name, "Name"));
            }
            return new TaggedMetricsExecutorService(
                    checkNotNull(executor, "delegate"),
                    ExecutorMetrics.of(checkNotNull(registry, "registry")),
                    checkNotNull(name, "name"),
                    reportQueuedDuration);
        }
    }

    public interface ExecutorInstrumentationBuilderRegistryStage {
        @CheckReturnValue
        ExecutorInstrumentationBuilderNameStage registry(TaggedMetricRegistry value);
    }

    public interface ExecutorInstrumentationBuilderNameStage {
        @CheckReturnValue
        ExecutorInstrumentationBuilderExecutorStage name(@Safe String value);
    }

    public interface ExecutorInstrumentationBuilderExecutorStage {
        @CheckReturnValue
        ExecutorInstrumentationBuilderFinalStage executor(ExecutorService value);
    }

    public interface ExecutorInstrumentationBuilderFinalStage {

        /**
         * May be used to inform instrumentation that the delegate executor does not
         * have a queue and the queue time metric is unnecessary to track. This is
         * the case for cached executors, and executors which immediately reject
         * work when all threads are saturated, otherwise the timer is always updated
         * with very small values that aren't helpful.
         */
        @CheckReturnValue
        ExecutorInstrumentationBuilderFinalStage reportQueuedDuration(boolean value);

        /**
         * Builds the instrumented {@link ExecutorService}.
         * @return instrumented executor service
         */
        @CheckReturnValue
        ExecutorService build();
    }
}
