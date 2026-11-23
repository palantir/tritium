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

import java.util.List;
import java.util.Optional;

/**
 * Exposes a cumulative exponential histogram from a {@link com.codahale.metrics.Snapshot}.
 * Total count is the sum of the positive, negative, and zero bucket counts.
 * The sum covers the same observations as the captured bucket counts, subject to floating-point rounding.
 * Buckets are captured independently, so the snapshot need not represent a single instant.
 */
public interface ExponentialHistogramCapture {
    /** Sum of all observations, in the original metric unit. */
    double sum();

    /** Bucket base is {@code 2^(2^-scale)}. */
    int scale();

    long zeroCount();

    double zeroThreshold();

    Optional<Buckets> positive();

    Optional<Buckets> negative();

    /**
     *  A contiguous range of bucket counts. Entry {@code bucketCounts[j]} is the count for bucket
     *  index {@code offset + j}; zero entries represent gaps between occupied buckets.
     *  Bucket index {@code i} covers magnitudes in {@code (base^i, base^(i+1)]},
     *  using the OpenTelemetry exponential histogram indexing convention.
     */
    record Buckets(int offset, List<Long> bucketCounts) {
        public Buckets {
            bucketCounts = List.copyOf(bucketCounts);
        }
    }
}
