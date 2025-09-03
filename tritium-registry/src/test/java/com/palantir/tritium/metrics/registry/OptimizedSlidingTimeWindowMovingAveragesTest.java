/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Clock;
import com.codahale.metrics.Meter;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OptimizedSlidingTimeWindowMovingAveragesTest {
    private ManualClock clock;
    private OptimizedSlidingTimeWindowMovingAverages movingAverages;
    private Meter meter;

    @BeforeEach
    public void init() {
        clock = new ManualClock();
        movingAverages = new OptimizedSlidingTimeWindowMovingAverages(clock);
        meter = new Meter(movingAverages, clock);
    }

    @Test
    public void normalizeIndex() {

        assertThat(OptimizedSlidingTimeWindowMovingAverages.normalizeIndex(0)).isEqualTo(0);
        assertThat(OptimizedSlidingTimeWindowMovingAverages.normalizeIndex(900)).isEqualTo(0);
        assertThat(OptimizedSlidingTimeWindowMovingAverages.normalizeIndex(9000))
                .isEqualTo(0);
        assertThat(OptimizedSlidingTimeWindowMovingAverages.normalizeIndex(-900))
                .isEqualTo(0);

        assertThat(OptimizedSlidingTimeWindowMovingAverages.normalizeIndex(1)).isEqualTo(1);

        assertThat(OptimizedSlidingTimeWindowMovingAverages.normalizeIndex(899)).isEqualTo(899);
        assertThat(OptimizedSlidingTimeWindowMovingAverages.normalizeIndex(-1)).isEqualTo(899);
        assertThat(OptimizedSlidingTimeWindowMovingAverages.normalizeIndex(-901))
                .isEqualTo(899);
    }

    @Test
    public void calculateIndexOfTick() {

        OptimizedSlidingTimeWindowMovingAverages stwm = new OptimizedSlidingTimeWindowMovingAverages(clock);

        assertThat(stwm.calculateIndexOfTick(Instant.ofEpochSecond(0L))).isEqualTo(0);
        assertThat(stwm.calculateIndexOfTick(Instant.ofEpochSecond(1L))).isEqualTo(1);
    }

    @Test
    public void mark_max_without_cleanup() {

        int markCount = OptimizedSlidingTimeWindowMovingAverages.NUMBER_OF_BUCKETS;

        // compensate the first addSeconds in the loop; first tick should be at zero
        clock.addSeconds(-1);

        for (int i = 0; i < markCount; i++) {
            clock.addSeconds(1);
            meter.mark();
        }

        // verify that no cleanup happened yet
        assertThat(movingAverages.oldestBucketTime()).isEqualTo(Instant.ofEpochSecond(0L));

        assertThat(meter.getOneMinuteRate()).isEqualTo(60.0);
        assertThat(meter.getFiveMinuteRate()).isEqualTo(300.0);
        assertThat(meter.getFifteenMinuteRate()).isEqualTo(900.0);
    }

    @Test
    public void mark_first_cleanup() {

        int markCount = OptimizedSlidingTimeWindowMovingAverages.NUMBER_OF_BUCKETS + 1;

        // compensate the first addSeconds in the loop; first tick should be at zero
        clock.addSeconds(-1);

        for (int i = 0; i < markCount; i++) {
            clock.addSeconds(1);
            meter.mark();
        }

        // verify that at least one cleanup happened
        assertThat(movingAverages.oldestBucketTime()).isNotEqualTo(Instant.EPOCH);

        assertThat(meter.getOneMinuteRate()).isEqualTo(60.0);
        assertThat(meter.getFiveMinuteRate()).isEqualTo(300.0);
        assertThat(meter.getFifteenMinuteRate()).isEqualTo(900.0);
    }

    @Test
    public void mark_10_values() {

        // compensate the first addSeconds in the loop; first tick should be at zero
        clock.addSeconds(-1);

        for (int i = 0; i < 10; i++) {
            clock.addSeconds(1);
            meter.mark();
        }

        assertThat(meter.getCount()).isEqualTo(10L);
        assertThat(meter.getOneMinuteRate()).isEqualTo(10.0);
        assertThat(meter.getFiveMinuteRate()).isEqualTo(10.0);
        assertThat(meter.getFifteenMinuteRate()).isEqualTo(10.0);
    }

    @Test
    public void mark_1000_values() {

        for (int i = 0; i < 1000; i++) {
            clock.addSeconds(1);
            meter.mark();
        }

        // only 60/300/900 of the 1000 events took place in the last 1/5/15 minute(s)
        assertThat(meter.getOneMinuteRate()).isEqualTo(60.0);
        assertThat(meter.getFiveMinuteRate()).isEqualTo(300.0);
        assertThat(meter.getFifteenMinuteRate()).isEqualTo(900.0);
    }

    @Test
    public void cleanup_pause_shorter_than_window() {

        meter.mark(10);

        // no mark for three minutes
        clock.addSeconds(180);
        assertThat(meter.getOneMinuteRate()).isEqualTo(0.0);
        assertThat(meter.getFiveMinuteRate()).isEqualTo(10.0);
        assertThat(meter.getFifteenMinuteRate()).isEqualTo(10.0);
    }

    @Test
    public void cleanup_window_wrap_around() {

        // mark at 14:40 minutes of the 15-minute window...
        clock.addSeconds(880);
        meter.mark(10);

        // and query at 15:30 minutes (the bucket index must have wrapped around)
        clock.addSeconds(50);
        assertThat(meter.getOneMinuteRate()).isEqualTo(10.0);
        assertThat(meter.getFiveMinuteRate()).isEqualTo(10.0);
        assertThat(meter.getFifteenMinuteRate()).isEqualTo(10.0);

        // and query at 30:10 minutes (the bucket index must have wrapped around for the second time)
        clock.addSeconds(880);
        assertThat(meter.getOneMinuteRate()).isEqualTo(0.0);
        assertThat(meter.getFiveMinuteRate()).isEqualTo(0.0);
        assertThat(meter.getFifteenMinuteRate()).isEqualTo(0.0);
    }

    @Test
    public void cleanup_pause_longer_than_two_windows() {

        meter.mark(10);

        // after forty minutes all rates should be zero
        clock.addSeconds(2400);
        assertThat(meter.getOneMinuteRate()).isEqualTo(0.0);
        assertThat(meter.getFiveMinuteRate()).isEqualTo(0.0);
        assertThat(meter.getFifteenMinuteRate()).isEqualTo(0.0);
    }

    static final class ManualClock extends Clock {
        private long ticksInNanos;

        public synchronized void addSeconds(int seconds) {
            ticksInNanos += TimeUnit.NANOSECONDS.convert(seconds, TimeUnit.SECONDS);
        }

        @Override
        public synchronized long getTick() {
            return ticksInNanos;
        }

        @Override
        public synchronized long getTime() {
            return TimeUnit.NANOSECONDS.toMillis(ticksInNanos);
        }
    }
}
