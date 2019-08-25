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

package com.palantir.tritium.registry.test;

import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.SlidingWindowTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

@SuppressWarnings("unused") // used by junit parameterized tests
public final class TestTaggedMetricRegistries {

    public static final String REGISTRIES = "com.palantir.tritium.registry.test.TestTaggedMetricRegistries#registries";

    public static final String REGISTRY_SUPPLIERS =
            "com.palantir.tritium.registry.test.TestTaggedMetricRegistries#registrySuppliers";

    private TestTaggedMetricRegistries() {}

    public static Stream<TaggedMetricRegistry> registries() {
        return Stream.of(
                new DefaultTaggedMetricRegistry(),
                createSlidingWindowTaggedMetricRegistry());
    }

    public static Stream<Supplier<TaggedMetricRegistry>> registrySuppliers() {
        return Stream.of(
                DefaultTaggedMetricRegistry::new,
                TestTaggedMetricRegistries::createSlidingWindowTaggedMetricRegistry);
    }

    private static SlidingWindowTaggedMetricRegistry createSlidingWindowTaggedMetricRegistry() {
        return new SlidingWindowTaggedMetricRegistry(30, TimeUnit.SECONDS);
    }
}
