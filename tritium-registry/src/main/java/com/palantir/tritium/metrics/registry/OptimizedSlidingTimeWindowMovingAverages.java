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

import com.codahale.metrics.Clock;
import com.codahale.metrics.ExponentialMovingAverages;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MovingAverages;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

/**
 * Optimized fork of {@link com.codahale.metrics.SlidingTimeWindowMovingAverages}, see
 * <a href="https://github.com/dropwizard/metrics/pull/4936">upstream PR 4936</a>.
 * <p>
 * A triple of simple moving average rates (one, five and fifteen minutes rates) as needed by {@link Meter}.
 * <p>
 * The averages are unweighted, i.e. they include strictly only the events in the
 * sliding time window, every event having the same weight. Unlike the
 * the more widely used {@link ExponentialMovingAverages} implementation,
 * with this class the moving average rate drops immediately to zero if the last
 * marked event is older than the time window.
 * <p>
 * A {@link Meter} with {@link com.codahale.metrics.SlidingTimeWindowMovingAverages} works similarly to
 * a {@link Histogram} with an {@link SlidingTimeWindowArrayReservoir}, but as a Meter
 * needs to keep track only of the count of events (not the events itself), the memory
 * overhead is much smaller. SlidingTimeWindowMovingAverages uses buckets with just one
 * counter to accumulate the number of events (one bucket per seconds, giving 900 buckets
 * for the 15 minutes time window).
 */
public final class OptimizedSlidingTimeWindowMovingAverages implements MovingAverages {

    private static final long TIME_WINDOW_DURATION_MINUTES = 15;
    private static final long TICK_INTERVAL = TimeUnit.SECONDS.toNanos(1);
    private static final Duration TIME_WINDOW_DURATION = Duration.ofMinutes(TIME_WINDOW_DURATION_MINUTES);

    // package private for the benefit of the unit test
    static final int NUMBER_OF_BUCKETS = (int) (TIME_WINDOW_DURATION.toNanos() / TICK_INTERVAL);

    private final AtomicLong lastTick;
    private final Clock clock;

    /**
     * One counter per time bucket/slot (i.e. per second, see TICK_INTERVAL) for the entire
     * time window (i.e. 15 minutes, see TIME_WINDOW_DURATION_MINUTES).
     */
    private final List<LongAdder> buckets;

    /**
     * Index into buckets, pointing at the bucket containing the oldest counts.
     */
    private int oldestBucketIndex;

    /**
     * Index into buckets, pointing at the bucket with the count for the current time (tick).
     */
    private int currentBucketIndex;

    /**
     * Instant at creation time of the time window. Used to calculate the currentBucketIndex
     * for the instant of a given tick (instant modulo time window duration).
     */
    private final Instant bucketBaseTime;

    /**
     * Instant of the bucket with index oldestBucketIndex.
     */
    private Instant oldestBucketTime;

    /**
     * Creates a new {@link OptimizedSlidingTimeWindowMovingAverages}.
     */
    OptimizedSlidingTimeWindowMovingAverages() {
        this(Clock.defaultClock());
    }

    /**
     * Creates a new {@link OptimizedSlidingTimeWindowMovingAverages}.
     *
     * @param clock the clock to use for the meter ticks
     */
    OptimizedSlidingTimeWindowMovingAverages(Clock clock) {
        this.clock = clock;
        final long startTime = clock.getTick();
        this.lastTick = new AtomicLong(startTime);

        this.buckets = IntStream.range(0, NUMBER_OF_BUCKETS)
                .mapToObj(_i -> new LongAdder())
                .toList();
        this.bucketBaseTime = Instant.ofEpochSecond(0L, startTime);
        this.oldestBucketTime = bucketBaseTime;
        this.oldestBucketIndex = 0;
        this.currentBucketIndex = 0;
    }

    @Override
    public void update(long value) {
        buckets.get(currentBucketIndex).add(value);
    }

