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
import java.util.Map;

/**
 * Similar to {@link com.codahale.metrics.MetricRegistry} but allows tagging of {@link Metric}s.
 */
public interface TaggedMetricRegistry {

    Timer timer(MetricName metric);

    Meter meter(MetricName metric);

    Histogram histogram(MetricName metric);

    // This differs from MetricRegistry and takes the Gauge directly rather than a Supplier<Gauge>
    Gauge gauge(MetricName metric, Gauge gauge);

    Counter counter(MetricName metric);

    Map<MetricName, Metric> getMetrics();
}
