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
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.tritium.metrics.registry.WeightedSnapshotWithExemplars.WeightedSampleWithExemplar;
import java.time.Duration;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * <p>
 * {@link LockFreeExponentiallyDecayingReservoirWithExemplars} is based on the codahale
 * <a href="https://github.com/dropwizard/metrics/blob/1418393d0476d3eb6147bc567797e5d2a71d0b83/metrics-core/src/main/java/com/codahale/metrics/LockFreeExponentiallyDecayingReservoir.java">LockFreeExponentiallyDecayingReservoir.java</a>
 * and adds support for capturing exemplar metadata, maintaining basically every other aspect of its original design.
 * <p>
 * Exemplar metadata is captured via the provided {@link ExemplarMetadataProvider}, which is invoked every time a
 * sample is selected to be added to the reservoir (ie for certain occurrences of {@link Reservoir#update(long)}).
 * <p>
 * The captured metadata is then exposed in the {@link ExemplarsCapture} returned by {@link Reservoir#getSnapshot()}:
 * even though the signature of {@link Reservoir#getSnapshot()} is unchanged, the returned {@link Snapshot} is actually
 * a concrete implementation of {@link Snapshot} that is also guaranteed to implement {@link ExemplarsCapture}.
 * <p>
 * See {@link com.codahale.metrics.ExponentiallyDecayingReservoir} Copyright 2010-2012 Coda Hale and Yammer, Inc.
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
public final class LockFreeExponentiallyDecayingReservoirWithExemplars implements Reservoir {

    private static final double SECONDS_PER_NANO = .000_000_001D;
    private static final AtomicReferenceFieldUpdater<LockFreeExponentiallyDecayingReservoirWithExemplars, State>
            stateUpdater = AtomicReferenceFieldUpdater.newUpdater(
                    LockFreeExponentiallyDecayingReservoirWithExemplars.class, State.class, "state");

    private final int size;
    private final long rescaleThresholdNanos;
    private final Clock clock;
    private volatile State state;

    private static final class State {

        private static final AtomicIntegerFieldUpdater<State> countUpdater =
                AtomicIntegerFieldUpdater.newUpdater(State.class, "count");

        private final double alphaNanos;
        private final int size;
        private final long startTick;
        // Count is updated after samples are successfully added to the map.
        private final ConcurrentSkipListMap<Double, WeightedSampleWithExemplar> values;

        private volatile int count;

        private final ExemplarMetadataProvider<?> exemplarMetadataProvider;

        State(
                double alphaNanos,
                int size,
                long startTick,
                int count,
                ConcurrentSkipListMap<Double, WeightedSampleWithExemplar> values,
                ExemplarMetadataProvider<?> exemplarMetadataProvider) {
            this.alphaNanos = alphaNanos;
            this.size = size;
            this.startTick = startTick;
            this.values = values;
            this.count = count;
            this.exemplarMetadataProvider = exemplarMetadataProvider;
        }

        private void update(long value, long timestampNanos) {
            double itemWeight = weight(timestampNanos - startTick);
            double priority = itemWeight / ThreadLocalRandom.current().nextDouble();
            boolean mapIsFull = count >= size;
            if (!mapIsFull || values.firstKey() < priority) {
                addSample(priority, value, itemWeight, mapIsFull, exemplarMetadataProvider.collect());
            }
        }

        private void addSample(
                double priority,
                long value,
                double itemWeight,
                boolean bypassIncrement,
                @Nullable Object exemplarMetadata) {
            if (values.putIfAbsent(priority, new WeightedSampleWithExemplar(value, itemWeight, exemplarMetadata))
                            == null
                    && (bypassIncrement || countUpdater.incrementAndGet(this) > size)) {
                values.pollFirstEntry();
            }
        }

        /* "A common feature of the above techniques—indeed, the key technique that
         * allows us to track the decayed weights efficiently—is that they maintain
         * counts and other quantities based on g(ti − L), and only scale by g(t − L)
         * at query time. But while g(ti −L)/g(t−L) is guaranteed to lie between zero
         * and one, the intermediate values of g(ti − L) could become very large. For
         * polynomial functions, these values should not grow too large, and should be
         * effectively represented in practice by floating point values without loss of
         * precision. For exponential functions, these values could grow quite large as
         * new values of (ti − L) become large, and potentially exceed the capacity of
         * common floating point types. However, since the values stored by the
         * algorithms are linear combinations of g values (scaled sums), they can be
         * rescaled relative to a new landmark. That is, by the analysis of exponential
         * decay in Section III-A, the choice of L does not affect the final result. We
         * can therefore multiply each value based on L by a factor of exp(−α(L′ − L)),
         * and obtain the correct value as if we had instead computed relative to a new
         * landmark L′ (and then use this new L′ at query time). This can be done with
         * a linear pass over whatever data structure is being used."
         */
        State rescale(long newTick) {
            long durationNanos = newTick - startTick;
            double scalingFactor = Math.exp(-alphaNanos * durationNanos);
            int newCount = 0;
            ConcurrentSkipListMap<Double, WeightedSampleWithExemplar> newValues = new ConcurrentSkipListMap<>();
            if (Double.compare(scalingFactor, 0) != 0) {
                RescalingConsumer consumer = new RescalingConsumer(scalingFactor, newValues);
                values.forEach(consumer);
                // make sure the counter is in sync with the number of stored samples.
                newCount = consumer.count;
            }
            // It's possible that more values were added while the map was scanned, those with the
            // minimum priorities are removed.
            while (newCount > size) {
                Preconditions.checkNotNull(newValues.pollFirstEntry(), "Expected an entry");
                newCount--;
            }
            return new State(alphaNanos, size, newTick, newCount, newValues, exemplarMetadataProvider);
        }

        private double weight(long durationNanos) {
            return Math.exp(alphaNanos * durationNanos);
        }
    }

    private static final class RescalingConsumer implements BiConsumer<Double, WeightedSampleWithExemplar> {
        private final double scalingFactor;
        private final ConcurrentSkipListMap<Double, WeightedSampleWithExemplar> values;
        private int count;

        RescalingConsumer(double scalingFactor, ConcurrentSkipListMap<Double, WeightedSampleWithExemplar> values) {
            this.scalingFactor = scalingFactor;
            this.values = values;
        }

        @Override
        public void accept(Double priority, WeightedSampleWithExemplar sample) {
            double newWeight = sample.weight() * scalingFactor;
            if (Double.compare(newWeight, 0) == 0) {
                return;
            }
            WeightedSampleWithExemplar newSample =
                    new WeightedSampleWithExemplar(sample.value(), newWeight, sample.exemplarMetadata());
            if (values.put(priority * scalingFactor, newSample) == null) {
                count++;
            }
        }
    }

    private LockFreeExponentiallyDecayingReservoirWithExemplars(
            int size,
            double alpha,
            Duration rescaleThreshold,
            Clock clock,
            ExemplarMetadataProvider<?> exemplarMetadataProvider) {
        // Scale alpha to nanoseconds
        double alphaNanos = alpha * SECONDS_PER_NANO;
        this.size = size;
        this.clock = clock;
        this.rescaleThresholdNanos = rescaleThreshold.toNanos();
        this.state = new State(
                alphaNanos, size, clock.getTick(), 0, new ConcurrentSkipListMap<>(), exemplarMetadataProvider);
    }

    @Override
    public int size() {
        return Math.min(size, state.count);
    }

    @Override
    public void update(long value) {
        long now = clock.getTick();
        rescaleIfNeeded(now).update(value, now);
    }

    private State rescaleIfNeeded(long currentTick) {
        // This method is optimized for size so the check may be quickly inlined.
        // Rescaling occurs substantially less frequently than the check itself.
        State stateSnapshot = this.state;
        if (currentTick - stateSnapshot.startTick >= rescaleThresholdNanos) {
            return doRescale(currentTick, stateSnapshot);
        }
        return stateSnapshot;
    }

    private State doRescale(long currentTick, State stateSnapshot) {
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

    @Override
    public Snapshot getSnapshot() {
        State stateSnapshot = rescaleIfNeeded(clock.getTick());
        return new WeightedSnapshotWithExemplars(stateSnapshot.exemplarMetadataProvider, stateSnapshot.values.values());
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * By default this uses a size of 1028 elements, which offers a 99.9%
     * confidence level with a 5% margin of error assuming a normal distribution, and an alpha
     * factor of 0.015, which heavily biases the reservoir to the past 5 minutes of measurements.
     */
    public static final class Builder {
        private static final int DEFAULT_SIZE = 1028;
        private static final double DEFAULT_ALPHA = 0.015D;
        private static final Duration DEFAULT_RESCALE_THRESHOLD = Duration.ofHours(1);

        private int size = DEFAULT_SIZE;
        private double alpha = DEFAULT_ALPHA;
        private Duration rescaleThreshold = DEFAULT_RESCALE_THRESHOLD;
        private Clock clock = Clock.defaultClock();
        private ExemplarMetadataProvider<?> exemplarMetadataProvider = () -> null;

        private Builder() {}

        /**
         * {@link ExemplarMetadataProvider} to provide exemplar metadata for each sample. It will be invoked
         * every time a call to {@link Reservoir#update(long)} actually results in the sample being added to the
         * reservoir.
         */
        public Builder exemplarProvider(ExemplarMetadataProvider<?> value) {
            this.exemplarMetadataProvider = Preconditions.checkNotNull(value, "exemplarMetadataProvider is required");
            return this;
        }

        /**
         * Maximum number of samples to keep in the reservoir. Once this number is reached older samples are
         * replaced (based on weight, with some amount of random jitter).
         */
        public Builder size(int value) {
            if (value <= 0) {
                throw new SafeIllegalArgumentException(
                        "LockFreeExponentiallyDecayingReservoirWithExemplars size must be positive",
                        SafeArg.of("size", value));
            }
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
            return new LockFreeExponentiallyDecayingReservoirWithExemplars(
                    size, alpha, rescaleThreshold, clock, exemplarMetadataProvider);
        }
    }
}
