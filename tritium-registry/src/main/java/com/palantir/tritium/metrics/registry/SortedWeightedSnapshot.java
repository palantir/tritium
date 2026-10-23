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
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Dropwizard's {@link com.codahale.metrics.WeightedSnapshot}, built from arrays that {@link SampleCollector} has
 * already sorted and normalized, with the same quantile, mean and standard deviation calculations.
 */
final class SortedWeightedSnapshot extends Snapshot {

    private final long[] values;
    private final double[] normWeights;
    private final double[] quantiles;

    SortedWeightedSnapshot(long[] values, double[] normWeights, double[] quantiles) {
        this.values = values;
        this.normWeights = normWeights;
        this.quantiles = quantiles;
    }

    @Override
    public double getValue(double quantile) {
        if (quantile < 0.0 || quantile > 1.0 || Double.isNaN(quantile)) {
            throw new SafeIllegalArgumentException("Quantile is not in [0..1]", SafeArg.of("quantile", quantile));
        }
        if (values.length == 0) {
            return 0.0;
        }
        int posx = Arrays.binarySearch(quantiles, quantile);
        if (posx < 0) {
            posx = -posx - 2;
        }
        if (posx < 1) {
            return values[0];
        }
        if (posx >= values.length) {
            return values[values.length - 1];
        }
        return values[posx];
    }

    @Override
    public int size() {
        return values.length;
    }

    @Override
    public long[] getValues() {
        return Arrays.copyOf(values, values.length);
    }

    @Override
    public long getMax() {
        return values.length == 0 ? 0 : values[values.length - 1];
    }

    @Override
    public long getMin() {
        return values.length == 0 ? 0 : values[0];
    }

    @Override
    public double getMean() {
        if (values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i] * normWeights[i];
        }
        return sum;
    }

    @Override
    public double getStdDev() {
        // two-pass algorithm for variance, avoids numeric overflow
        if (values.length <= 1) {
            return 0;
        }
        double mean = getMean();
        double variance = 0;
        for (int i = 0; i < values.length; i++) {
            double diff = values[i] - mean;
            variance += normWeights[i] * diff * diff;
        }
        return Math.sqrt(variance);
    }

    @Override
    public void dump(OutputStream output) {
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            for (long value : values) {
                out.printf("%d%n", value);
            }
        }
    }
}
