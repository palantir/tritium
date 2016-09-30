/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.mpierce.metrics.reservoir.hdrhistogram.HdrHistogramReservoir;

/**
 * Metric registry which produces timers and histograms backed by high dynamic range histograms.
 * <p>
 * See <a href="http://taint.org/2014/01/16/145944a.html">
 * Don’t use Timers with exponentially-decaying reservoirs in Graphite
 * </a>
 */
@SuppressFBWarnings(justification = "Dropwizard MetricRegistry is a concrete type, not an interface")
final class HdrHistogramMetricRegistry extends MetricRegistry {

    static final MetricBuilder<Histogram> HISTOGRAMS =
            new MetricBuilder<Histogram>() {
                @Override
                public Histogram newMetric() {
                    return new Histogram(new HdrHistogramReservoir());
                }

                @Override
                public boolean isInstance(Metric metric) {
                    return Histogram.class.isInstance(metric);
                }
            };

    static final MetricBuilder<Timer> TIMERS =
            new MetricBuilder<Timer>() {
                @Override
                public Timer newMetric() {
                    return new Timer(new HdrHistogramReservoir());
                }

                @Override
                public boolean isInstance(Metric metric) {
                    return Timer.class.isInstance(metric);
                }
            };

    private HdrHistogramMetricRegistry() {}

    public static HdrHistogramMetricRegistry create() {
        HdrHistogramMetricRegistry metricRegistry = new HdrHistogramMetricRegistry();
        metricRegistry.register(MetricRegistry.name(MetricRegistry.class, "reservoirType"),
                new Gauge<String>() {
                    @Override
                    public String getValue() {
                        return "HDR Histogram";
                    }
                });
        return metricRegistry;
    }

    @Override
    public Histogram histogram(String name) {
        return MetricRegistries.getOrAdd(this, name, HISTOGRAMS);
    }

    @Override
    public Timer timer(String name) {
        return MetricRegistries.getOrAdd(this, name, TIMERS);
    }

}
