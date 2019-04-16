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
import static com.palantir.logsafe.Preconditions.checkState;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
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
    @Nonnull
    static Reservoir hdrHistogramReservoir() {
        Recorder recorder = twoSignificantDigits().get();
        return hdrHistogramReservoir(recorder);
    }

    @Nonnull
    private static Reservoir hdrHistogramReservoir(Recorder recorder) {
        checkNotNull(recorder, "recorder");
        return new HdrHistogramReservoir(recorder);
    }

    /**
     * Supplies reservoirs backed by sliding time window array that store measurements for the specified sliding
     * time window.
     *
     * <p>
     * See also:
     * <ul>
     * <li>
     * <a href="https://github.com/dropwizard/metrics/issues/1138">
     * Drop-in replacement of sliding time window reservoir
     * </a>
     * </li>
     * <a href="https://github.com/dropwizard/metrics/pull/1139">
     * Issue #1138 Replacement of sliding time window
     * </a>
     * <li>
     * <a href="https://medium.com/hotels-com-technology/your-latency-metrics-could-be-misleading-you-how-hdrhistogram-can-help-9d545b598374">
     * Your Dropwizard Latency Metrics Could Be Misleading You — How Rolling-Metrics and HdrHistogram Can Help
     * </a>
     * </li>
     * </ul>
     * </p>
     *
     * @param window window of time
     * @param windowUnit unit for window
     * @return reservoir
     */
    @Nonnull
    static Reservoir slidingTimeWindowArrayReservoir(long window, TimeUnit windowUnit) {
        return slidingTimeWindowArrayReservoir(window, windowUnit, Clock.defaultClock());
    }

    @Nonnull
    @VisibleForTesting
    static Reservoir slidingTimeWindowArrayReservoir(long window, TimeUnit windowUnit, Clock clock) {
        checkState(window > 0, "window must be positive");
        checkNotNull(windowUnit, "windowUnit");
        checkNotNull(clock, "clock");
        return new SlidingTimeWindowArrayReservoir(window, windowUnit, clock);
    }

}
