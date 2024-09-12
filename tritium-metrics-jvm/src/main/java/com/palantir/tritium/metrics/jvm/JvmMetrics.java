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

package com.palantir.tritium.metrics.jvm;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.RatioGauge;
import com.codahale.metrics.jvm.ThreadDeadlockDetector;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.palantir.jvm.diagnostics.CpuSharesAccessor;
import com.palantir.jvm.diagnostics.JvmDiagnostics;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.jvm.InternalJvmMetrics.AttributeUptime_EnablePreview;
import com.palantir.tritium.metrics.jvm.InternalJvmMetrics.DnsCacheTtlSeconds_Cache;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** {@link JvmMetrics} provides a standard set of metrics for debugging java services. */
public final class JvmMetrics {
    private static final SafeLogger log = SafeLoggerFactory.get(JvmMetrics.class);
    private static final RatioGauge.Ratio RATIO_NAN = RatioGauge.Ratio.of(Double.NaN, Double.NaN);

    /**
     * Registers a default set of metrics.
     *
     * <p>This includes {@link MetricRegistries#registerGarbageCollection(TaggedMetricRegistry)} and
     * {@link MetricRegistries#registerMemoryPools(TaggedMetricRegistry)}.
     *
     * @param registry metric registry
     */
    public static void register(TaggedMetricRegistry registry) {
        Preconditions.checkNotNull(registry, "TaggedMetricRegistry is required");
        MetricRegistries.registerGarbageCollection(registry);
        MetricRegistries.registerMemoryPools(registry);
        InternalJvmMetrics metrics = InternalJvmMetrics.of(registry);
        Jdk9CompatibleFileDescriptorRatioGauge.register(metrics);
        OperatingSystemMetrics.register(registry);
        SafepointMetrics.register(registry);
        registerAttributes(metrics);
        registerJvmBufferPools(registry);
        registerClassLoading(metrics);
        registerJvmMemory(registry);
        registerThreads(metrics);
        metrics.processors(Runtime.getRuntime()::availableProcessors);
        registerCpuShares(registry, JvmDiagnostics.cpuShares());
        registerDnsCacheMetrics(metrics);
    }

    private static void registerAttributes(InternalJvmMetrics metrics) {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        metrics.attributeUptime()
                .javaSpecificationVersion(System.getProperty("java.specification.version", "unknown"))
                .javaVersion(System.getProperty("java.version", "unknown"))
                .javaVersionDate(System.getProperty("java.version.date", "unknown"))
                .javaRuntimeVersion(System.getProperty("java.runtime.version", "unknown"))
                .javaVendorVersion(System.getProperty("java.vendor.version", "unknown"))
                .javaVmVendor(System.getProperty("java.vm.vendor", "unknown"))
                .enablePreview(
                        runtimeMxBean.getInputArguments().contains("--enable-preview")
                                ? AttributeUptime_EnablePreview.TRUE
                                : AttributeUptime_EnablePreview.FALSE)
                .build(runtimeMxBean::getUptime);
    }

    private static void registerClassLoading(InternalJvmMetrics metrics) {
        ClassLoadingMXBean classLoadingBean = ManagementFactory.getClassLoadingMXBean();
        metrics.classloaderLoaded(classLoadingBean::getTotalLoadedClassCount);
        metrics.classloaderLoadedCurrent(classLoadingBean::getLoadedClassCount);
        metrics.classloaderUnloaded(classLoadingBean::getUnloadedClassCount);
    }

