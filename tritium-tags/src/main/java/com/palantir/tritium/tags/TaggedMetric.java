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
import java.util.Iterator;
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

    @Value.Parameter
    abstract String metricName();

    @Value.Parameter
    abstract Map<String, String> tags();

    @Value.Check
    final void check() {
        checkArgument(isNotBlank(metricName()),
                "Metric name '%s' must not be empty", metricName());
        for (String delimiter : Arrays.asList(":", ",", "{", "}")) {
            checkMetricNameDoesNotContain(delimiter);
        }
    }

    private void checkMetricNameDoesNotContain(String seq) {
        checkArgument(!metricName().contains(seq),
                "Metric name '%s' must not contain '%s'", metricName(), seq);
    }

    @Override
    public final String toString() {
        return canonicalName();
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
                .filter(entry -> isNotBlank(entry.getKey()) && isNotBlank(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Returns the canonical metric name including any tags.
     *
     * @return the canonical metric name
     */
    @Value.Lazy
    public String canonicalName() {
        if (nonEmptyTags().isEmpty()) {
            return metricName();
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append(metricName()).append('[');

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

    private static void checkArgument(boolean condition, String messageFormat, Object argument1, Object argument2) {
        if (!condition) {
            throw new IllegalArgumentException(String.format(messageFormat, argument1, argument2));
        }
    }

    private static void checkArgument(boolean condition, String messageFormat, Object argument) {
        if (!condition) {
            throw new IllegalArgumentException(String.format(messageFormat, argument));
        }
    }

    static boolean isNotBlank(String string) {
        if (string == null || string.isEmpty()) {
            return false;
        }

        for (int i = 0; i < string.length(); i++) {
            if (!Character.isWhitespace(string.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static TaggedMetricBuilder builder() {
        return new TaggedMetricBuilder();
    }

}
