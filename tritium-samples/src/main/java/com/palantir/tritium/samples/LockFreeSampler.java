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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Sampler with throttling that currently only supports a single element - the max of observed values.
 */
public final class LockFreeSampler implements Sampler {

    private final AtomicReference<Sample> sampleRef = new AtomicReference<>(null);

    private final AtomicLong lastSampleTickRef = new AtomicLong(0);

    private final Clock clock;

    private static final long DEFAULT_MAX_RETENTION_PERIOD_MILLIS = 1 * 1_000; // 30s
    private static final long DEFAULT_SAMPLE_INTERVAL_NANOS = 70 * 1_000_000; // 70ms

    private final long maxRetentionPeriodMillis;
    private final long sampleIntervalNanos;
    private final Supplier<Optional<String>> traceSupplier;

    public LockFreeSampler(
            Clock clock,
            long maxRetentionPeriodMillis,
            long sampleIntervalNanos,
            Supplier<Optional<String>> traceSupplier) {
        this.clock = clock;
        this.maxRetentionPeriodMillis = maxRetentionPeriodMillis;
        this.sampleIntervalNanos = sampleIntervalNanos;
        this.traceSupplier = traceSupplier;
    }

    public LockFreeSampler(Supplier<Optional<String>> traceSupplier) {
        this(Clock.defaultClock(), DEFAULT_MAX_RETENTION_PERIOD_MILLIS, DEFAULT_SAMPLE_INTERVAL_NANOS, traceSupplier);
    }

    // Rate limited doObserve.
    @Override
    public void observe(long value) {
        long currentTick = clock.getTick();

        long lastSampleTick = lastSampleTickRef.get();
        if (currentTick - lastSampleTick < sampleIntervalNanos) {
            return;
        }

        if (lastSampleTickRef.compareAndSet(lastSampleTick, currentTick)) {
            doObserve(value, clock.getTime());
        }
    }

    private void doObserve(long value, long currentMillis) {
        Sample existingSample = sampleRef.get();
        if (existingSample == null
                || currentMillis - existingSample.getTimestamp() >= maxRetentionPeriodMillis
                || value > existingSample.getValue()) {
            Optional<String> maybeNewTrace = traceSupplier.get();
            if (maybeNewTrace.isPresent()) {
                sampleRef.set(Sample.of(value, currentMillis, maybeNewTrace.get()));
            }
        }
    }

    @Override
    public List<Sample> collect() {
        Sample sample = sampleRef.get();
        if (sample == null) {
            return Collections.emptyList();
        }
        return List.of(sample);
    }
}