    private static void registerThreads(InternalJvmMetrics metrics) {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        metrics.threadsCount(threads::getThreadCount);
        metrics.threadsDaemonCount(threads::getDaemonThreadCount);
        ThreadDeadlockDetector deadlockDetector = new ThreadDeadlockDetector(threads);
        metrics.threadsDeadlockCount(
                () -> deadlockDetector.getDeadlockedThreads().size());
        Supplier<Map<Thread.State, Integer>> threadsByStateSupplier =
                Suppliers.memoizeWithExpiration(() -> threadsByState(threads), 10, TimeUnit.SECONDS);
        metrics.threadsNewCount(() -> threadsByStateSupplier.get().getOrDefault(Thread.State.NEW, 0));
        metrics.threadsRunnableCount(() -> threadsByStateSupplier.get().getOrDefault(Thread.State.RUNNABLE, 0));
        metrics.threadsBlockedCount(() -> threadsByStateSupplier.get().getOrDefault(Thread.State.BLOCKED, 0));
        metrics.threadsWaitingCount(() -> threadsByStateSupplier.get().getOrDefault(Thread.State.WAITING, 0));
        metrics.threadsTimedWaitingCount(
                () -> threadsByStateSupplier.get().getOrDefault(Thread.State.TIMED_WAITING, 0));
        metrics.threadsTerminatedCount(() -> threadsByStateSupplier.get().getOrDefault(Thread.State.TERMINATED, 0));
    }

    @VisibleForTesting
    static void registerCpuShares(TaggedMetricRegistry registry, Optional<CpuSharesAccessor> maybeCpuSharesAccessor) {
        ContainerMetrics.of(registry)
                .cpuShares(maybeCpuSharesAccessor
                        .<Gauge<Long>>map(cpuSharesAccessor ->
                                () -> cpuSharesAccessor.getCpuShares().orElse(-1L))
                        .orElseGet(() -> {
                            log.info("CPU Shares information is not supported, cpu share metrics will not be reported");
                            return () -> -2L;
                        }));
    }

    @SuppressWarnings("UnnecessaryLambda") // Avoid allocations in the threads-by-state loop
    private static final BiFunction<Thread.State, Integer, Integer> incrementThreadState = (_state, input) -> {
        int existingValue = input == null ? 0 : input;
        return existingValue + 1;
    };

    private static Map<Thread.State, Integer> threadsByState(ThreadMXBean threads) {
        // max-depth zero to avoid creating stack traces, we're only interested in high level metadata
        ThreadInfo[] loadedThreadInfo = threads.getThreadInfo(threads.getAllThreadIds(), 0);
        Map<Thread.State, Integer> threadsByState = new EnumMap<>(Thread.State.class);
        for (ThreadInfo threadInfo : loadedThreadInfo) {
            // Threads may have been destroyed between ThreadMXBean.getAllThreadIds and ThreadMXBean.getThreadInfo
            if (threadInfo != null) {
                Thread.State threadState = threadInfo.getThreadState();
                if (threadState != null) {
                    threadsByState.compute(threadState, incrementThreadState);
                }
            }
        }
        return threadsByState;
    }

    private static void registerDnsCacheMetrics(InternalJvmMetrics metrics) {
        JvmDiagnostics.dnsCacheTtl().ifPresent(dnsCacheTtlAccessor -> {
            metrics.dnsCacheTtlSeconds()
                    .cache(DnsCacheTtlSeconds_Cache.POSITIVE)
                    .build(dnsCacheTtlAccessor::getPositiveSeconds);
            metrics.dnsCacheTtlSeconds()
                    .cache(DnsCacheTtlSeconds_Cache.NEGATIVE)
                    .build(dnsCacheTtlAccessor::getNegativeSeconds);
            metrics.dnsCacheTtlSeconds()
                    .cache(DnsCacheTtlSeconds_Cache.STALE)
                    .build(dnsCacheTtlAccessor::getStaleSeconds);
        });
    }

