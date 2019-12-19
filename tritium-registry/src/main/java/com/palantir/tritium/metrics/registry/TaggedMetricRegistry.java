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
import com.palantir.logsafe.SafeArg;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;

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
     * Returns existing gauge metric for the specified metric name or empty if none has been registered.
     *
     * @implNote Implementations should override this method with a more efficient mechanism.
     *
     * @param metricName metric name
     * @return gauge metric or empty if none exists for this name
     */
    @SuppressWarnings("unchecked")
    default <T> Optional<Gauge<T>> gauge(MetricName metricName) {
        Metric metric = getMetrics().get(metricName);
        if (metric instanceof Gauge) {
            return Optional.of((Gauge<T>) metric);
        }
        return Optional.empty();
    }

    /**
     * Returns existing or new gauge metric for the specified metric name.
     *
     * @param metricName metric name
     * @param gauge gauge
     * @return gauge metric
     *
     * In most cases, one typically wants {@link #registerWithReplacement(MetricName, Gauge)} as gauges may
     * retain references to large backing data structures (e.g. queue or cache), and if a gauge is being registered
     * with the same name, one is likely replacing that previous data structure and the registry should not retain
     * references to the previous version. If lookup of an existing gauge is desired, one should use
     * {@link #gauge(MetricName)}.
     */
    // This differs from MetricRegistry and takes the Gauge directly rather than a Supplier<Gauge>
    // @Deprecated
    <T> Gauge<T> gauge(MetricName metricName, Gauge<T> gauge);

    /**
     * Registers and returns the specified gauge, replacing any existing gauge with the specified metric name.
     *
     * @param metricName metric name
     * @param gauge gauge
     */
    // This differs from MetricRegistry and takes the Gauge directly rather than a Supplier<Gauge>
    @SuppressWarnings("deprecation") // explicitly using as desired
    default void registerWithReplacement(MetricName metricName, Gauge<?> gauge) {
        Gauge<?> existing = gauge(metricName, gauge);
        if (existing == gauge) {
            return;
        }
        remove(metricName).ifPresent(removed -> LoggerFactory.getLogger(getClass())
                .debug("Removed previously registered gauge {}", SafeArg.of("metricName", metricName)));
        gauge(metricName, gauge);
    }

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
     * <p>
     * If a metric exists with duplicate tags, then calling {@link TaggedMetricSet#getMetrics}
     * will be impossible.
     *
     * @param safeTagName a tag key which should be added to all metrics in the metrics set
     * @param safeTagValue a tag value which should be added to all metrics in the metrics set
     * @param metrics the metrics which should be added
     */
    void addMetrics(String safeTagName, String safeTagValue, TaggedMetricSet metrics);

    /**
     * Removes a TaggedMetricsSet added via addMetrics from this metrics set.
     */
    Optional<TaggedMetricSet> removeMetrics(String safeTagName, String safeTagValue);

    /**
     * Removes a TaggedMetricsSet added via addMetrics from this metrics set, if currently registered to this metric
     * set.
     *
     * The value is removed only when {@link Object#equals} for the values returns true, therefore
     * you should generally provide the same instance as a previous call to addMetrics.
     *
     * @return true if value was removed
     */
    boolean removeMetrics(String safeTagName, String safeTagValue, TaggedMetricSet metrics);
}
