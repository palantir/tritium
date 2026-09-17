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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Snapshot} with support for storing exemplar metadata for each sample.
 */
final class WeightedSnapshotWithExemplars extends Snapshot implements ExemplarsCapture {

    /**
     * A single sample item with value, weights and optional exemplar metadata.
     */
    record WeightedSampleWithExemplar(
            long value, double weight, @Nullable Object exemplarMetadata) {}

    private final Snapshot delegate;
    private final ExemplarMetadataProvider<?> exemplarProvider;
    private final List<LongExemplar<Object>> exemplars;

    private WeightedSnapshotWithExemplars(
            ExemplarMetadataProvider<?> provider, List<LongExemplar<Object>> exemplars, Snapshot snapshot) {
        this.exemplars = Collections.unmodifiableList(exemplars);
        this.delegate = Preconditions.checkNotNull(snapshot, "snapshot");
        this.exemplarProvider = Preconditions.checkNotNull(provider, "provider");
    }

    /**
     * Create a new {@link Snapshot} with the given values.
     *
     * @param provider the provider used to capture exemplar metadata. The {@link ExemplarsCapture} will only return
     * exemplars to clients able to input the same provider instance, to guarantee type-safety.
     * @param values an unordered set of values in the reservoir
     */
    static Snapshot snapshot(ExemplarMetadataProvider<?> provider, Collection<WeightedSampleWithExemplar> values) {
        if (values.isEmpty()) {
            return PrimitiveWeightedSnapshot.EMPTY;
        }
        int size = values.size();
        long[] sampleValues = new long[size];
        double[] sampleWeights = new double[size];
        List<LongExemplar<Object>> exemplars = null;
        int idx = 0;
        for (WeightedSampleWithExemplar v : values) {
            sampleValues[idx] = v.value();
            sampleWeights[idx] = v.weight();
            if (v.exemplarMetadata() != null) {
                if (exemplars == null) {
                    exemplars = new ArrayList<>();
                }
                exemplars.add(DefaultLongExemplar.of(v.exemplarMetadata(), v.value()));
            }
            idx++;
        }

        Snapshot snapshot = new PrimitiveWeightedSnapshot(sampleValues, sampleWeights);
        if (exemplars == null) {
            return snapshot;
        }
        return new WeightedSnapshotWithExemplars(provider, exemplars, snapshot);
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

    /* All Snapshot methods are delegated to the delegate */

    @Override
    public double getValue(double quantile) {
        return delegate.getValue(quantile);
    }

    @Override
    public long[] getValues() {
        return delegate.getValues();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public long getMax() {
        return delegate.getMax();
    }

    @Override
    public double getMean() {
        return delegate.getMean();
    }

    @Override
    public long getMin() {
        return delegate.getMin();
    }

    @Override
    public double getStdDev() {
        return delegate.getStdDev();
    }

    @Override
    public void dump(OutputStream output) {
        delegate.dump(output);
    }
}
