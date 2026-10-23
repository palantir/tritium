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

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;

/**
 * Removes samples from an exponentially decaying reservoir whose weight is negligible next to its newest sample.
 *
 * <p>Without this, a sample only leaves the reservoir when the reservoir is full or when its weight underflows to
 * zero on a rescale, which takes about 15 hours with the default alpha of 0.015. A reservoir updated once a minute
 * therefore holds about 900 samples even though only the last few minutes of them influence its quantiles.
 *
 * <p>A sample's share of any quantile is proportional to its weight. Samples weighing less than
 * {@link #RELATIVE_WEIGHT_THRESHOLD} of the newest sample (about 15 minutes older with the default alpha) can be
 * removed while moving quantiles by at most {@code size * RELATIVE_WEIGHT_THRESHOLD} of the newest sample's weight,
 * about 0.1% for the default size of 1028. Comparing against the newest sample rather than the clock means an idle
 * reservoir keeps its last samples, and so keeps reporting the same quantiles.
 */
final class NegligibleSamples {

    static final double RELATIVE_WEIGHT_THRESHOLD = 1e-6;

    private NegligibleSamples() {}

    /**
     * Removes samples weighing less than {@code threshold} from {@code values}, returning how many were removed.
     * Entries are removed only if they still map to the sample that was examined, so concurrent updates and evictions
     * are not double-counted.
     */
    static <S> int evictBelow(ConcurrentSkipListMap<Double, S> values, ToDoubleFunction<S> weight, double threshold) {
        // forEach walks the nodes directly, where entrySet() would allocate an entry per sample.
        Evictor<S> evictor = new Evictor<>(values, weight, threshold);
        values.forEach(evictor);
        return evictor.removed;
    }

    private static final class Evictor<S> implements BiConsumer<Double, S> {
        private final ConcurrentSkipListMap<Double, S> values;
        private final ToDoubleFunction<S> weight;
        private final double threshold;
        private int removed;

        Evictor(ConcurrentSkipListMap<Double, S> values, ToDoubleFunction<S> weight, double threshold) {
            this.values = values;
            this.weight = weight;
            this.threshold = threshold;
        }

        @Override
        public void accept(Double priority, S sample) {
            if (weight.applyAsDouble(sample) < threshold && values.remove(priority, sample)) {
                removed++;
            }
        }
    }
}
