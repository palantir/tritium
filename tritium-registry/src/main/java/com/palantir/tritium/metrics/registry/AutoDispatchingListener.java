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

class AutoDispatchingListener {
    private final TaggedMetricRegistryListener listener;

    AutoDispatchingListener(TaggedMetricRegistryListener listener) {
        this.listener = listener;
    }

    void metricRemoved(Metric metric, MetricName metricName) {
        MetricVisitor.visitMetric(metric, new MetricVisitor<Void>() {
            @Override
            public Void visitGauge(Gauge<?> gauge) {
                listener.onGaugeRemoved(metricName);
                return null;
            }

            @Override
            public Void visitMeter(Meter meter) {
                listener.onMeterRemoved(metricName);
                return null;
            }

            @Override
            public Void visitHistogram(Histogram histogram) {
                listener.onHistogramRemoved(metricName);
                return null;
            }

            @Override
            public Void visitTimer(Timer timer) {
                listener.onTimerRemoved(metricName);
                return null;
            }

            @Override
            public Void visitCounter(Counter counter) {
                listener.onCounterRemoved(metricName);
                return null;
            }
        });
    }

    void metricRemoved(MetricName metricName, Metric metric) {
        MetricVisitor.visitMetric(metric, new MetricVisitor<Void>() {
            @Override
            public Void visitGauge(Gauge<?> gauge) {
                listener.onGaugeAdded(metricName, (Gauge<?>) metric);
                return null;
            }

            @Override
            public Void visitMeter(Meter meter) {
                listener.onMeterAdded(metricName, (Meter) metric);
                return null;
            }

            @Override
            public Void visitHistogram(Histogram histogram) {
                listener.onHistogramAdded(metricName, (Histogram) metric);
                return null;
            }

            @Override
            public Void visitTimer(Timer timer) {
                listener.onTimerAdded(metricName, (Timer) metric);
                return null;
            }

            @Override
            public Void visitCounter(Counter counter) {
                listener.onCounterAdded(metricName, (Counter) metric);
                return null;
            }
        });
    }
}
