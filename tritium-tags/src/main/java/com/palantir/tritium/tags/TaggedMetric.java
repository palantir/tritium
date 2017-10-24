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
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public enum TaggedMetric {
    ;

    static final char TAG_START_DELIMITER = '[';
    static final char TAG_END_DELIMITER = ']';
    static final char KEY_VALUE_DELIMITER = ':';
    static final char ELEMENT_DELIMITER = ',';
    static final List<String> DELIMITERS = Collections.unmodifiableList(Arrays.asList(
            String.valueOf(TAG_START_DELIMITER),
            String.valueOf(TAG_END_DELIMITER),
            String.valueOf(KEY_VALUE_DELIMITER),
            String.valueOf(ELEMENT_DELIMITER)));

    static SortedMap<String, String> nonEmptyElements(Map<String, String> tags) {
        if (tags.isEmpty()) {
            return Collections.emptySortedMap();
        }
        return tags.entrySet().stream()
                .filter(entry ->
                        TagPreconditions.isNotNullOrBlank(entry.getKey())
                                && TagPreconditions.isNotNullOrBlank(entry.getValue()))
                .collect(Collectors.toMap(
                        mapEntry -> TagPreconditions.checkValidTagComponent(mapEntry.getKey(), DELIMITERS),
                        mapEntry -> TagPreconditions.checkValidTagComponent(mapEntry.getValue(), DELIMITERS),
                        (left, right) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", left));
                        },
                        TreeMap::new));
    }

    /**
     * Generates a canonical metric name for the given base metric name and tags.
     *
     * @param name base metric name
     * @param tags map of key/value tags
     * @return encoded metric name
     */
    public static String of(String name, Map<String, String> tags) {
        TagPreconditions.checkNotNull(name, "name");
        TagPreconditions.checkNotNull(tags, "tags");

        if (tags.isEmpty()) {
            return name;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(name).append(TAG_START_DELIMITER);

        for (Iterator<Map.Entry<String, String>> it = nonEmptyElements(tags).entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> entry = it.next();
            builder.append(entry.getKey().trim()).append(KEY_VALUE_DELIMITER).append(entry.getValue().trim());
            if (it.hasNext()) {
                builder.append(ELEMENT_DELIMITER);
            }
        }

        return builder.append(TAG_END_DELIMITER).toString();
    }

}
