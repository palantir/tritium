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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;

final class MetricNames {

    private MetricNames() {}

    private static final Comparator<Map.Entry<String, String>> entryComparator =
            Map.Entry.<String, String>comparingByKey()
                    .thenComparing(Map.Entry.comparingByValue());

    private static final Comparator<Map<String, String>> sizeComparator = Comparator.comparingInt(Map::size);

    private static final Comparator<Map<String, String>> mapComparator = (m1, m2) -> {
        NavigableSet<Map.Entry<String, String>> l1 = ImmutableSortedSet.copyOf(entryComparator, m1.entrySet());
        NavigableSet<Map.Entry<String, String>> l2 = ImmutableSortedSet.copyOf(entryComparator, m2.entrySet());
        Iterator<Map.Entry<String, String>> i1 = l1.iterator();
        Iterator<Map.Entry<String, String>> i2 = l2.iterator();
        while (i1.hasNext() && i2.hasNext()) {
            Map.Entry<String, String> e1 = i1.next();
            Map.Entry<String, String> e2 = i2.next();
            int compare = entryComparator.compare(e1, e2);
            if (compare != 0) {
                return compare;
            }
        }
        return i1.hasNext() ? -1 : i2.hasNext() ? 1 : 0;
    };

    @VisibleForTesting
    static final Comparator<MetricName> metricNameComparator =
            Comparator.comparing(MetricName::safeName)
                    .thenComparing(MetricName::safeTags, sizeComparator)
                    .thenComparing(MetricName::safeTags, mapComparator);
}
