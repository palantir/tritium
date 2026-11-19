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

package com.palantir.tritium.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

final class ExponentialReservoirTest {
    @Test
    void summaryStatisticsUseUniformSnapshot() {
        ExponentialReservoir reservoir = new ExponentialReservoir();
        for (long value : new long[] {-10, -2, 0, 4, 8}) {
            reservoir.update(value);
        }
        ExponentialSnapshot snapshot = reservoir.getSnapshot();
        assertThat(snapshot.getValues()).containsExactly(-10, -2, 0, 4, 8);
        assertThat(snapshot.getMin()).isEqualTo(-10);
        assertThat(snapshot.getMax()).isEqualTo(8);
        assertThat(snapshot.getMean()).isZero();
        assertThat(snapshot.getStdDev()).isEqualTo(Math.sqrt(46));
        assertThat(snapshot.getMedian()).isZero();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        snapshot.dump(output);
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("-10\n-2\n0\n4\n8\n");
    }

    @Test
    @Timeout(15)
    void concurrentWritersDoNotLoseUpdates() throws Exception {
        ExponentialReservoir reservoir = new ExponentialReservoir(4);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> writers = new ArrayList<>();
            for (int writer = 0; writer < 4; writer++) {
                writers.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 10_000; i++) {
                        reservoir.update(1);
                        reservoir.update(-1);
                        reservoir.update(0);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> writer : writers) {
                writer.get();
            }
        } finally {
            executor.shutdownNow();
        }
        ExponentialSnapshot snapshot = reservoir.getSnapshot();
        ExponentialSnapshot.Buckets expected = new ExponentialSnapshot.Buckets(-1, List.of(40_000L));
        assertThat(snapshot.positive()).contains(expected);
        assertThat(snapshot.negative()).contains(expected);
        assertThat(snapshot.zeroCount()).isEqualTo(40_000);
        assertThat(snapshot.sum()).isZero();
    }

    @Test
    void snapshotPreservesSignedBucketsAndGapsAfterFurtherUpdates() {
        ExponentialReservoir reservoir = new ExponentialReservoir(0);
        for (long value : new long[] {0, 0, 1, 2, 2, 8, -1, -8}) {
            reservoir.update(value);
        }
        ExponentialSnapshot snapshot = reservoir.getSnapshot();
        reservoir.update(16);
        reservoir.update(-16);
        reservoir.update(0);

        assertThat(snapshot.sum()).isEqualTo(4);
        assertThat(snapshot.zeroCount()).isEqualTo(2);
        assertThat(snapshot.positive()).contains(new ExponentialSnapshot.Buckets(-1, List.of(1L, 2L, 0L, 1L)));
        assertThat(snapshot.negative()).contains(new ExponentialSnapshot.Buckets(-1, List.of(1L, 0L, 0L, 1L)));
        assertThat(snapshot.getValues()).containsExactly(-8, -1, 0, 0, 1, 2, 2, 8);
    }

    @ParameterizedTest(name = "scale={0}, magnitude={1}, bucket={2}")
    @CsvSource({
        "-4, 1, -1",
        "-4, 65536, 0",
        "-4, 65537, 1",
        "-1, 4, 0",
        "-1, 5, 1",
        "0, 1, -1",
        "0, 8, 2",
        "0, 9, 3",
        "3, 10, 26",
        "4, 10, 53",
        "8, 2, 255",
        "8, 3, 405"
    })
    void observationsLandInExpectedBuckets(int scale, long magnitude, int offset) {
        ExponentialReservoir reservoir = new ExponentialReservoir(scale);
        reservoir.update(magnitude);
        reservoir.update(-magnitude);
        ExponentialSnapshot snapshot = reservoir.getSnapshot();
        ExponentialSnapshot.Buckets expected = new ExponentialSnapshot.Buckets(offset, List.of(1L));
        assertThat(snapshot.positive()).contains(expected);
        assertThat(snapshot.negative()).contains(expected);
    }

    @ParameterizedTest(name = "scale={0}, last bucket={1}")
    @CsvSource({"-4, 3", "-1, 31", "0, 62", "3, 503", "8, 16127"})
    void extremeLongsLandInTheLastBucket(int scale, int offset) {
        ExponentialReservoir reservoir = new ExponentialReservoir(scale);
        reservoir.update(Long.MAX_VALUE);
        reservoir.update(Long.MIN_VALUE);
        ExponentialSnapshot snapshot = reservoir.getSnapshot();
        ExponentialSnapshot.Buckets expected = new ExponentialSnapshot.Buckets(offset, List.of(1L));
        assertThat(snapshot.positive()).contains(expected);
        assertThat(snapshot.negative()).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {53, 62})
    void adjacentLargeIntegersStayOnTheCorrectSideOfABoundary(int exponent) {
        long boundary = 1L << exponent;
        ExponentialReservoir reservoir = new ExponentialReservoir(0);
        for (long value : new long[] {boundary - 1, boundary, boundary + 1}) {
            reservoir.update(value);
            reservoir.update(-value);
        }
        ExponentialSnapshot snapshot = reservoir.getSnapshot();
        ExponentialSnapshot.Buckets expected = new ExponentialSnapshot.Buckets(exponent - 1, List.of(2L, 1L));
        assertThat(snapshot.positive()).contains(expected);
        assertThat(snapshot.negative()).contains(expected);
    }

    @Test
    void integerBoundsSelectTheFirstMatchingBucket() {
        // At scale 3, several fractional bucket boundaries below 2 all round down to 1.
        ExponentialReservoir reservoir = new ExponentialReservoir();
        reservoir.update(1);
        reservoir.update(2);
        reservoir.update(-1);
        reservoir.update(-2);
        ExponentialSnapshot snapshot = reservoir.getSnapshot();
        ExponentialSnapshot.Buckets expected =
                new ExponentialSnapshot.Buckets(-1, List.of(1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L));
        assertThat(snapshot.scale()).isEqualTo(3);
        assertThat(snapshot.positive()).contains(expected);
        assertThat(snapshot.negative()).contains(expected);
    }

    @Test
    void cumulativeCountsAreNotLimitedByTheSummaryReservoir() {
        ExponentialReservoir reservoir = new ExponentialReservoir(4);
        for (int i = 0; i < 10_000; i++) {
            reservoir.update(10);
        }
        ExponentialSnapshot snapshot = reservoir.getSnapshot();
        assertThat(snapshot.sum()).isEqualTo(100_000);
        assertThat(snapshot.positive().orElseThrow().bucketCounts()).containsExactly(10_000L);
        assertThat(snapshot.size()).isLessThan(10_000);
        assertThat(snapshot.get99thPercentile()).isEqualTo(10);
    }

    @Test
    void zeroOnlyObservationsHaveNoSignedBuckets() {
        ExponentialReservoir reservoir = new ExponentialReservoir();
        reservoir.update(0);
        ExponentialSnapshot snapshot = reservoir.getSnapshot();
        assertThat(snapshot.positive()).isEmpty();
        assertThat(snapshot.negative()).isEmpty();
        assertThat(snapshot.zeroCount()).isOne();
        assertThat(snapshot.sum()).isZero();
    }
}
