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
import com.codahale.metrics.WeightedSnapshot;
import com.codahale.metrics.WeightedSnapshot.WeightedSample;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A {@link WeightedSnapshot} with support for storing exemplar metadata for each sample.
 */
final class WeightedSnapshotWithExemplars extends Snapshot implements ExemplarsCapture {

    /**
     * A single sample item with value and its weights for {@link WeightedSnapshotWithExemplars}.
     */
    public static class WeightedSampleWithExemplar {

        public final long value;
        public final double weight;
        public final Optional<?> metadata;

        WeightedSampleWithExemplar(long value, double weight, Optional<?> metadata) {
            this.value = value;
            this.weight = weight;
            this.metadata = metadata;
        }
    }

    private final WeightedSnapshot weightedSnapshot;
    private final ExemplarMetadataProvider<?> exemplarProvider;
    private final List<Object> exemplarMetadatas;

    /**
     * Create a new {@link Snapshot} with the given values.
     *
     * @param values an unordered set of values in the reservoir
     */
    WeightedSnapshotWithExemplars(ExemplarMetadataProvider<?> provider, Collection<WeightedSampleWithExemplar> values) {
        final List<WeightedSample> weightedSamples = new ArrayList<>();
        this.exemplarMetadatas = new ArrayList<>();

        values.forEach(v -> {
            weightedSamples.add(new WeightedSample(v.value, v.weight));
            if (v.metadata.isPresent()) {
                exemplarMetadatas.add(v.metadata);
            }
        });

        this.weightedSnapshot = new WeightedSnapshot(weightedSamples);
        this.exemplarProvider = provider;
    }

    @Override
    @SuppressWarnings("unchecked") // instance check on the provider guarantees the cast is safe
    public <U> List<U> getSamples(ExemplarMetadataProvider<U> provider) {
        if (this.exemplarProvider == provider) {
            return (List<U>) exemplarMetadatas;
        }
        return Collections.emptyList();
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
