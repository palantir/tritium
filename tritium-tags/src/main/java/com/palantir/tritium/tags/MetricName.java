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

import java.util.Map;
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
        builderVisibility = Value.Style.BuilderVisibility.PUBLIC,
        // Default interface methods don't need to be annotated @Value.Default
        defaultAsDefault = true)
public interface MetricName {
    String name();
    Map<String, String> tags();
}
