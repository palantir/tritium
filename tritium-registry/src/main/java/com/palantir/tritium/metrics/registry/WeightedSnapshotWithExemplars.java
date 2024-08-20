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
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@link WeightedSnapshot} with support for storing exemplar metadata for each sample.
 */
final class WeightedSnapshotWithExemplars extends WeightedSnapshot implements ExemplarsCapture {

    /**
     * A single sample item with value and its weights for {@link WeightedSnapshotWithExemplars}.
     */
    public static class WeightedSampleWithExemplar {
        public final long value;
        public final double weight;
        public final ExemplarMetadata metadata;

        public WeightedSampleWithExemplar(long value, double weight, ExemplarMetadata metadata) {
            this.value = value;
            this.weight = weight;
            this.metadata = metadata;
        }
    }

    private final List<ExemplarMetadata> exemplarMetadata;

    /**
     * Create a new {@link Snapshot} with the given values.
     *
     * @param values an unordered set of values in the reservoir
     */
    public WeightedSnapshotWithExemplars(Collection<WeightedSampleWithExemplar> values) {
        super(values.stream().map(v -> new WeightedSample(v.value, v.weight)).collect(Collectors.toList()));
        exemplarMetadata = values.stream().map(v -> v.metadata).collect(Collectors.toList());
    }

    @Override
    public List<ExemplarMetadata> getSamples() {
        return exemplarMetadata;
    }
}
