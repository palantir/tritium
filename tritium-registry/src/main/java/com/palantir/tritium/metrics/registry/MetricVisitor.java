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

package com.palantir.tritium.metrics.registry;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;

interface MetricVisitor<T> {
    T visitGauge(Gauge<?> gauge);
    T visitMeter(Meter meter);
    T visitHistogram(Histogram histogram);
    T visitTimer(Timer timer);
    T visitCounter(Counter counter);

    static <T> T visitMetric(Metric metric, MetricVisitor<T> visitor) {
        if (metric instanceof Gauge) {
            return visitor.visitGauge((Gauge<?>) metric);
        } else if (metric instanceof Meter) {
            return visitor.visitMeter((Meter) metric);
        } else if (metric instanceof Histogram) {
            return visitor.visitHistogram((Histogram) metric);
        } else if (metric instanceof Timer) {
            return visitor.visitTimer((Timer) metric);
        } else if (metric instanceof Counter) {
            return visitor.visitCounter((Counter) metric);
        }
        throw new SafeIllegalArgumentException("Unknown metric class", SafeArg.of("metricClass", metric.getClass()));
    }
}
