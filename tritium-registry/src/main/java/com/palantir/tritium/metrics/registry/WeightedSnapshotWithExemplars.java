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
import com.google.common.collect.ImmutableList;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A {@link WeightedSnapshot} with support for storing exemplar metadata for each sample.
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

    private final WeightedSnapshot weightedSnapshot;
    private final ExemplarMetadataProvider<?> exemplarProvider;
    private final List<LongExemplar<Object>> exemplars;

    /**
     * Create a new {@link Snapshot} with the given values.
     *
     * @param provider the provider used to capture exemplar metadata. The {@link ExemplarsCapture} will only return
     * exemplars to clients able to input the same provider instance, to guarantee type-safety.
     * @param values an unordered set of values in the reservoir
     */
    WeightedSnapshotWithExemplars(ExemplarMetadataProvider<?> provider, Collection<WeightedSampleWithExemplar> values) {
        ImmutableList.Builder<WeightedSample> weightedSamplesBuilder =
                ImmutableList.builderWithExpectedSize(values.size());
        ImmutableList.Builder<LongExemplar<Object>> exemplarsBuilder = null;

        for (WeightedSampleWithExemplar v : values) {
            weightedSamplesBuilder.add(new WeightedSample(v.value, v.weight));
            if (v.exemplarMetadata != null) {
                if (exemplarsBuilder == null) {
                    exemplarsBuilder = ImmutableList.builder();
                }
                exemplarsBuilder.add(DefaultLongExemplar.of(v.exemplarMetadata, v.value));
            }
        }
        this.exemplars = (exemplarsBuilder == null) ? ImmutableList.of() : exemplarsBuilder.build();

        this.weightedSnapshot = new WeightedSnapshot(weightedSamplesBuilder.build());
        this.exemplarProvider = provider;
    }

    /**
     * Returns the exemplars captured from the given provider. If the provider is different from the
     * one used to create this snapshot, an empty list is returned.
     * Only exemplars for which the provider returned non-null metadata are returned.
     */
    @Override
    @SuppressWarnings("unchecked") // instance check on the provider guarantees the cast is safe
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
