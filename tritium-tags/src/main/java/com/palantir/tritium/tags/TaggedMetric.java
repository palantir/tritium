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
import java.util.Map;
import java.util.stream.Collectors;
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

    private static final List<String> DELIMITERS = Collections.unmodifiableList(Arrays.asList(":", ",", "[", "]"));

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
        if (nonEmptyTags().isEmpty()) {
            return name();
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append(name()).append('[');

            for (Iterator<Map.Entry<String, String>> it = nonEmptyTags().entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, String> entry = it.next();
                builder.append(entry.getKey().trim()).append(':').append(entry.getValue().trim());
                if (it.hasNext()) {
                    builder.append(',');
                }
            }

            builder.append(']');
            return builder.toString();
        }
    }

    /**
     * Returns a map of the tags with non-empty keys and values.
     *
     * @return non-empty tags
     */
    @Value.Lazy
    Map<String, String> nonEmptyTags() {
        if (tags().isEmpty()) {
            return tags();
        }

        return tags().entrySet().stream()
                .filter(entry -> isNotNullOrBlank(entry.getKey()) && isNotNullOrBlank(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public final String toString() {
        return canonicalName();
    }

    @Value.Check
    final void check() {
        checkNotBlankName();

        for (String delimiter : DELIMITERS) {
            checkNameDoesNotContain(name(), delimiter);
            tags().forEach((key, value) -> {
                checkNameDoesNotContain(key, delimiter);
                checkNameDoesNotContain(value, delimiter);
            });
        }
    }

    private void checkNotBlankName() {
        if (isNullOrBlank(name())) {
            throw new IllegalArgumentException(String.format("Metric name '%s' must not be empty", name()));
        }
    }

    private static void checkNameDoesNotContain(String name, String seq) {
        boolean condition = !name.contains(seq);
        if (!condition) {
            throw new IllegalArgumentException(String.format("'%s' must not contain '%s'", name, seq));
        }
    }

    static boolean isNotNullOrBlank(String string) {
        return !isNullOrBlank(string);
    }

    static boolean isNullOrBlank(String string) {
        if (string == null || string.isEmpty()) {
            return true;
        }

        for (int i = 0; i < string.length(); i++) {
            if (!Character.isWhitespace(string.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static TaggedMetricBuilder builder() {
        return new TaggedMetricBuilder();
    }

}
