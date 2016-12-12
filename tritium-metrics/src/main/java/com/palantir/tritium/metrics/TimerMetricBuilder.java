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

import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Timer;
import com.google.common.base.Supplier;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings("PT_EXTENDS_CONCRETE_TYPE")
final class TimerMetricBuilder extends AbstractReservoirMetricBuilder<Timer> {

    TimerMetricBuilder(Supplier<Reservoir> reservoirSupplier) {
        super(Timer.class, reservoirSupplier);
    }

    @Override
    public Timer newMetric() {
        return new ReservoirTimer(getReservoirSupplier().get());
    }

    private static class ReservoirTimer extends Timer {
        private final Reservoir reservoir;

        ReservoirTimer(Reservoir reservoir) {
            super(reservoir);
            this.reservoir = reservoir;
        }

        @Override
        public long getCount() {
            return reservoir.size();
        }
    }
}
