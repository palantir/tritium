/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Similar to {@link com.codahale.metrics.MetricRegistry} but allows tagging of {@link Metric}s.
 */
public interface TaggedMetricRegistry extends TaggedMetricSet {

    /**
     * Returns existing or new timer metric for the specified metric name.
     *
     * @param metricName metric name
     * @return timer metric
     */
    Timer timer(MetricName metricName);

    Timer timer(MetricName metricName, Supplier<Timer> timerSupplier);

    /**
     * Returns existing or new meter metric for the specified metric name.
     *
     * @param metricName metric name
     * @return meter metric
     */
    Meter meter(MetricName metricName);

    Meter meter(MetricName metricName, Supplier<Meter> meterSupplier);

    /**
     * Returns existing or new histogram metric for the specified metric name.
     *
     * @param metricName metric name
     * @return histogram metric
     */
    Histogram histogram(MetricName metricName);

    Histogram histogram(MetricName metricName, Supplier<Histogram> histogramSupplier);

    /**
     * Returns existing or new gauge metric for the specified metric name.
     *
     * @param metricName metric name
     * @param gauge gauge
     * @return gauge metric
     */
    // This differs from MetricRegistry and takes the Gauge directly rather than a Supplier<Gauge>
    Gauge gauge(MetricName metricName, Gauge gauge);

    /**
     * Returns existing or new counter metric for the specified metric name.
     *
     * @param metricName metric name
     * @return counter metric
     */
    Counter counter(MetricName metricName);

    Counter counter(MetricName metricName, Supplier<Counter> counterSupplier);

    /**
     * Removes the tagged metric with the specified metric name.
     *
     * @param metricName metric name
     * @return the removed metric
     */
    Optional<Metric> remove(MetricName metricName);

    /**
     * Adds a set of metrics to this TaggedMetricRegistry's metric set,
     * which are to be uniquely identified by the tags provided.
     * <p>
     * So, if I have a metric registry with a single metric called 'foo', and I add it
     * with tag (bar, baz), this registry will now contain 'foo', tagged with (bar, baz).
     *
     * @param tags the tags which will be added to all the metrics in the metric set
     * @param metrics the metrics which should be added
     */
    void addMetrics(String safeTagName, String safeTagValue, TaggedMetricSet metrics);

    /**
     * Removes a TaggedMetricsSet added via addMetrics from this metrics set.
     */
    void removeMetrics(String safeTagName, String safeTagValue);
}
