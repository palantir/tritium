/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codahale.metrics.Counter;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import org.junit.jupiter.api.Test;

class AugmentedTaggedMetricRegistryTest {
    private final DefaultTaggedMetricRegistry delegate = new DefaultTaggedMetricRegistry();
    private final AugmentedTaggedMetricRegistry registry =
            AugmentedTaggedMetricRegistry.create(delegate, "dialogueVersion", "1.0.0");
    private final MetricName hello = MetricName.builder().safeName("hello").build();
    private final MetricName helloAugmented = MetricName.builder()
            .safeName("hello")
            .putSafeTags("dialogueVersion", "1.0.0")
            .build();

    @Test
    void creating_a_counter_stores_augmented_metricname_in_underlying_registry() {
        Counter counter = registry.counter(hello);
        assertThat(delegate.getMetrics()).containsEntry(helloAugmented, counter);
        assertThat(registry.getMetrics()).containsEntry(helloAugmented, counter);
    }

    @Test
    void foo() {
        assertThatThrownBy(() -> registry.counter(MetricName.builder()
                        .safeName("foo")
                        .putSafeTags("dialogueVersion", "bork")
                        .build()))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("Map must not contain the extra key that is to be added: "
                        + "{key=dialogueVersion, existingValue=bork, newValue=1.0.0}");
    }

    @Test
    void underlying_metrics_can_be_accessed_unmodified() {
        Counter counter = delegate.counter(hello);
        assertThat(delegate.getMetrics()).containsEntry(hello, counter);
        assertThat(registry.getMetrics()).containsEntry(hello, counter);
    }

    @Test
    void reewrapping_with_identical_values_does_nothing() {
        AugmentedTaggedMetricRegistry secondCreate =
                AugmentedTaggedMetricRegistry.create(registry, "dialogueVersion", "1.0.0");
        assertThat(secondCreate).isSameAs(registry);
    }

    @Test
    void rewrapping_with_different_value_will_break() {
        assertThatThrownBy(() -> AugmentedTaggedMetricRegistry.create(registry, "dialogueVersion", "2.0.0"))
                .hasMessage("Tag is already defined with a different value: "
                        + "{tagName=dialogueVersion, existing=1.0.0, new=2.0.0}");
    }

    @Test
    void equals_and_hashcode() {
        assertThat(AugmentedTaggedMetricRegistry.create(delegate, "dialogueVersion", "1.0.0"))
                .hasSameHashCodeAs(registry);
        assertThat(AugmentedTaggedMetricRegistry.create(delegate, "dialogueVersion", "1.0.0"))
                .isEqualTo(registry);
        assertThat(AugmentedTaggedMetricRegistry.create(delegate, "dialogueVersion", "1.0.1"))
                .isNotEqualTo(registry);
        assertThat(AugmentedTaggedMetricRegistry.create(delegate, "atlasVersion", "1.0.0"))
                .isNotEqualTo(registry);
    }
}
