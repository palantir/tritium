/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableSortedMap;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedMap;

public final class Tagged {
    private Tagged() {}

    public static <M extends Metric> ImmutableSortedMap<String, M> filter(
            Map<MetricName, Metric> metrics, Class<M> clazz) {
        return metrics.entrySet().stream()
                .filter(metricNameMetricEntry -> clazz.isInstance(metricNameMetricEntry.getValue()))
                .collect(ImmutableSortedMap.toImmutableSortedMap(
                        Comparator.comparing(Object::toString),
                        entry ->
                                entry.getKey().safeName() + ":" + entry.getKey().safeTags(),
                        entry -> clazz.cast(entry.getValue())));
    }

    @SuppressWarnings("SystemOut") // dumping metrics to standard out
    public static void report(ConsoleReporter reporter, TaggedMetricRegistry taggedMetricRegistry) {
        Map<MetricName, Metric> metrics = taggedMetricRegistry.getMetrics();
        if (!metrics.isEmpty()) {
            System.out.println("Tagged Metrics:");
            @SuppressWarnings("rawtypes")
            SortedMap<String, Gauge> gauges = filter(metrics, Gauge.class);
            SortedMap<String, Counter> counters = filter(metrics, Counter.class);
            SortedMap<String, Histogram> histograms = filter(metrics, Histogram.class);
            SortedMap<String, Meter> meters = filter(metrics, Meter.class);
            SortedMap<String, Timer> timers = filter(metrics, Timer.class);
            reporter.report(gauges, counters, histograms, meters, timers);
        }
    }
}
