/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSortedMap;
import java.util.SortedMap;

final class RealMetricName implements MetricName {
    private static final SortedMap<String, String> EMPTY = prehash(ImmutableSortedMap.of());
    private final String safeName;
    private final SortedMap<String, String> safeTags;
    private final int hashCode;

    private RealMetricName(String safeName, SortedMap<String, String> safeTags) {
        this.safeName = safeName;
        this.safeTags = safeTags;
        this.hashCode = computeHashCode();
    }

    private int computeHashCode() {
        int hash = 5381;
        hash += (hash << 5) + safeName().hashCode();
        hash += (hash << 5) + safeTags().hashCode();
        return hash;
    }

    @Override
    public String safeName() {
        return safeName;
    }

    @Override
    public SortedMap<String, String> safeTags() {
        return safeTags;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof MetricName)) {
            return false;
        }
        if (this == other) {
            return true;
        }
        MetricName otherMetric = (MetricName) other;
        return safeName().equals(otherMetric.safeName()) && safeTags().equals(otherMetric.safeTags());
    }

    static MetricName create(String safeName) {
        return new RealMetricName(checkNotNull(safeName, "safeName"), EMPTY);
    }

    static MetricName create(MetricName other) {
        return new RealMetricName(other.safeName(), prehash(other.safeTags()));
    }

    static MetricName create(MetricName other, String extraTagName, String extraTagValue) {
        return new RealMetricName(
                other.safeName(),
                new ExtraEntrySortedMap<>(prehash(other.safeTags()), extraTagName, extraTagValue));
    }

    private static <K, V> SortedMap<K, V> prehash(SortedMap<K, V> map) {
        if (map instanceof PrehashedSortedMap) {
            return map;
        }
        return new PrehashedSortedMap<>(ImmutableSortedMap.copyOfSorted(map));
    }
}
