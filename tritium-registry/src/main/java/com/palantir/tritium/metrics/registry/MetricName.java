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

import com.palantir.logsafe.Safe;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable(prehash = true)
@Value.Style(
        jdkOnly = true,
        get = {"get*", "is*"},
        overshadowImplementation = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE)
public interface MetricName extends Comparable<MetricName> {

    Comparator<Map.Entry<String, String>> entryComparator =
            Map.Entry.<String, String>comparingByKey()
                    .thenComparing(Map.Entry.comparingByValue());
    Comparator<MetricName> metricNameComparator =
            Comparator.comparing(MetricName::safeName)
                    .thenComparing(metricName -> metricName.safeTags().entrySet(), (set1, set2) -> {
                        Iterator<Map.Entry<String, String>> i1 = set1.iterator();
                        Iterator<Map.Entry<String, String>> i2 = set2.iterator();
                        while (i1.hasNext() && i2.hasNext()) {
                            Map.Entry<String, String> e1 = i1.next();
                            Map.Entry<String, String> e2 = i2.next();
                            int compare = entryComparator.compare(e1, e2);
                            if (compare != 0) {
                                return compare;
                            }
                        }
                        return i1.hasNext() ? -1 : i2.hasNext() ? 1 : 0;
                    });

    /**
     * General/abstract measure (e.g. server.response-time).
     * <p>
     * Names must be {@link Safe} to log.
     */
    String safeName();

    /**
     * Metadata/coordinates for where a particular measure came from. Used for filtering & grouping.
     * <p>
     * All tags and keys must be {@link Safe} to log.
     */
    Map<String, String> safeTags();

    @Override
    default int compareTo(MetricName that) {
        return metricNameComparator.compare(this, that);
    }

    static Builder builder() {
        return new Builder();
    }

    class Builder extends ImmutableMetricName.Builder {}
}
