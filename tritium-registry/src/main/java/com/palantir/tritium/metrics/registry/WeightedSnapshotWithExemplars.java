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
package com.palantir.tritium.metrics.registry;

import com.codahale.metrics.Snapshot;
import com.palantir.logsafe.Preconditions;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A weighted {@link Snapshot} with support for storing exemplar metadata for each sample.
 */
final class WeightedSnapshotWithExemplars extends Snapshot implements ExemplarsCapture {

    /**
     * A single sample item with value, weights and optional exemplar metadata.
     */
    static class WeightedSampleWithExemplar {

        private final long value;
        private final double weight;
        private final @Nullable Object exemplarMetadata;

        WeightedSampleWithExemplar(long value, double weight, @Nullable Object exemplarMetadata) {
            this.value = value;
            this.weight = weight;
            this.exemplarMetadata = exemplarMetadata;
        }

        long value() {
            return value;
        }

        double weight() {
            return weight;
        }

        @Nullable
        Object exemplarMetadata() {
            return exemplarMetadata;
        }
    }

    private final Snapshot weightedSnapshot;
    private final ExemplarMetadataProvider<?> exemplarProvider;
    private final List<LongExemplar<Object>> exemplars;

    private WeightedSnapshotWithExemplars(
            ExemplarMetadataProvider<?> provider, List<LongExemplar<Object>> exemplars, Snapshot snapshot) {
        this.exemplars = Collections.unmodifiableList(exemplars);
        this.weightedSnapshot = Preconditions.checkNotNull(snapshot, "snapshot");
        this.exemplarProvider = Preconditions.checkNotNull(provider, "provider");
    }

    /** Collects a reservoir's samples and their exemplars. */
    static final class Collector extends SampleCollector<WeightedSampleWithExemplar> {
        // allocated on the first sample with metadata, so reservoirs without exemplars don't pay for it
        private @Nullable Object @Nullable [] metadata;

        Collector(int expectedSize) {
            super(expectedSize);
        }

        @Override
        long sampleValue(WeightedSampleWithExemplar sample) {
            return sample.value;
        }

        @Override
        double sampleWeight(WeightedSampleWithExemplar sample) {
            return sample.weight;
        }

        @Override
        void collected(int index, WeightedSampleWithExemplar sample) {
            if (sample.exemplarMetadata != null) {
                if (metadata == null) {
                    metadata = new Object[capacity()];
                }
                metadata[index] = sample.exemplarMetadata;
            }
        }

        @Override
        void grown(int capacity) {
            if (metadata != null) {
                metadata = Arrays.copyOf(metadata, capacity);
            }
        }

        /** The snapshot, with the exemplars of the samples it holds in iteration order if there are any. */
        Snapshot snapshot(ExemplarMetadataProvider<?> provider) {
            List<LongExemplar<Object>> exemplars = new ArrayList<>();
            if (metadata != null) {
                for (int i = 0; i < size(); i++) {
                    Object exemplarMetadata = metadata[i];
                    if (exemplarMetadata != null) {
                        exemplars.add(DefaultLongExemplar.of(exemplarMetadata, value(i)));
                    }
                }
            }
            // after reading the values: building the snapshot reuses the collector's arrays
            Snapshot snapshot = snapshot();
            if (exemplars.isEmpty()) {
                return snapshot;
            }
            return new WeightedSnapshotWithExemplars(provider, exemplars, snapshot);
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "for-rollout:ReferenceEquality"
    }) // instance check on the provider guarantees the cast is safe
    public <U> List<LongExemplar<U>> getSamples(ExemplarMetadataProvider<U> provider) {
        if (this.exemplarProvider == provider) {
            return (List<LongExemplar<U>>) (List<?>) exemplars;
        }
        return List.of();
    }

    /* All Snapshot methods are delegated to the weightedSnapshot */

    @Override
    public double getValue(double quantile) {
        return weightedSnapshot.getValue(quantile);
    }

    @Override
    public long[] getValues() {
        return weightedSnapshot.getValues();
    }

    @Override
    public int size() {
        return weightedSnapshot.size();
    }

    @Override
    public long getMax() {
        return weightedSnapshot.getMax();
    }

    @Override
    public double getMean() {
        return weightedSnapshot.getMean();
    }

    @Override
    public long getMin() {
        return weightedSnapshot.getMin();
    }

    @Override
    public double getStdDev() {
        return weightedSnapshot.getStdDev();
    }

    @Override
    public void dump(OutputStream output) {
        weightedSnapshot.dump(output);
    }
}
