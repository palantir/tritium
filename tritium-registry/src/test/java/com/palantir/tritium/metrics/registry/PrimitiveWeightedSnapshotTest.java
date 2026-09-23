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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.codahale.metrics.Snapshot;
import com.codahale.metrics.WeightedSnapshot;
import com.codahale.metrics.WeightedSnapshot.WeightedSample;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

class PrimitiveWeightedSnapshotTest {

    @Test
    void emptySnapshot() {
        Snapshot snapshot = new PrimitiveWeightedSnapshot(new long[0], new double[0]);
        assertThat(snapshot.size()).isEqualTo(0);
        assertThat(snapshot.getMax()).isEqualTo(0);
        assertThat(snapshot.getMin()).isEqualTo(0);
        assertThat(snapshot.getMean()).isEqualTo(0);
        assertThat(snapshot.getStdDev()).isEqualTo(0);
        assertThat(snapshot.getValue(0.5)).isEqualTo(0);
        assertThat(snapshot.getValues()).isEmpty();
    }

    @Test
    void singleElement() {
        Snapshot snapshot = new PrimitiveWeightedSnapshot(new long[] {42}, new double[] {1.0});
        assertThat(snapshot.size()).isEqualTo(1);
        assertThat(snapshot.getMax()).isEqualTo(42);
        assertThat(snapshot.getMin()).isEqualTo(42);
        assertThat(snapshot.getMean()).isEqualTo(42);
        assertThat(snapshot.getStdDev()).isEqualTo(0);
        assertThat(snapshot.getMedian()).isEqualTo(42);
    }

    @Test
    void valuesAreSorted() {
        Snapshot snapshot = new PrimitiveWeightedSnapshot(new long[] {5, 3, 1, 4, 2}, new double[] {1, 1, 1, 1, 1});
        assertThat(snapshot.getValues()).containsExactly(1, 2, 3, 4, 5);
        assertThat(snapshot.getMin()).isEqualTo(1);
        assertThat(snapshot.getMax()).isEqualTo(5);
    }

    @Test
    void weightsAreRespected() {
        // value 100 has weight 9, value 200 has weight 1
        // median should be 100 because it accounts for 90% of weight
        Snapshot snapshot = new PrimitiveWeightedSnapshot(new long[] {200, 100}, new double[] {1.0, 9.0});
        assertThat(snapshot.getMedian()).isEqualTo(100);
    }

    @Test
    void quantileEdgeCases() {
        Snapshot snapshot = new PrimitiveWeightedSnapshot(new long[] {1, 2, 3, 4, 5}, new double[] {1, 1, 1, 1, 1});
        assertThat(snapshot.getValue(0.0)).isEqualTo(1);
        assertThat(snapshot.getValue(1.0)).isEqualTo(5);
        assertThatThrownBy(() -> snapshot.getValue(-0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> snapshot.getValue(1.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> snapshot.getValue(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchesWeightedSnapshotBehavior() {
        // Generate random data and verify identical results vs upstream WeightedSnapshot
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int trial = 0; trial < 20; trial++) {
            int size = rng.nextInt(1, 200);
            long[] values = new long[size];
            double[] weights = new double[size];
            List<WeightedSample> samples = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                values[i] = rng.nextLong(-10_000, 10_000);
                weights[i] = rng.nextDouble(0.01, 100.0);
                samples.add(new WeightedSample(values[i], weights[i]));
            }

            Snapshot primitive = new PrimitiveWeightedSnapshot(values.clone(), weights.clone());
            Snapshot upstream = new WeightedSnapshot(samples);

            assertThat(primitive.size()).as("size trial %d", trial).isEqualTo(upstream.size());
            assertThat(primitive.getMin()).as("min trial %d", trial).isEqualTo(upstream.getMin());
            assertThat(primitive.getMax()).as("max trial %d", trial).isEqualTo(upstream.getMax());
            assertThat(primitive.getMean()).as("mean trial %d", trial).isCloseTo(upstream.getMean(), within(1e-10));
            assertThat(primitive.getStdDev())
                    .as("stddev trial %d", trial)
                    .isCloseTo(upstream.getStdDev(), within(1e-6));
            assertThat(primitive.getValues()).as("values trial %d", trial).containsExactly(upstream.getValues());
            assertThat(primitive.getMedian())
                    .as("median trial %d", trial)
                    .isCloseTo(upstream.getMedian(), within(1e-10));
            assertThat(primitive.get75thPercentile())
                    .as("p75 trial %d", trial)
                    .isCloseTo(upstream.get75thPercentile(), within(1e-10));
            assertThat(primitive.get95thPercentile())
                    .as("p95 trial %d", trial)
                    .isCloseTo(upstream.get95thPercentile(), within(1e-10));
            assertThat(primitive.get99thPercentile())
                    .as("p99 trial %d", trial)
                    .isCloseTo(upstream.get99thPercentile(), within(1e-10));
        }
    }

    @Test
    void largeArraySort() {
        int count = 2000;
        long[] values = new long[count];
        double[] weights = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = (long) count - i;
            weights[i] = 1.0;
        }
        Snapshot snapshot = new PrimitiveWeightedSnapshot(values, weights);
        assertThat(snapshot.getMin()).isEqualTo(1);
        assertThat(snapshot.getMax()).isEqualTo(count);
        long[] sorted = snapshot.getValues();
        for (int i = 1; i < sorted.length; i++) {
            assertThat(sorted[i]).isGreaterThanOrEqualTo(sorted[i - 1]);
        }
    }

    @Test
    void duplicateValues() {
        Snapshot snapshot = new PrimitiveWeightedSnapshot(new long[] {5, 5, 5, 5, 5}, new double[] {1, 2, 3, 4, 5});
        assertThat(snapshot.getMin()).isEqualTo(5);
        assertThat(snapshot.getMax()).isEqualTo(5);
        assertThat(snapshot.getMean()).isEqualTo(5.0);
        assertThat(snapshot.getMedian()).isEqualTo(5);
    }

    @Test
    void zeroWeights() {
        Snapshot snapshot = new PrimitiveWeightedSnapshot(new long[] {1, 2, 3}, new double[] {0, 0, 0});
        assertThat(snapshot.getMean()).isEqualTo(0.0);
    }

    @Test
    void dump() {
        Snapshot snapshot = new PrimitiveWeightedSnapshot(new long[] {3, 1, 2}, new double[] {1, 1, 1});
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        snapshot.dump(out);
        String result = out.toString(StandardCharsets.UTF_8);
        assertThat(result.lines()).containsExactly("1", "2", "3");
    }
}
