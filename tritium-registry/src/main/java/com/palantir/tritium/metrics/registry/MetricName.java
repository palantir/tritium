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
import java.util.SortedMap;
import org.immutables.value.Value;

@Value.Immutable
@ImmutablesStyle
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
    @Value.NaturalOrder
    SortedMap<String, String> safeTags();

    /**
     * Creates a metric name with the specified extra tag name and value entry.
     * <p>All tags and keys must be {@link Safe} to log.
     * @param extraTagName extra tag key name
     * @param extraTagValue extra tag value
     * @return metric name including keys
     */
    default MetricName withExtraTag(@Safe String extraTagName, @Safe String extraTagValue) {
        return RealMetricName.create(this, extraTagName, extraTagValue);
    }

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends ImmutableMetricName.Builder {
        // We cannot use the immutables implementation because it causes too much hashcode pain.
        @Override
        public MetricName build() {
            return RealMetricName.create(super.build());
        }
    }
}
