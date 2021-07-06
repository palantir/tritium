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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class SharedTaggedMetricRegistriesTest {
    @Test
    @SuppressWarnings("deprecation") // testing a deprecated registry
    void all_default_methods_return_the_same_thing() {
        TaggedMetricRegistry defaultRegistry = SharedTaggedMetricRegistries.getSingleton();
        assertThat(DefaultTaggedMetricRegistry.getDefault()).isSameAs(defaultRegistry);
        assertThat(SharedTaggedMetricRegistries.getSingleton()).isSameAs(defaultRegistry);
        assertThat(defaultRegistry).isInstanceOf(DefaultTaggedMetricRegistry.class);
    }
}
