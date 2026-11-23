/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Snapshot;
import java.io.OutputStream;
import java.util.Optional;

/**
 * An immutable cumulative histogram snapshot. Total count can be derived from the captured bucket counts,
 * including the zero bucket. The sum covers the captured bucket observations.
 * Uniform summary statistics are captured independently.
 */
final class ExponentialSnapshot extends Snapshot implements ExponentialHistogramCapture {
    private final Snapshot summaries;
    private final double sum;
    private final int scale;
    private final long zeroCount;
    private final Optional<Buckets> positive;
    private final Optional<Buckets> negative;

    ExponentialSnapshot(
            Snapshot summaries,
            double sum,
            int scale,
            long zeroCount,
            Optional<Buckets> positive,
            Optional<Buckets> negative) {
        this.summaries = summaries;
        this.sum = sum;
        this.scale = scale;
        this.zeroCount = zeroCount;
        this.positive = positive;
        this.negative = negative;
    }

    @Override
    public double sum() {
        return sum;
    }

    @Override
    public int scale() {
        return scale;
    }

    @Override
    public long zeroCount() {
        return zeroCount;
    }

    @Override
    public double zeroThreshold() {
        return 0.0;
    }

    @Override
    public Optional<Buckets> positive() {
        return positive;
    }

    @Override
    public Optional<Buckets> negative() {
        return negative;
    }

    @Override
    public double getValue(double quantile) {
        return summaries.getValue(quantile);
    }

    @Override
    public long[] getValues() {
        return summaries.getValues();
    }

    @Override
    public int size() {
        return summaries.size();
    }

    @Override
    public long getMax() {
        return summaries.getMax();
    }

    @Override
    public double getMean() {
        return summaries.getMean();
    }

    @Override
    public long getMin() {
        return summaries.getMin();
    }

    @Override
    public double getStdDev() {
        return summaries.getStdDev();
    }

    @Override
    public void dump(OutputStream output) {
        summaries.dump(output);
    }
}
