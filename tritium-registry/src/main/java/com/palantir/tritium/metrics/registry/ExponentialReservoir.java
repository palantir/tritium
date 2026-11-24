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

import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Snapshot;
import com.palantir.logsafe.Preconditions;
import com.palantir.tritium.metrics.registry.ExponentialHistogramCapture.Buckets;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Cumulative exponential distribution for {@link com.codahale.metrics.Histogram}.
 * Observations may be any {@code long} value in the metric's unit.
 * Only zero belongs to the zero bucket. Buckets never decay or
 * reset. Create a new reservoir to start a new cumulative distribution.
 * Summary statistics default to a {@link LockFreeExponentiallyDecayingReservoir}.
 *
 * <p>The total number of observations can be derived from the captured bucket counts,
 * including the zero bucket. Updates and snapshots share a lock, capturing sum and bucket counts
 * together. Snapshot collection blocks updates.
 *
 * <p>The scale parameter dictates the granularity of the histogram buckets. At scale 0,
 * bucket boundaries are powers of two. Each increase in scale divides each bucket into
 * two narrower buckets, doubling storage requirements. For example, scale 3 provides
 * eight buckets per doubling of magnitude, with adjacent boundaries about 9% apart.
 * Negative scales produce wider buckets.
 */
public final class ExponentialReservoir implements Reservoir {
    private static final ConcurrentMap<Integer, long[]> UPPER_BOUNDS = new ConcurrentHashMap<>();

    private final int scale;
    private final long[] bucketUpperBounds;
    private final Reservoir summaries;
    private final Map<Integer, Bucket> positiveBuckets = new HashMap<>();
    private final Map<Integer, Bucket> negativeBuckets = new HashMap<>();
    private long zeroCount;
    private double sum;

    /** Creates a reservoir at scale 3, with eight buckets per doubling of magnitude. */
    public ExponentialReservoir() {
        this(3);
    }

    public ExponentialReservoir(int scale) {
        this(scale, LockFreeExponentiallyDecayingReservoir.builder().build());
    }

    /**
     * Uses the supplied reservoir for summary statistics and exemplars. The delegate should be empty
     * and accessed only through this reservoir.
     */
    public ExponentialReservoir(int scale, Reservoir delegate) {
        Preconditions.checkArgument(scale >= -4 && scale <= 8, "Scale must be between -4 and 8");
        this.scale = scale;
        this.summaries = Preconditions.checkNotNull(delegate, "delegate");
        bucketUpperBounds = UPPER_BOUNDS.computeIfAbsent(scale, ExponentialReservoir::upperBounds);
    }

    @Override
    public synchronized int size() {
        return summaries.size();
    }

    @Override
    public synchronized void update(long value) {
        if (value == 0) {
            zeroCount++;
        } else {
            Map<Integer, Bucket> buckets = value > 0 ? positiveBuckets : negativeBuckets;
            buckets.computeIfAbsent(bucketPosition(value), _index -> new Bucket()).count++;
        }
        sum += value;
        summaries.update(value);
    }

    @Override
    public synchronized Snapshot getSnapshot() {
        return new ExponentialSnapshot(
                summaries.getSnapshot(), sum, scale, zeroCount, capture(positiveBuckets), capture(negativeBuckets));
    }

    private int bucketPosition(long value) {
        // Its magnitude is 2^63, which cannot be represented as a positive long.
        if (value == Long.MIN_VALUE) {
            return bucketUpperBounds.length - 1;
        }

        long magnitude = Math.abs(value);
        // Subtract one to keep exact powers of two in the upper-inclusive bucket.
        int exponent = Long.SIZE - Long.numberOfLeadingZeros(magnitude - 1);
        if (scale <= 0) {
            int exponentStep = 1 << -scale;
            return (exponent + exponentStep - 1) / exponentStep;
        }
        if (exponent == 0) {
            return 0;
        }
        int low = ((exponent - 1) << scale) + 1;
        int high = exponent << scale;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (magnitude <= bucketUpperBounds[middle]) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    private static long[] upperBounds(int scale) {
        int size = (int) Math.ceil(Math.scalb(63.0, scale)) + 1;
        long[] bounds = new long[size];
        for (int position = 0; position < size - 1; position++) {
            bounds[position] = integerUpperBound(position, scale);
        }
        // The final boundary is at least 2^63, beyond the positive long range.
        bounds[size - 1] = Long.MAX_VALUE;
        return bounds;
    }

    private static long integerUpperBound(int position, int scale) {
        // The boundary is 2^(position / 2^scale). At negative scales, the exponent is multiplied
        // by a power of two; at scale zero, it is unchanged.
        if (scale <= 0) {
            return 1L << (position << -scale);
        }

        // Dividing the exponent by 2^scale is equivalent to taking 'scale' square roots of 2^position.
        // BigInteger.sqrt() rounds down exactly. Repeated rounding is safe because
        // floor(sqrt(floor(x))) == floor(sqrt(x)), so this gives the exact integer floor of the boundary.
        BigInteger bound = BigInteger.ONE.shiftLeft(position);
        for (int root = 0; root < scale; root++) {
            bound = bound.sqrt();
        }
        return bound.longValueExact();
    }

    private static Optional<Buckets> capture(Map<Integer, Bucket> buckets) {
        int firstPosition = Integer.MAX_VALUE;
        int lastPosition = -1;
        for (int position : buckets.keySet()) {
            firstPosition = Math.min(firstPosition, position);
            lastPosition = Math.max(lastPosition, position);
        }
        if (lastPosition < firstPosition) {
            return Optional.empty();
        }

        long[] counts = new long[lastPosition - firstPosition + 1];
        for (Map.Entry<Integer, Bucket> entry : buckets.entrySet()) {
            counts[entry.getKey() - firstPosition] = entry.getValue().count;
        }
        return Optional.of(new ExponentialSnapshot.ArrayBuckets(firstPosition - 1, counts));
    }

    private static final class Bucket {
        private long count;
    }
}
