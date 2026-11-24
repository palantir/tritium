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

import com.codahale.metrics.UniformReservoir;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    void summaryStatisticsUseSuppliedDelegate() {
        ExponentialReservoir reservoir = new ExponentialReservoir(3, new UniformReservoir());
        for (long value : new long[] {-10, -2, 0, 4, 8}) {
            reservoir.update(value);
        }
        ExponentialSnapshot snapshot = (ExponentialSnapshot) reservoir.getSnapshot();
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
        ExponentialSnapshot snapshot = (ExponentialSnapshot) reservoir.getSnapshot();
        assertBuckets(snapshot.positive(), -1, 40_000L);
        assertBuckets(snapshot.negative(), -1, 40_000L);
        assertThat(snapshot.zeroCount()).isEqualTo(40_000);
        assertThat(snapshot.sum()).isZero();
    }

    @Test
    void snapshotPreservesSignedBucketsAndGapsAfterFurtherUpdates() {
        ExponentialReservoir reservoir = new ExponentialReservoir(0);
        for (long value : new long[] {0, 0, 1, 2, 2, 8, -1, -8}) {
            reservoir.update(value);
        }
        ExponentialSnapshot snapshot = (ExponentialSnapshot) reservoir.getSnapshot();
        reservoir.update(16);
        reservoir.update(-16);
        reservoir.update(0);

        assertThat(snapshot.sum()).isEqualTo(4);
        assertThat(snapshot.zeroCount()).isEqualTo(2);
        assertBuckets(snapshot.positive(), -1, 1L, 2L, 0L, 1L);
        assertBuckets(snapshot.negative(), -1, 1L, 0L, 0L, 1L);
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
        ExponentialSnapshot snapshot = (ExponentialSnapshot) reservoir.getSnapshot();
        assertBuckets(snapshot.positive(), offset, 1L);
        assertBuckets(snapshot.negative(), offset, 1L);
    }

    @ParameterizedTest(name = "scale={0}, last bucket={1}")
    @CsvSource({"-4, 3", "-1, 31", "0, 62", "3, 503", "8, 16127"})
    void extremeLongsLandInTheLastBucket(int scale, int offset) {
        ExponentialReservoir reservoir = new ExponentialReservoir(scale);
        reservoir.update(Long.MAX_VALUE);
        reservoir.update(Long.MIN_VALUE);
        ExponentialSnapshot snapshot = (ExponentialSnapshot) reservoir.getSnapshot();
        assertBuckets(snapshot.positive(), offset, 1L);
        assertBuckets(snapshot.negative(), offset, 1L);
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
        ExponentialSnapshot snapshot = (ExponentialSnapshot) reservoir.getSnapshot();
        assertBuckets(snapshot.positive(), exponent - 1, 2L, 1L);
        assertBuckets(snapshot.negative(), exponent - 1, 2L, 1L);
    }

    @Test
    void integerBoundsSelectTheFirstMatchingBucket() {
        // At scale 3, several fractional bucket boundaries below 2 all round down to 1.
        ExponentialReservoir reservoir = new ExponentialReservoir();
        reservoir.update(1);
        reservoir.update(2);
        reservoir.update(-1);
        reservoir.update(-2);
        ExponentialSnapshot snapshot = (ExponentialSnapshot) reservoir.getSnapshot();
        assertThat(snapshot.scale()).isEqualTo(3);
        assertBuckets(snapshot.positive(), -1, 1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L);
        assertBuckets(snapshot.negative(), -1, 1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L);
    }

    @Test
    void cumulativeCountsAreNotLimitedByTheSummaryReservoir() {
        ExponentialReservoir reservoir = new ExponentialReservoir(4);
        for (int i = 0; i < 10_000; i++) {
            reservoir.update(10);
        }
        ExponentialSnapshot snapshot = (ExponentialSnapshot) reservoir.getSnapshot();
        assertThat(snapshot.sum()).isEqualTo(100_000);
        assertBuckets(snapshot.positive(), 53, 10_000L);
        assertThat(snapshot.size()).isLessThan(10_000);
        assertThat(snapshot.get99thPercentile()).isEqualTo(10);
    }

    @ParameterizedTest(name = "scale={0}, magnitude={1}, bucket={2}")
    @CsvSource({
        // Neighbors of floor(2^(105/2)).
        "1, 6369051672525771, 104",
        "1, 6369051672525772, 104",
        "1, 6369051672525773, 105",
        // Neighbors of floor(2^(417/8)).
        "3, 4911210218475897, 416",
        "3, 4911210218475898, 416",
        "3, 4911210218475899, 417",
        // Neighbors of floor(2^(13313/256)), at the maximum supported scale.
        "8, 4515810124676285, 13312",
        "8, 4515810124676286, 13312",
        "8, 4515810124676287, 13313"
    })
    void fractionalBoundariesUseExactIntegerFloors(int scale, long magnitude, int offset) {
        ExponentialReservoir reservoir = new ExponentialReservoir(scale);
        reservoir.update(magnitude);
        reservoir.update(-magnitude);
        ExponentialHistogramCapture capture = (ExponentialHistogramCapture) reservoir.getSnapshot();
        assertBuckets(capture.positive(), offset, 1L);
        assertBuckets(capture.negative(), offset, 1L);
    }

    @Test
    void zeroOnlyObservationsHaveNoSignedBuckets() {
        ExponentialReservoir reservoir = new ExponentialReservoir();
        reservoir.update(0);
        ExponentialSnapshot snapshot = (ExponentialSnapshot) reservoir.getSnapshot();
        assertThat(snapshot.positive()).isEmpty();
        assertThat(snapshot.negative()).isEmpty();
        assertThat(snapshot.zeroCount()).isOne();
        assertThat(snapshot.sum()).isZero();
    }

    @Test
    void forwardsDelegateExemplars() {
        ExemplarMetadataProvider<String> provider = () -> "trace-id";
        ExponentialReservoir reservoir = new ExponentialReservoir(
                3,
                LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                        .exemplarProvider(provider)
                        .build());
        reservoir.update(42);
        ExponentialSnapshot snapshot = (ExponentialSnapshot) reservoir.getSnapshot();
        assertThat(snapshot.getSamples(provider)).singleElement().satisfies(exemplar -> {
            assertThat(exemplar.value()).isEqualTo(42);
            assertThat(exemplar.metadata()).isEqualTo("trace-id");
        });
        assertThat(snapshot.sum()).isEqualTo(42);
        assertThat(snapshot.getValues()).containsExactly(42);
    }

    @Test
    void defaultDelegateHasNoExemplars() {
        ExponentialReservoir reservoir = new ExponentialReservoir();
        reservoir.update(42);
        ExponentialSnapshot snapshot = (ExponentialSnapshot) reservoir.getSnapshot();
        assertThat(snapshot.getSamples(() -> "trace-id")).isEmpty();
    }

    private static void assertBuckets(
            Optional<ExponentialHistogramCapture.Buckets> buckets, int offset, long... counts) {
        ExponentialHistogramCapture.Buckets captured = buckets.orElseThrow();
        assertThat(captured.offset()).isEqualTo(offset);
        assertThat(captured.size()).isEqualTo(counts.length);
        for (int index = 0; index < counts.length; index++) {
            assertThat(captured.count(index)).isEqualTo(counts[index]);
        }
    }
}