    @Override
    public void tickIfNecessary() {
        final long oldTick = lastTick.get();
        final long newTick = clock.getTick();
        final long age = newTick - oldTick;
        if (age >= TICK_INTERVAL) {
            // - the newTick doesn't fall into the same slot as the oldTick anymore
            // - newLastTick is the lower border time of the new currentBucketIndex slot
            final long newLastTick = newTick - age % TICK_INTERVAL;
            if (lastTick.compareAndSet(oldTick, newLastTick)) {
                Instant currentInstant = Instant.ofEpochSecond(0L, newLastTick);
                currentBucketIndex = normalizeIndex(calculateIndexOfTick(currentInstant));
                cleanOldBuckets(currentInstant);
            }
        }
    }

    @VisibleForTesting
    Instant oldestBucketTime() {
        return oldestBucketTime;
    }

    @Override
    public double getM15Rate() {
        return getMinuteRate(15);
    }

    @Override
    public double getM5Rate() {
        return getMinuteRate(5);
    }

    @Override
    public double getM1Rate() {
        return getMinuteRate(1);
    }

    private double getMinuteRate(int minutes) {
        Instant now = Instant.ofEpochSecond(0L, lastTick.get());
        return sumBuckets(now, (int) (TimeUnit.MINUTES.toNanos(minutes) / TICK_INTERVAL));
    }

    @VisibleForTesting
    int calculateIndexOfTick(Instant tickTime) {
        return (int) (Duration.between(bucketBaseTime, tickTime).toNanos() / TICK_INTERVAL);
    }

    @VisibleForTesting
    static int normalizeIndex(int index) {
        int mod = index % NUMBER_OF_BUCKETS;
        return mod >= 0 ? mod : mod + NUMBER_OF_BUCKETS;
    }

    private void cleanOldBuckets(Instant currentTick) {
        int newOldestIndex;
        Instant oldestStillNeededTime = currentTick.minus(TIME_WINDOW_DURATION).plusNanos(TICK_INTERVAL);
        Instant youngestNotInWindow = oldestBucketTime.plus(TIME_WINDOW_DURATION);
        if (oldestStillNeededTime.isAfter(youngestNotInWindow)) {
            // there was no update() call for more than two whole TIME_WINDOW_DURATION
            newOldestIndex = oldestBucketIndex;
            oldestBucketTime = currentTick;
        } else if (oldestStillNeededTime.isAfter(oldestBucketTime)) {
            newOldestIndex = normalizeIndex(calculateIndexOfTick(oldestStillNeededTime));
            oldestBucketTime = oldestStillNeededTime;
        } else {
            return;
        }

        cleanBucketRange(oldestBucketIndex, newOldestIndex);
        oldestBucketIndex = newOldestIndex;
    }

    private void cleanBucketRange(int fromIndex, int toIndex) {
        if (fromIndex < toIndex) {
            for (int i = fromIndex; i < toIndex; i++) {
                buckets.get(i).reset();
            }
        } else {
            for (int i = fromIndex; i < NUMBER_OF_BUCKETS; i++) {
                buckets.get(i).reset();
            }
            for (int i = 0; i < toIndex; i++) {
                buckets.get(i).reset();
            }
        }
    }

    private long sumBuckets(Instant toTime, int numberOfBuckets) {

        // increment toIndex to include the current bucket into the sum
        int toIndex = normalizeIndex(calculateIndexOfTick(toTime) + 1);
        int fromIndex = normalizeIndex(toIndex - numberOfBuckets);

        long sum = 0;
        if (fromIndex < toIndex) {
            for (int i = fromIndex; i < toIndex; i++) {
                sum += buckets.get(i).longValue();
            }
        } else {
            for (int i = fromIndex; i < NUMBER_OF_BUCKETS; i++) {
                sum += buckets.get(i).longValue();
            }
            for (int i = 0; i < toIndex; i++) {
                sum += buckets.get(i).longValue();
            }
        }
        return sum;
    }
}
