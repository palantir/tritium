/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Timer;
import java.util.function.Supplier;

final class MetricRegistryWithReservoirs extends MetricRegistry {

    private final HistogramMetricBuilder histogramMetricBuilder;
    private final TimerMetricBuilder timerMetricBuilder;

    MetricRegistryWithReservoirs(Supplier<Reservoir> reservoirSupplier) {
        checkNotNull(reservoirSupplier, "reservoirSupplier");
        this.histogramMetricBuilder = new HistogramMetricBuilder(reservoirSupplier);
        this.timerMetricBuilder = new TimerMetricBuilder(reservoirSupplier);
    }

    @Override
    public Histogram histogram(String name) {
        return MetricRegistries.getOrAdd(this, name, histogramMetricBuilder);
    }

    @Override
    public Timer timer(String name) {
        return MetricRegistries.getOrAdd(this, name, timerMetricBuilder);
    }
}
