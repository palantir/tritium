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

import com.codahale.metrics.RatioGauge;
import com.google.common.base.CharMatcher;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

/**
 * {@link MemoryPoolMetrics} provides the same data as codahale MemoryUsageGaugeSet 'pools' section, but uses tags to
 * describe each pool instead of different metric names. This allows for simpler dashboards which do not need to be
 * updated with new pools.
 */
final class MemoryPoolMetrics {

    /**
     * Registers the following metrics, tagged with
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
     */
    static void register(TaggedMetricRegistry registry) {
        JvmMemoryPoolsMetrics metrics = JvmMemoryPoolsMetrics.of(registry);
        for (MemoryPoolMXBean memoryPool : ManagementFactory.getMemoryPoolMXBeans()) {
            String poolName = canonicalName(memoryPool.getName());

            metrics.max().memoryPool(poolName).build(() -> memoryPool.getUsage().getMax());
            metrics.used()
                    .memoryPool(poolName)
                    .build(() -> memoryPool.getUsage().getUsed());
            metrics.committed()
                    .memoryPool(poolName)
                    .build(() -> memoryPool.getUsage().getCommitted());
            metrics.init()
                    .memoryPool(poolName)
                    .build(() -> memoryPool.getUsage().getInit());

            metrics.usage().memoryPool(poolName).build(new RatioGauge() {
                @Override
                protected Ratio getRatio() {
                    MemoryUsage memoryUsage = memoryPool.getUsage();
                    long maximum = memoryUsage.getMax() == -1 ? memoryUsage.getCommitted() : memoryUsage.getMax();
                    return Ratio.of(memoryUsage.getUsed(), maximum);
                }
            });

            // MetricPool.getCollectionUsage is not supported by all implementations, returning null in some cases.
            if (memoryPool.getCollectionUsage() != null) {
                metrics.usedAfterGc()
                        .memoryPool(poolName)
                        .build(() -> memoryPool.getCollectionUsage().getUsed());
            }
        }
    }

    private static String canonicalName(String collectorName) {
        return CharMatcher.whitespace().replaceFrom(collectorName, "-");
    }

    private MemoryPoolMetrics() {}
}
