package com.palantir.tritium.metrics.registry;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.palantir.logsafe.Preconditions;
import java.util.Map;
import javax.annotation.Nullable;

/** ABI backcompat stub for code which was previously generated with immutables. */
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
        public MetricName.Builder safeName(String value) {
            this.safeName = Preconditions.checkNotNull(value, "safeName");
            return (MetricName.Builder) this;
        }

        @CanIgnoreReturnValue
        public MetricName.Builder putSafeTags(String key, String value) {
            Preconditions.checkNotNull(key, "safeTagName");
            Preconditions.checkNotNull(value, "safeTagValue");
            tagMap = tagMap.withEntry(key, value);
            return (MetricName.Builder) this;
        }

        @CanIgnoreReturnValue
        public MetricName.Builder putSafeTags(Map.Entry<String, ? extends String> entry) {
            Preconditions.checkNotNull(entry, "entry");
            tagMap = tagMap.withEntry(entry.getKey(), entry.getValue());
            return (MetricName.Builder) this;
        }

        @SuppressWarnings("unchecked")
        @CanIgnoreReturnValue
        public MetricName.Builder safeTags(Map<String, ? extends String> entries) {
            Preconditions.checkNotNull(entries, "entries");
            tagMap = TagMap.of((Map<String, String>) entries);
            return (MetricName.Builder) this;
        }

        @SuppressWarnings("unchecked")
        @CanIgnoreReturnValue
        public MetricName.Builder putAllSafeTags(Map<String, ? extends String> entries) {
            Preconditions.checkNotNull(entries, "entries");
            if (!entries.isEmpty()) {
                tagMap = tagMap.isEmpty()
                        ? TagMap.of((Map<String, String>) entries)
                        : TagMap.of(ImmutableMap.<String, String>builderWithExpectedSize(tagMap.size() + entries.size())
                                .putAll(tagMap)
                                .putAll(entries)
                                .build());
            }
            return (MetricName.Builder) this;
        }

        public MetricName build() {
            return new RealMetricName(Preconditions.checkNotNull(safeName, "safeName is required"), tagMap);
        }
    }
}
