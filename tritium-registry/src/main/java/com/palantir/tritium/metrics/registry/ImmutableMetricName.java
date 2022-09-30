/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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
import javax.annotation.Nullable;

/**
 * ABI backcompat stub for code which was previously generated with immutables.
 *
 * This implementation is written by hand to optimize creation performance beyond what can be expected from immutables,
 * for instance using {@link TagMap} due to an expected small number of tag entries.
 */
final class ImmutableMetricName {
    private ImmutableMetricName() {}

    public abstract static class Builder {
        @Nullable
        private String safeName;

        private TagMap tagMap = TagMap.EMPTY;

        @CanIgnoreReturnValue
        public MetricName.Builder from(MetricName instance) {
            Preconditions.checkNotNull(instance, "instance");
            safeName(instance.safeName());
            putAllSafeTags(instance.safeTags());
            return (MetricName.Builder) this;
        }

        @CanIgnoreReturnValue
        public MetricName.Builder safeName(@Safe String value) {
            this.safeName = Preconditions.checkNotNull(value, "safeName");
            return (MetricName.Builder) this;
        }

        @CanIgnoreReturnValue
        public MetricName.Builder putSafeTags(@Safe String key, @Safe String value) {
            Preconditions.checkNotNull(key, "safeTagName");
            Preconditions.checkNotNull(value, "safeTagValue");
            tagMap = tagMap.withEntry(key, value);
            return (MetricName.Builder) this;
        }

        @CanIgnoreReturnValue
        public MetricName.Builder putSafeTags(@Safe Map.Entry<@Safe String, @Safe ? extends String> entry) {
            Preconditions.checkNotNull(entry, "entry");
            tagMap = tagMap.withEntry(entry.getKey(), entry.getValue());
            return (MetricName.Builder) this;
        }

        @SuppressWarnings("unchecked")
        @CanIgnoreReturnValue
        public MetricName.Builder safeTags(@Safe Map<@Safe String, @Safe ? extends String> entries) {
            Preconditions.checkNotNull(entries, "entries");
            tagMap = TagMap.of((Map<String, String>) entries);
            return (MetricName.Builder) this;
        }

        @SuppressWarnings("unchecked")
        @CanIgnoreReturnValue
        public MetricName.Builder putAllSafeTags(@Safe Map<@Safe String, @Safe ? extends String> entries) {
            Preconditions.checkNotNull(entries, "entries");
            if (!entries.isEmpty()) {
                tagMap = tagMap.isEmpty()
                        ? TagMap.of((Map<String, String>) entries)
                        : TagMap.of(ImmutableMap.<String, String>builderWithExpectedSize(tagMap.size() + entries.size())
                                .putAll(tagMap)
                                .putAll(entries)
                                .buildOrThrow());
            }
            return (MetricName.Builder) this;
        }

        @SuppressWarnings("NullAway") // RealMetricName ctor checks nulls
        public MetricName build() {
            return new RealMetricName(safeName, tagMap);
        }
    }
}
