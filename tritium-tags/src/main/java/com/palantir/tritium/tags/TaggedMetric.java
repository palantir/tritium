/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.tritium.tags;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
        // Don't require GuavaModule
        jdkOnly = true,
        // detect 'get' and 'is' prefixes in accessor methods
        get = {"get*", "is*"},
        // Try to avoid leaking Immutable prefixed objects to avoid
        // api fallout when we switch to another processor.
        // Side effect: Return the base type from builders.
        // Side effect: Nest implementation class inside of builder class.
        visibility = Value.Style.ImplementationVisibility.PRIVATE,
        builderVisibility = Value.Style.BuilderVisibility.PACKAGE,
        // Default interface methods don't need to be annotated @Value.Default
        defaultAsDefault = true)
public abstract class TaggedMetric {

    static final Pattern TAG_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9-]{1,19}");
    static final char TAG_START_DELIMITER = '[';
    static final char TAG_END_DELIMITER = ']';
    static final char KEY_VALUE_DELIMITER = ':';
    static final char ELEMENT_DELIMITER = ',';
    static final List<String> DELIMITERS = Collections.unmodifiableList(Arrays.asList(
            String.valueOf(TAG_START_DELIMITER),
            String.valueOf(TAG_END_DELIMITER),
            String.valueOf(KEY_VALUE_DELIMITER),
            String.valueOf(ELEMENT_DELIMITER)));


    @Value.Parameter
    abstract String name();

    @Value.Parameter
    abstract Map<String, String> tags();

    /**
     * Returns the canonical metric name including any tags.
     *
     * @return the canonical metric name
     */
    @Value.Lazy
    public String canonicalName() {
        return TaggedMetric.toCanonicalName(name(), tags());
    }

    @Override
    public final String toString() {
        return canonicalName();
    }

    @Value.Check
    final void check() {
        TagPreconditions.checkNotBlank(name());

        for (String delimiter : TaggedMetric.DELIMITERS) {
            TagPreconditions.checkNameDoesNotContain(name(), delimiter);
            tags().forEach((key, value) -> {
                TagPreconditions.checkNotBlank(key);
                TagPreconditions.checkNameDoesNotContain(key, delimiter);
                TagPreconditions.checkNotBlank(value);
                TagPreconditions.checkNameDoesNotContain(value, delimiter);
            });
        }
    }

    public static TaggedMetricBuilder builder() {
        return new TaggedMetricBuilder();
    }

    /**
     * Parses the specified canonical metric name into metric name and tags.
     *
     * @param canonicalMetricName canonical metric name
     * @return tagged metric
     * @see TaggedMetric#toCanonicalName(String, Map)
     */
    public static TaggedMetric from(String canonicalMetricName) {
        TagPreconditions.checkNotNull(canonicalMetricName, "canonicalMetricName");

        TaggedMetricBuilder builder = TaggedMetric.builder();

        int metricNameEnd = canonicalMetricName.indexOf(TaggedMetric.TAG_START_DELIMITER);
        if (metricNameEnd > -1) {
            builder.name(canonicalMetricName.substring(0, metricNameEnd));
            String tagName = null;
            int startIndex = metricNameEnd + 1;
            boolean bracketsOpen = false;
            for (int i = metricNameEnd; i < canonicalMetricName.length(); i++) {
                char ch = canonicalMetricName.charAt(i);
                switch (ch) {
                    case TaggedMetric.TAG_START_DELIMITER:
                        bracketsOpen = true;
                        break;
                    case TaggedMetric.KEY_VALUE_DELIMITER:
                        tagName = canonicalMetricName.substring(startIndex, i).trim();
                        startIndex = i + 1;
                        break;
                    case TaggedMetric.ELEMENT_DELIMITER:
                        // fall through
                    case TaggedMetric.TAG_END_DELIMITER:
                        bracketsOpen = false;
                        if (tagName != null) {
                            String tagValue = canonicalMetricName.substring(startIndex, i).trim();
                            builder.putTags(tagName, tagValue);
                        }
                        startIndex = i + 1;
                        break;
                }
            }
            if (bracketsOpen) {
                throw new IllegalArgumentException(String.format("Invalid metric name '%s', found trailing '%s'",
                        canonicalMetricName, canonicalMetricName.substring(metricNameEnd)));
            }
        } else {
            builder.name(canonicalMetricName);
        }

        return builder.build();
    }

    /**
     * Generates a canonical metric name for the given base metric name and tags.
     *
     * @param name base metric name
     * @param tags map of key/value tags
     * @return encoded metric name
     */
    public static String toCanonicalName(String name, Map<String, String> tags) {
        TagPreconditions.checkNotNull(name, "name");
        TagPreconditions.checkNotNull(tags, "tags");

        if (tags.isEmpty()) {
            return name;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(name).append(TAG_START_DELIMITER);

        for (Iterator<Map.Entry<String, String>> it = normalizeTags(tags).entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> entry = it.next();
            builder.append(normalizeKey(entry.getKey())).append(KEY_VALUE_DELIMITER).append(entry.getValue().trim());
            if (it.hasNext()) {
                builder.append(ELEMENT_DELIMITER);
            }
        }

        return builder.append(TAG_END_DELIMITER).toString();
    }


    static SortedMap<String, String> normalizeTags(Map<String, String> tags) {
        if (tags.isEmpty()) {
            return Collections.emptySortedMap();
        }

        SortedMap<String, String> normalizedTags = new TreeMap<>();
        tags.forEach((key, value) -> {
            checkValidTagName(key);
            TagPreconditions.checkValidTagComponent(value, DELIMITERS);

            String previous = normalizedTags.put(normalizeKey(key), value);
            if (previous != null) {
                throw new IllegalArgumentException(String.format(
                        "Invalid tag '%s' with value '%s' duplicates case-insensitive key '%s' with value '%s'",
                        key, value, key.toLowerCase(), previous));
            }
        });

        return normalizedTags;
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).trim();
    }

    static void checkValidTagName(String key) {
        TagPreconditions.checkValidTagComponent(key, DELIMITERS);
        if (!TAG_NAME_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(String.format(
                    "Invalid metric name '%s' does not match pattern %s",
                    key, TAG_NAME_PATTERN));
        }
    }

}
