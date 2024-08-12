/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.samples;

import com.codahale.metrics.Clock;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Exemplar sampler with throttling that currently only supports a single exemplar - the max of the sampled values.
 */
public final class LockFreeSampler implements Sampler {

    @Nullable
    private Sample sample;

    private volatile long lastSampleTick = 0;

    private final Clock clock;

    private static final long DEFAULT_MAX_RETENTION_PERIOD_MILLIS = 1 * 1_000; // 30s
    private static final long DEFAULT_SAMPLE_INTERVAL_NANOS = 70 * 1_000_000; // 70ms

    private final long maxRetentionPeriodMillis;
    private final long sampleIntervalNanos;
    private final Supplier<String> traceSupplier;

    public LockFreeSampler(
            Clock clock, long maxRetentionPeriodMillis, long sampleIntervalNanos, Supplier<String> traceSupplier) {
        this.clock = clock;
        this.maxRetentionPeriodMillis = maxRetentionPeriodMillis;
        this.sampleIntervalNanos = sampleIntervalNanos;
        this.traceSupplier = traceSupplier;
    }

    public LockFreeSampler(Supplier<String> traceSupplier) {
        this(Clock.defaultClock(), DEFAULT_MAX_RETENTION_PERIOD_MILLIS, DEFAULT_SAMPLE_INTERVAL_NANOS, traceSupplier);
    }

    // Rate limited doObserve.
    @Override
    public synchronized void observe(long value) {
        long currentTick = clock.getTick();

        if (currentTick - lastSampleTick < sampleIntervalNanos) {
            return;
        }

        doObserve(value, clock.getTime());
        lastSampleTick = currentTick;
    }

    private void doObserve(long value, long currentMillis) {
        if (sample == null
                || currentMillis - sample.getTimestamp() >= maxRetentionPeriodMillis
                || value > sample.getTimestamp()) {
            sample = Sample.of(value, currentMillis, traceSupplier.get());
        }
    }

    @Override
    public synchronized List<Sample> collect() {
        if (sample == null) {
            return Collections.emptyList();
        }
        return List.of(sample);
    }
}
