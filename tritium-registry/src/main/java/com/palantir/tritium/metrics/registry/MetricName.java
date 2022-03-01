/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.Safe;
import java.util.Map;
import java.util.SortedMap;
import javax.annotation.Nullable;

public interface MetricName {

    /**
     * General/abstract measure (e.g. server.response-time).
     *
     * <p>Names must be {@link Safe} to log.
     */
    String safeName();

    /**
     * Metadata/coordinates for where a particular measure came from. Used for filtering and grouping.
     *
     * <p>All tags and keys must be {@link Safe} to log.
     */
    SortedMap<String, String> safeTags();

    static Builder builder() {
        return new Builder();
    }

    final class Builder {
        @Nullable
        private String safeName;

        private TagMap tagMap = TagMap.EMPTY;

        private Builder() {}

        @CanIgnoreReturnValue
        public Builder from(MetricName instance) {
            Preconditions.checkNotNull(instance, "instance");
            safeName(instance.safeName());
            putAllSafeTags(instance.safeTags());
            return this;
        }

        @CanIgnoreReturnValue
        public Builder safeName(String value) {
            this.safeName = Preconditions.checkNotNull(value, "safeName");
            return this;
        }

        @CanIgnoreReturnValue
        public Builder putSafeTags(String key, String value) {
            Preconditions.checkNotNull(key, "safeTagName");
            Preconditions.checkNotNull(value, "safeTagValue");
            tagMap = tagMap.withEntry(key, value);
            return this;
        }

        @CanIgnoreReturnValue
        public Builder putSafeTags(Map.Entry<String, ? extends String> entry) {
            Preconditions.checkNotNull(entry, "entry");
            tagMap = tagMap.withEntry(entry.getKey(), entry.getValue());
            return this;
        }

        @SuppressWarnings("unchecked")
        @CanIgnoreReturnValue
        public Builder safeTags(Map<String, ? extends String> entries) {
            Preconditions.checkNotNull(entries, "entries");
            tagMap = TagMap.of((Map<String, String>) entries);
            return this;
        }

        @SuppressWarnings("unchecked")
        @CanIgnoreReturnValue
        public Builder putAllSafeTags(Map<String, ? extends String> entries) {
            Preconditions.checkNotNull(entries, "entries");
            if (!entries.isEmpty()) {
                tagMap = tagMap.isEmpty()
                        ? TagMap.of((Map<String, String>) entries)
                        : TagMap.of(ImmutableMap.<String, String>builderWithExpectedSize(tagMap.size() + entries.size())
                                .putAll(tagMap)
                                .putAll(entries)
                                .build());
            }
            return this;
        }

        public MetricName build() {
            return new RealMetricName(Preconditions.checkNotNull(safeName, "safeName is required"), tagMap);
        }
    }
}
