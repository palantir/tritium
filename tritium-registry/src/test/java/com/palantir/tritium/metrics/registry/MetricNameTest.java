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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class MetricNameTest {

    @Test
    public void compareSame() {
        MetricName one = MetricName.builder()
                .safeName("test")
                .putSafeTags("key", "value")
                .putSafeTags("key1", "value1")
                .putSafeTags("key2", "value2")
                .build();
        MetricName two = MetricName.builder()
                .safeName("test")
                .putSafeTags("key2", "value2")
                .putSafeTags("key", "value")
                .putSafeTags("key1", "value1")
                .build();
        MetricName three = RealMetricName.create(two);
        MetricName four = RealMetricName.create(
                MetricName.builder()
                        .safeName("test")
                        .putSafeTags("key2", "value2")
                        .putSafeTags("key", "value")
                        .build(),
                "key1",
                "value1");
        MetricName five = MetricName.builder().from(four).build();

        assertThat(one).isEqualTo(two).isEqualTo(three).isEqualTo(four).isEqualTo(five);
        assertThat(two).isEqualTo(one).isEqualTo(three).isEqualTo(four).isEqualTo(five);
        assertThat(three).isEqualTo(one).isEqualTo(two).isEqualTo(four).isEqualTo(five);
        assertThat(four).isEqualTo(one).isEqualTo(two).isEqualTo(three).isEqualTo(five);

        assertThat(one.toString())
                .isEqualTo(two.toString())
                .isEqualTo(three.toString())
                .isEqualTo(four.toString())
                .isEqualTo(five.toString())
                .isEqualTo("MetricName{safeName=test, safeTags={key=value, key1=value1, key2=value2}}");
        assertThat(one)
                .hasSameHashCodeAs(two)
                .hasSameHashCodeAs(three)
                .hasSameHashCodeAs(four)
                .hasSameHashCodeAs(five);
    }

    @Test
    public void compareName() {
        assertThat(MetricName.builder().safeName("a").build())
                .isEqualTo(MetricName.builder().safeName("a").build());

        assertThat(MetricName.builder().safeName("a").build())
                .isNotEqualTo(MetricName.builder().safeName("b").build());
    }

    @Test
    public void compareTagNames() {
        MetricName one = MetricName.builder()
                .safeName("a")
                .putSafeTags("key1", "value1")
                .putSafeTags("key2", "value2")
                .build();
        MetricName two = MetricName.builder()
                .safeName("a")
                .putSafeTags("key1", "value1")
                .putSafeTags("key3", "value2")
                .build();

        assertThat(one).isNotEqualTo(two);
        assertThat(two).isNotEqualTo(one);
    }

    @Test
    public void compareTagValues() {
        MetricName one = MetricName.builder()
                .safeName("a")
                .putSafeTags("key1", "value1")
                .putSafeTags("key2", "value2")
                .build();
        MetricName two = MetricName.builder()
                .safeName("a")
                .putSafeTags("key1", "value1")
                .putSafeTags("key2", "valueZ")
                .build();

        assertThat(one).isNotEqualTo(two);
        assertThat(two).isNotEqualTo(one);
    }

    @Test
    public void preSizedBuilder() {
        MetricName preSized = MetricName.builderWithExpectedTags(2)
                .safeName("test")
                .putSafeTags("key1", "value1")
                .putSafeTags("key2", "value2")
                .build();
        MetricName standard = MetricName.builder()
                .safeName("test")
                .putSafeTags("key1", "value1")
                .putSafeTags("key2", "value2")
                .build();

        assertThat(preSized).isEqualTo(standard);
        assertThat(preSized).hasSameHashCodeAs(standard);
        assertThat(preSized.safeName()).isEqualTo("test");
        assertThat(preSized.safeTags()).containsEntry("key1", "value1").containsEntry("key2", "value2");
    }

    @Test
    public void preSizedBuilder_noTags() {
        MetricName preSized =
                MetricName.builderWithExpectedTags(0).safeName("test").build();
        MetricName standard = MetricName.builder().safeName("test").build();

        assertThat(preSized).isEqualTo(standard);
        assertThat(preSized).hasSameHashCodeAs(standard);
    }

    @Test
    public void preSizedBuilder_equalsStandardBuilderWithDifferentInsertionOrder() {
        MetricName preSized = MetricName.builderWithExpectedTags(3)
                .safeName("test")
                .putSafeTags("a", "1")
                .putSafeTags("b", "2")
                .putSafeTags("c", "3")
                .build();
        MetricName standard = MetricName.builder()
                .safeName("test")
                .putSafeTags("c", "3")
                .putSafeTags("a", "1")
                .putSafeTags("b", "2")
                .build();

        assertThat(preSized).isEqualTo(standard);
        assertThat(preSized).hasSameHashCodeAs(standard);
        assertThat(preSized.toString()).isEqualTo(standard.toString());
    }

    @Test
    public void preSizedBuilder_sizeMismatchThrows() {
        assertThatThrownBy(() -> MetricName.builderWithExpectedTags(3)
                        .safeName("test")
                        .putSafeTags("key1", "value1")
                        .putSafeTags("key2", "value2")
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void preSizedBuilder_notLexicographicalOrderThrows() {
        assertThatThrownBy(() -> MetricName.builderWithExpectedTags(3)
                        .safeName("test")
                        .putSafeTags("key2", "value2")
                        .putSafeTags("key1", "value1")
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
