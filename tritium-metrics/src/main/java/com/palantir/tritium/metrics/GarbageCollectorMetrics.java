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

package com.palantir.tritium.metrics;

import com.google.common.base.CharMatcher;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

/**
 * {@link GarbageCollectorMetrics} provides the same data as codahale GarbageCollectorMetricSet, but uses tags to
 * describe each collector instead of different metric names. This allows for simpler dashboards which do not need to be
 * updated with new GC implementations.
 */
final class GarbageCollectorMetrics {

    private static final SafeLogger log = SafeLoggerFactory.get(GarbageCollectorMetrics.class);

    private static Map<String, Map<String, Long>> collectorBytesCollected = new ConcurrentHashMap<>();

    /**
     * Registers gauges {@code jvm.gc.count} and {@code jvm.gc.time} tagged with {@code {collector: NAME}}.
     */
    static void register(TaggedMetricRegistry metrics) {
        JvmGcMetrics gcMetrics = JvmGcMetrics.of(metrics);
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            String canonicalCollector = canonicalName(gc.getName());
            gcMetrics.count().collector(canonicalCollector).build(gc::getCollectionCount);
            gcMetrics.time().collector(canonicalCollector).build(gc::getCollectionTime);
            registerGarbageCollectionNotificationListener(gc);
            for (String memoryPool : gc.getMemoryPoolNames()) {
                String canonicalMemoryPool = canonicalName(memoryPool);
                gcMetrics
                        .bytesCollected()
                        .collector(canonicalCollector)
                        .memoryPool(canonicalMemoryPool)
                        .build(() -> {
                            if (collectorBytesCollected.containsKey(canonicalCollector)) {
                                Map<String, Long> memoryPoolBytesCollected =
                                        collectorBytesCollected.get(canonicalCollector);
                                if (memoryPoolBytesCollected.containsKey(canonicalMemoryPool)) {
                                    Long bytesCollected = memoryPoolBytesCollected.get(canonicalMemoryPool);
                                    if (bytesCollected > 0) {
                                        return bytesCollected;
                                    }
                                }
                            }
                            return 0L;
                        });
            }
        }
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        gcMetrics.finalizerQueueSize(memoryBean::getObjectPendingFinalizationCount);
    }

    private static void registerGarbageCollectionNotificationListener(GarbageCollectorMXBean garbageCollector) {
        if (!(garbageCollector instanceof NotificationEmitter)) {
            log.warn("Cannot retrieve garbage collection events, skipping.");
            return;
        }

        NotificationEmitter notificationEmitter = (NotificationEmitter) garbageCollector;
        NotificationListener notificationListener = (notification, _handback) -> {
            if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                GarbageCollectionNotificationInfo gcNotificationInfo =
                        GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());

                reportGc(gcNotificationInfo);
            }
        };
        notificationEmitter.addNotificationListener(notificationListener, null, null);
    }

    private static void reportGc(GarbageCollectionNotificationInfo gcNotificationInfo) {
        GcInfo gcInfo = gcNotificationInfo.getGcInfo();
        String canonicalCollector = canonicalName(gcNotificationInfo.getGcName());
        gcInfo.getMemoryUsageBeforeGc().forEach((memoryPool, memoryUsageBefore) -> {
            if (!gcInfo.getMemoryUsageAfterGc().containsKey(memoryPool)) {
                return;
            }
            long bytesUsedAfter = gcInfo.getMemoryUsageAfterGc().get(memoryPool).getUsed();
            long bytesCollected = memoryUsageBefore.getUsed() - bytesUsedAfter;
            if (bytesCollected == 0) {
                return;
            }
            String canonicalMemoryPool = canonicalName(memoryPool);
            if (!collectorBytesCollected.containsKey(canonicalCollector)) {
                collectorBytesCollected.put(canonicalCollector, new ConcurrentHashMap<>());
            }
            Map<String, Long> memoryPoolBytesCollected = collectorBytesCollected.get(canonicalCollector);
            if (!memoryPoolBytesCollected.containsKey(canonicalMemoryPool)) {
                memoryPoolBytesCollected.put(canonicalMemoryPool, 0L);
            }
            memoryPoolBytesCollected.put(
                    canonicalMemoryPool, memoryPoolBytesCollected.get(canonicalMemoryPool) + bytesCollected);
        });
    }

    private static String canonicalName(String collectorName) {
        return CharMatcher.whitespace().replaceFrom(collectorName, "-");
    }

    private GarbageCollectorMetrics() {}
}
