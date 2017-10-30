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

package com.palantir.tritium.metrics;

import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable(prehash = true)
@Value.Style(
        jdkOnly = true,
        get = {"get*", "is*"},
        overshadowImplementation = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE)
public interface MetricName {

    /**
     * General/abstract measure (e.g. server.response-time).
     * <p>
     * Names must be {@link com.palantir.logsafe.Safe} to log.
     */
    String name();

    /**
     * Metadata/coordinates for where a particular measure came from. Used for filtering & grouping.
     * <p>
     * All tags and keys must be {@link com.palantir.logsafe.Safe} to log.
     */
    Map<String, String> tags();

    static Builder builder() {
        return new Builder();
    }

    class Builder extends ImmutableMetricName.Builder {}
}
