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
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.ThreadDeadlockDetector;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** {@link JvmMetrics} provides a standard set of metrics for debugging java services. */
public final class JvmMetrics {
    private static final SafeLogger log = SafeLoggerFactory.get(JvmMetrics.class);

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
        MetricRegistries.registerAll(
                registry, "jvm.buffers", new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer()));
        registerClassLoading(metrics);
        registerJvmMemory(registry);
        registerThreads(metrics);
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
        metrics.totalInit(
                (NonNegativeGauge) () -> memoryBean.getHeapMemoryUsage().getInit()
                        + memoryBean.getNonHeapMemoryUsage().getInit());
        metrics.totalUsed(
                (NonNegativeGauge) () -> memoryBean.getHeapMemoryUsage().getUsed()
                        + memoryBean.getNonHeapMemoryUsage().getUsed());
        metrics.totalMax(
                (NonNegativeGauge) () -> memoryBean.getHeapMemoryUsage().getMax()
                        + memoryBean.getNonHeapMemoryUsage().getMax());
        metrics.totalCommitted(
                (NonNegativeGauge) () -> memoryBean.getHeapMemoryUsage().getCommitted()
                        + memoryBean.getNonHeapMemoryUsage().getCommitted());
        // jvm.memory.heap
        metrics.heapInit(
                (NonNegativeGauge) () -> memoryBean.getHeapMemoryUsage().getInit());
        metrics.heapUsed(
                (NonNegativeGauge) () -> memoryBean.getHeapMemoryUsage().getUsed());
        metrics.heapMax((NonNegativeGauge) () -> memoryBean.getHeapMemoryUsage().getMax());
        metrics.heapCommitted(
                (NonNegativeGauge) () -> memoryBean.getHeapMemoryUsage().getCommitted());
        metrics.heapUsage(new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                MemoryUsage heapMemoryUsage = memoryBean.getHeapMemoryUsage();
                long used = heapMemoryUsage.getUsed();
                long max = heapMemoryUsage.getMax();
                return (used < 0 || max < 0) ? Ratio.of(Double.NaN, Double.NaN) : Ratio.of(used, max);
            }
        });
        // jvm.memory.non-heap
        metrics.nonHeapInit(
                (NonNegativeGauge) () -> memoryBean.getNonHeapMemoryUsage().getInit());
        metrics.nonHeapUsed(
                (NonNegativeGauge) () -> memoryBean.getNonHeapMemoryUsage().getUsed());
        metrics.nonHeapMax(
                (NonNegativeGauge) () -> memoryBean.getNonHeapMemoryUsage().getMax());
        metrics.nonHeapCommitted(
                (NonNegativeGauge) () -> memoryBean.getNonHeapMemoryUsage().getCommitted());
        metrics.nonHeapUsage(new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                MemoryUsage nonHeapMemoryUsage = memoryBean.getNonHeapMemoryUsage();
                long used = nonHeapMemoryUsage.getUsed();
                long max = nonHeapMemoryUsage.getMax();
                return (used < 0 || max < 0) ? Ratio.of(Double.NaN, Double.NaN) : Ratio.of(used, max);
            }
        });
    }

    /** Gauge which replaces negative values with null to avoid confusing data when metrics are unavailable. */
    @FunctionalInterface
    private interface NonNegativeGauge extends Gauge<Long> {
        @Nullable
        @Override
        default Long getValue() {
            long value = getValueAsLong();
            return value >= 0 ? value : null;
        }

        long getValueAsLong();
    }

    private JvmMetrics() {
        throw new UnsupportedOperationException();
    }
}
