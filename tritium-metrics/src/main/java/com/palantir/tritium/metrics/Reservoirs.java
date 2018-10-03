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

import static com.google.common.base.Preconditions.checkNotNull;

import com.codahale.metrics.Reservoir;
import java.util.function.Supplier;
import org.HdrHistogram.Recorder;
import org.mpierce.metrics.reservoir.hdrhistogram.HdrHistogramReservoir;

/**
 * Static methods for supplying HdrHistogram based reservoirs.
 */
final class Reservoirs {

    private Reservoirs() {
        throw new UnsupportedOperationException();
    }

    private static Supplier<Recorder> twoSignificantDigits() {
        return () -> new Recorder(2);
    }

    /**
     * Metric registry which produces timers and histograms backed by high dynamic range histograms.
     * <p>
     * See <a href="http://taint.org/2014/01/16/145944a.html">
     * Don’t use Timers with exponentially-decaying reservoirs in Graphite
     * </a>
     */
    static Supplier<Reservoir> hdrHistogramReservoirSupplier() {
        return hdrHistogramReservoirSupplier(twoSignificantDigits());
    }

    static Supplier<Reservoir> hdrHistogramReservoirSupplier(final Supplier<Recorder> recorder) {
        checkNotNull(recorder, "recorder");
        return () -> new HdrHistogramReservoir(recorder.get());
    }

}
