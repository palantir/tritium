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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Reservoir;
import com.google.common.base.Supplier;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

final class HistogramMetricBuilder extends AbstractReservoirMetricBuilder<Histogram> {

    HistogramMetricBuilder(Supplier<Reservoir> reservoirSupplier) {
        super(Histogram.class, reservoirSupplier);
    }

    @Override
    public Histogram newMetric() {
        return new ReservoirHistogram(getReservoirSupplier().get());
    }

    @SuppressFBWarnings("PT_EXTENDS_CONCRETE_TYPE")
    private static class ReservoirHistogram extends Histogram {
        private final Reservoir reservoir;

        ReservoirHistogram(Reservoir reservoir) {
            super(reservoir);
            this.reservoir = reservoir;
        }

        @Override
        public long getCount() {
            return reservoir.size();
        }
    }
}
