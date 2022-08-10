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

import com.google.common.collect.ImmutableSortedMap;
import com.palantir.logsafe.Preconditions;
import java.util.SortedMap;
import javax.annotation.Nullable;

final class RealMetricName implements MetricName {

    private final String safeName;
    private final TagMap safeTags;
    private int hashCode;

    RealMetricName(String safeName, TagMap safeTags) {
        this.safeName = Preconditions.checkNotNull(safeName, "safeName is required");
        this.safeTags = Preconditions.checkNotNull(safeTags, "safeTags is required");
    }

    @SuppressWarnings("JdkObsolete") // SortedMap is part of Metrics API
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
    public TagMap safeTags() {
        return safeTags;
    }

    @Override
    public String toString() {
        return "MetricName{safeName=" + safeName + ", safeTags=" + safeTags + '}';
    }

    @Override
    public int hashCode() {
        int memoized = hashCode;
        if (memoized == 0) {
            memoized = computeHashCode();
            hashCode = memoized;
        }
        return memoized;
    }

    @Override
    @SuppressWarnings("JdkObsolete") // SortedMap is part of Metrics API
    public boolean equals(@Nullable Object other) {
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
        return new RealMetricName(safeName, TagMap.EMPTY);
    }

    static MetricName create(MetricName other) {
        return new RealMetricName(other.safeName(), TagMap.of(other.safeTags()));
    }

    static MetricName create(MetricName other, String extraTagName, String extraTagValue) {
        return new RealMetricName(other.safeName(), withEntry(other.safeTags(), extraTagName, extraTagValue));
    }

    public static RealMetricName create(MetricName metricName, TagMap tag) {
        return new RealMetricName(metricName.safeName(), tag.withEntries(metricName.safeTags()));
    }

    private static TagMap withEntry(SortedMap<String, String> tags, String extraTagName, String extraTagValue) {
        if (tags instanceof TagMap) {
            return ((TagMap) tags).withEntry(extraTagName, extraTagValue);
        }
        return withEntryFallback(tags, extraTagName, extraTagValue);
    }

    private static TagMap withEntryFallback(SortedMap<String, String> tags, String extraTagName, String extraTagValue) {
        return TagMap.of(ImmutableSortedMap.<String, String>naturalOrder()
                .putAll(tags)
                .put(extraTagName, extraTagValue)
                .build());
    }
}
