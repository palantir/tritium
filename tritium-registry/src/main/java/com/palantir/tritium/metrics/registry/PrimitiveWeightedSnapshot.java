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
 * A {@link Snapshot} implementation that avoids the boxing overhead of
 * {@link com.codahale.metrics.WeightedSnapshot} by sorting primitive {@code long[]} values
 * directly rather than using {@code Arrays.sort(Object[], Comparator)}.
 */
final class PrimitiveWeightedSnapshot extends Snapshot {

    static final Snapshot EMPTY = new PrimitiveWeightedSnapshot(new long[0], new double[0]);

    private static final int INSERTION_SORT_THRESHOLD = 47;

    private final long[] values;
    private final double[] weights;
    private final double[] quantiles;
    private final double inverseSumWeight;

    PrimitiveWeightedSnapshot(long[] values, double[] weights) {
        sortParallelArrays(values, weights, 0, values.length - 1);

        this.values = values;
        this.weights = weights;
        this.quantiles = new double[values.length];

        double sumWeight = 0;
        for (double weight : weights) {
            sumWeight += weight;
        }
        this.inverseSumWeight = sumWeight != 0 ? 1.0 / sumWeight : 0;

        double cumulativeQuantile = 0;
        for (int i = 0; i < values.length; i++) {
            this.quantiles[i] = cumulativeQuantile;
            cumulativeQuantile += weights[i] * inverseSumWeight;
        }
    }

    @Override
    public double getValue(double quantile) {
        if (quantile < 0.0 || quantile > 1.0 || Double.isNaN(quantile)) {
            throw new SafeIllegalArgumentException("quantile is not in [0..1]", SafeArg.of("quantile", quantile));
        }

        if (values.length == 0) {
            return 0.0;
        }

        int idx = Arrays.binarySearch(quantiles, quantile);
        if (idx < 0) {
            idx = -idx - 1 - 1;
        }

        if (idx < 1) {
            return values[0];
        }

        if (idx >= values.length) {
            return values[values.length - 1];
        }

        return values[idx];
    }

    @Override
    public int size() {
        return values.length;
    }

    @Override
    public long[] getValues() {
        if (values.length == 0) {
            return values;
        }
        return Arrays.copyOf(values, values.length);
    }

    @Override
    public long getMax() {
        if (values.length == 0) {
            return 0;
        }
        return values[values.length - 1];
    }

    @Override
    public long getMin() {
        if (values.length == 0) {
            return 0;
        }
        return values[0];
    }

    @Override
    public double getMean() {
        if (values.length == 0 || inverseSumWeight == 0) {
            return 0;
        }

        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i] * weights[i];
        }
        return sum * inverseSumWeight;
    }

    @Override
    public double getStdDev() {
        if (values.length <= 1 || inverseSumWeight == 0) {
            return 0;
        }

        final double mean = getMean();
        double variance = 0;

        for (int i = 0; i < values.length; i++) {
            final double diff = values[i] - mean;
            variance += weights[i] * diff * diff;
        }

        return Math.sqrt(variance * inverseSumWeight);
    }

    @Override
    public void dump(OutputStream output) {
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            for (long value : values) {
                out.println(value);
            }
        }
    }

    private static void sortParallelArrays(long[] values, double[] weights, int lo, int hi) {
        int left = lo;
        int right = hi;
        while (right - left >= INSERTION_SORT_THRESHOLD) {
            int pivotIdx = partition(values, weights, left, right);
            if (pivotIdx - left < right - pivotIdx) {
                sortParallelArrays(values, weights, left, pivotIdx - 1);
                left = pivotIdx + 1;
            } else {
                sortParallelArrays(values, weights, pivotIdx + 1, right);
                right = pivotIdx - 1;
            }
        }
        if (left < right) {
            insertionSort(values, weights, left, right);
        }
    }

    private static int partition(long[] values, double[] weights, int left, int right) {
        medianOfThree(values, weights, left, right);
        long pivot = values[(left + right) >>> 1];
        swap(values, weights, (left + right) >>> 1, right - 1);

        int lo = left;
        int hi = right - 1;
        while (true) {
            while (values[++lo] < pivot) {
                // advance
            }
            while (values[--hi] > pivot) {
                // advance
            }
            if (lo >= hi) {
                break;
            }
            swap(values, weights, lo, hi);
        }
        swap(values, weights, lo, right - 1);
        return lo;
    }

    private static void medianOfThree(long[] values, double[] weights, int left, int right) {
        int mid = (left + right) >>> 1;
        if (values[left] > values[mid]) {
            swap(values, weights, left, mid);
        }
        if (values[left] > values[right]) {
            swap(values, weights, left, right);
        }
        if (values[mid] > values[right]) {
            swap(values, weights, mid, right);
        }
    }

    private static void insertionSort(long[] values, double[] weights, int left, int right) {
        for (int idx = left + 1; idx <= right; idx++) {
            long val = values[idx];
            double wt = weights[idx];
            int pos = idx - 1;
            while (pos >= left && values[pos] > val) {
                values[pos + 1] = values[pos];
                weights[pos + 1] = weights[pos];
                pos--;
            }
            values[pos + 1] = val;
            weights[pos + 1] = wt;
        }
    }

    private static void swap(long[] values, double[] weights, int idx1, int idx2) {
        long tmpVal = values[idx1];
        values[idx1] = values[idx2];
        values[idx2] = tmpVal;
        double tmpWt = weights[idx1];
        weights[idx1] = weights[idx2];
        weights[idx2] = tmpWt;
    }
}