    private static void registerJvmMemory(TaggedMetricRegistry registry) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        if (memoryBean == null) {
            log.warn("Failed to load the MemoryMXBean, jvm.memory metrics will not be recorded");
            return;
        }
        registerJvmMemory(registry, memoryBean);
    }

    @VisibleForTesting
    static void registerJvmMemory(TaggedMetricRegistry registry, MemoryMXBean memoryBean) {
        JvmMemoryMetrics metrics = JvmMemoryMetrics.of(registry);
        // jvm.memory.total
        metrics.totalInit(nonNegative(() -> totalHeapPlusNonHeap(memoryBean, MemoryUsage::getInit)));
        metrics.totalUsed(nonNegative(() -> totalHeapPlusNonHeap(memoryBean, MemoryUsage::getUsed)));
        metrics.totalMax(nonNegative(() -> totalHeapPlusNonHeap(memoryBean, MemoryUsage::getMax)));
        metrics.totalCommitted(nonNegative(() -> totalHeapPlusNonHeap(memoryBean, MemoryUsage::getCommitted)));
        // jvm.memory.heap
        metrics.heapInit(nonNegative(() -> memoryBean.getHeapMemoryUsage().getInit()));
        metrics.heapUsed(nonNegative(() -> memoryBean.getHeapMemoryUsage().getUsed()));
        metrics.heapMax(nonNegative(() -> memoryBean.getHeapMemoryUsage().getMax()));
        metrics.heapCommitted(nonNegative(() -> memoryBean.getHeapMemoryUsage().getCommitted()));
        metrics.heapUsage(new RatioGauge() {
            @Override
            protected RatioGauge.Ratio getRatio() {
                MemoryUsage heapMemoryUsage = memoryBean.getHeapMemoryUsage();
                double used = heapMemoryUsage.getUsed();
                double max = heapMemoryUsage.getMax();
                return (used < 0 || max < 0) ? RATIO_NAN : RatioGauge.Ratio.of(used, max);
            }
        });
        // jvm.memory.non-heap
        metrics.nonHeapInit(nonNegative(() -> memoryBean.getNonHeapMemoryUsage().getInit()));
        metrics.nonHeapUsed(nonNegative(() -> memoryBean.getNonHeapMemoryUsage().getUsed()));
        metrics.nonHeapMax(nonNegative(() -> memoryBean.getNonHeapMemoryUsage().getMax()));
        metrics.nonHeapCommitted(
                nonNegative(() -> memoryBean.getNonHeapMemoryUsage().getCommitted()));
        metrics.nonHeapUsage(new RatioGauge() {
            @Override
            protected RatioGauge.Ratio getRatio() {
                MemoryUsage nonHeapMemoryUsage = memoryBean.getNonHeapMemoryUsage();
                double used = nonHeapMemoryUsage.getUsed();
                double max = nonHeapMemoryUsage.getMax();
                return (used < 0 || max < 0) ? RATIO_NAN : RatioGauge.Ratio.of(used, max);
            }
        });
    }

    private static void registerJvmBufferPools(TaggedMetricRegistry registry) {
        JvmBuffersMetrics jvmBuffersMetrics = JvmBuffersMetrics.of(registry);
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            String poolName = pool.getName();
            if ("direct".equals(poolName)) {
                jvmBuffersMetrics.directCount(nonNegative(pool::getCount));
                jvmBuffersMetrics.directUsed(nonNegative(pool::getMemoryUsed));
                jvmBuffersMetrics.directCapacity(nonNegative(pool::getTotalCapacity));
            } else if ("mapped".equals(poolName)) {
                jvmBuffersMetrics.mappedCount(nonNegative(pool::getCount));
                jvmBuffersMetrics.mappedUsed(nonNegative(pool::getMemoryUsed));
                jvmBuffersMetrics.mappedCapacity(nonNegative(pool::getTotalCapacity));
            }
        }
    }

    /**
     * Computes the total heap + non-heap result of applying the specified function.
     * If either value is negative, returns null to avoid misleading metric data.
     */
    @Nullable
    private static Long totalHeapPlusNonHeap(MemoryMXBean memoryBean, Function<MemoryUsage, Long> longFunction) {
        Long heap = negativeToNull(longFunction.apply(memoryBean.getHeapMemoryUsage()));
        Long nonHeap = negativeToNull(longFunction.apply(memoryBean.getNonHeapMemoryUsage()));
        return (heap == null) ? null : (nonHeap == null) ? null : heap + nonHeap;
    }

    /**
     * Creates a gauge which replaces negative values with null to avoid
     * confusing data when metrics are unavailable.
     */
    private static Gauge<Long> nonNegative(Supplier<Long> supplier) {
        return () -> negativeToNull(supplier.get());
    }

    @Nullable
    private static Long negativeToNull(@Nullable Long value) {
        return value == null || value < 0 ? null : value;
    }

    private JvmMetrics() {
        throw new UnsupportedOperationException();
    }
}
