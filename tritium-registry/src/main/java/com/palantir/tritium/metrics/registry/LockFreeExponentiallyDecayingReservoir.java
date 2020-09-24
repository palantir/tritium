/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.WeightedSnapshot;
import com.codahale.metrics.WeightedSnapshot.WeightedSample;
import com.google.common.annotations.Beta;
import com.palantir.logsafe.Preconditions;
import java.time.Duration;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * {@link LockFreeExponentiallyDecayingReservoir} is based closely on the codahale
 * <a href="https://github.com/dropwizard/metrics/blob/0313a104bf785e87d7d14a18a82026225304c402/metrics-core/src/main/java/com/codahale/metrics/ExponentiallyDecayingReservoir.java">
 * ExponentiallyDecayingReservoir.java</a>, however it provides looser guarantees while completely avoiding locks.
 *
 * Looser guarantees:
 * <ul>
 *     <li> Updates which occur concurrently with rescaling may be discarded if the orphaned state node is updated after
 *     rescale has replaced it. This condition has a greater probability as the rescale interval is reduced due to the
 *     increased frequency of rescaling. {@link #rescaleThresholdNanos} values below 30 seconds are not recommended.
 *     <li> Given a small rescale threshold, updates may attempt to rescale into a new bucket, but lose the CAS race
 *     and update into a newer bucket than expected. In these cases the measurement weight is reduced accordingly.
 *     <li>In the worst case, all concurrent threads updating the reservoir may attempt to rescale rather than
 *     a single thread holding an exclusive write lock. It's expected that the configuration is set such that
 *     rescaling is substantially less common than updating at peak load. Even so, when size is reasonably small
 *     it can be more efficient to rescale than to park and context switch.
 * </ul>
 *
 * See {@link com.codahale.metrics.ExponentiallyDecayingReservoir} Copyright 2010-2012 Coda Hale and Yammer, Inc.
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
@Beta
public final class LockFreeExponentiallyDecayingReservoir implements Reservoir {

    private static final AtomicReferenceFieldUpdater<LockFreeExponentiallyDecayingReservoir, State> stateUpdater =
            AtomicReferenceFieldUpdater.newUpdater(LockFreeExponentiallyDecayingReservoir.class, State.class, "state");

    private final double alpha;
    private final int size;
    private final long rescaleThresholdNanos;
    private final Clock clock;

    private volatile State state;

    private final class State {
        private final long startTick;
        private final AtomicLong count;
        private final ConcurrentSkipListMap<Double, WeightedSample> values;

        State(long startTick, AtomicLong count, ConcurrentSkipListMap<Double, WeightedSample> values) {
            this.startTick = startTick;
            this.values = values;
            this.count = count;
        }

        private void update(long value, long timestampNanos) {
            double itemWeight = weight(timestampNanos - startTick);
            WeightedSample sample = new WeightedSample(value, itemWeight);
            double priority = itemWeight / ThreadLocalRandom.current().nextDouble();

            long newCount = count.incrementAndGet();
            if (newCount <= size || values.isEmpty()) {
                values.put(priority, sample);
            } else {
                Double first = values.firstKey();
                if (first < priority && values.putIfAbsent(priority, sample) == null) {
                    // Always remove an item
                    while (values.remove(first) == null) {
                        first = values.firstKey();
                    }
                }
            }
        }

        State rescale(long newTick) {
            long durationNanos = newTick - startTick;
            double durationSeconds = durationNanos / 1_000_000_000D;
            double scalingFactor = Math.exp(-alpha * durationSeconds);
            AtomicLong newCount = new AtomicLong();
            ConcurrentSkipListMap<Double, WeightedSample> newValues = new ConcurrentSkipListMap<>();
            if (Double.compare(scalingFactor, 0) != 0) {
                values.forEach((key, sample) -> {
                    double newWeight = sample.weight * scalingFactor;
                    if (Double.compare(newWeight, 0) == 0) {
                        return;
                    }
                    WeightedSample newSample = new WeightedSample(sample.value, newWeight);
                    if (newValues.put(key * scalingFactor, newSample) == null) {
                        newCount.incrementAndGet();
                    }
                });
            }
            return new State(newTick, newCount, newValues);
        }
    }

    private LockFreeExponentiallyDecayingReservoir(int size, double alpha, Duration rescaleThreshold, Clock clock) {
        this.alpha = alpha;
        this.size = size;
        this.clock = clock;
        this.rescaleThresholdNanos = rescaleThreshold.toNanos();
        this.state = new State(clock.getTick(), new AtomicLong(), new ConcurrentSkipListMap<>());
    }

    @Override
    public int size() {
        return (int) Math.min(size, state.count.get());
    }

    @Override
    public void update(long value) {
        long now = clock.getTick();
        rescaleIfNeeded(now).update(value, now);
    }

    private State rescaleIfNeeded(long currentTick) {
        State stateSnapshot = this.state;
        long lastScaleTick = stateSnapshot.startTick;
        if (currentTick - lastScaleTick >= rescaleThresholdNanos) {
            State newState = stateSnapshot.rescale(currentTick);
            if (stateUpdater.compareAndSet(this, stateSnapshot, newState)) {
                // newState successfully installed
                return newState;
            }
            // Otherwise another thread has won the race and we can return the result of a volatile read.
            // It's possible this has taken so long that another update is required, however that's unlikely
            // and no worse than the standard race between a rescale and update.
            return this.state;
        }
        return stateSnapshot;
    }

    @Override
    public Snapshot getSnapshot() {
        State stateSnapshot = rescaleIfNeeded(clock.getTick());
        return new WeightedSnapshot(stateSnapshot.values.values());
    }

    private double weight(long durationNanos) {
        double durationSeconds = durationNanos / 1_000_000_000D;
        return Math.exp(alpha * durationSeconds);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private static final int DEFAULT_SIZE = 1028;
        private static final double DEFAULT_ALPHA = 0.015D;
        private static final Duration DEFAULT_RESCALE_THRESHOLD = Duration.ofHours(1);

        private int size = DEFAULT_SIZE;
        private double alpha = DEFAULT_ALPHA;
        private Duration rescaleThreshold = DEFAULT_RESCALE_THRESHOLD;
        private Clock clock = Clock.defaultClock();

        private Builder() {}

        /**
         * Maximum number of samples to keep in the reservoir. Once this number is reached older samples are
         * replaced (based on weight, with some amount of random jitter).
         */
        public Builder size(int value) {
            this.size = value;
            return this;
        }

        /**
         * Alpha is the exponential decay factor. Higher values bias results more heavily toward newer values.
         */
        public Builder alpha(double value) {
            this.alpha = value;
            return this;
        }

        /**
         * Interval at which this reservoir is rescaled.
         */
        public Builder rescaleThreshold(Duration value) {
            this.rescaleThreshold = Preconditions.checkNotNull(value, "rescaleThreshold is required");
            return this;
        }

        /**
         * Clock instance used for decay.
         */
        public Builder clock(Clock value) {
            this.clock = Preconditions.checkNotNull(value, "clock is required");
            return this;
        }

        public Reservoir build() {
            return new LockFreeExponentiallyDecayingReservoir(size, alpha, rescaleThreshold, clock);
        }
    }
}
