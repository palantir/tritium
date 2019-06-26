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
import com.palantir.logsafe.Preconditions;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * {@link GarbageCollectorMetrics} provides the same data as codahale GarbageCollectorMetricSet, but uses
 * tags to describe each collector instead of different metric names. This allows for simpler dashboards which
 * do not need to be updated with new GC implementations.
 */
final class GarbageCollectorMetrics {

    /**
     * Registers gauges <pre>jvm.gc.count</pre> and <pre>jvm.gc.time</pre> tagged with <pre>{collector: NAME}</pre>.
     */
    static void register(TaggedMetricRegistry metrics) {
        Preconditions.checkNotNull(metrics, "TaggedMetricRegistry is required");
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            String collector = canonicalName(gc.getName());
            metrics.gauge(name(collector, "count"), gc::getCollectionCount);
            metrics.gauge(name(collector, "time"), gc::getCollectionTime);
        }
    }

    private static String canonicalName(String collectorName) {
        return CharMatcher.whitespace().replaceFrom(collectorName, "-");
    }

    private static MetricName name(String collector, String suffix) {
        return MetricName.builder()
                .safeName("jvm.gc." + suffix)
                .putSafeTags("collector", collector)
                .build();
    }

    private GarbageCollectorMetrics() {}
}
