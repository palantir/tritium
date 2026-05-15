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

import com.google.common.collect.ImmutableMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class MetricNameTest {

    @Nested
    class Equality {

        @Test
        public void sameTags() {
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
        public void sameReference() {
            MetricName name =
                    MetricName.builder().safeName("test").putSafeTags("k", "v").build();
            assertThat(name.equals(name)).isTrue();
        }

        @Test
        public void differentName() {
            assertThat(MetricName.builder().safeName("a").build())
                    .isEqualTo(MetricName.builder().safeName("a").build());
            assertThat(MetricName.builder().safeName("a").build())
                    .isNotEqualTo(MetricName.builder().safeName("b").build());
        }

        @Test
        public void differentTagKeys() {
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
        public void differentTagValues() {
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
        public void notEqualToNonMetricName() {
            MetricName name = MetricName.builder().safeName("a").build();
            assertThat(name).isNotEqualTo("not a MetricName");
        }

        @Test
        public void nameOnlyFactory() {
            MetricName name = RealMetricName.create("test");
            assertThat(name.safeName()).isEqualTo("test");
            assertThat(name.safeTags()).isEmpty();
            assertThat(name).isEqualTo(MetricName.builder().safeName("test").build());
        }
    }

    @Nested
    class StandardBuilder {

        @Test
        public void putSafeTagsEntry() {
            MetricName name = MetricName.builder()
                    .safeName("test")
                    .putSafeTags(new SimpleImmutableEntry<>("k1", "v1"))
                    .putSafeTags(new SimpleImmutableEntry<>("k2", "v2"))
                    .build();
            assertThat(name.safeTags()).containsEntry("k1", "v1").containsEntry("k2", "v2");
        }

        @Test
        public void safeTagsReplacesAll() {
            MetricName name = MetricName.builder()
                    .safeName("test")
                    .putSafeTags("old", "tag")
                    .safeTags(ImmutableMap.of("new1", "v1", "new2", "v2"))
                    .build();
            assertThat(name.safeTags()).hasSize(2).containsEntry("new1", "v1").doesNotContainKey("old");
        }

        @Test
        public void putAllSafeTagsOntoEmpty() {
            MetricName name = MetricName.builder()
                    .safeName("test")
                    .putAllSafeTags(ImmutableMap.of("a", "1", "b", "2"))
                    .build();
            assertThat(name.safeTags()).hasSize(2).containsEntry("a", "1").containsEntry("b", "2");
        }

        @Test
        public void putAllSafeTagsMergesWithExisting() {
            MetricName name = MetricName.builder()
                    .safeName("test")
                    .putSafeTags("a", "1")
                    .putAllSafeTags(ImmutableMap.of("b", "2", "c", "3"))
                    .build();
            assertThat(name.safeTags()).hasSize(3).containsEntry("a", "1").containsEntry("b", "2");
        }

        @Test
        public void putAllSafeTagsEmptyIsNoop() {
            MetricName name = MetricName.builder()
                    .safeName("test")
                    .putSafeTags("a", "1")
                    .putAllSafeTags(Collections.emptyMap())
                    .build();
            assertThat(name.safeTags()).hasSize(1).containsEntry("a", "1");
        }

        @Test
        public void from() {
            MetricName original =
                    MetricName.builder().safeName("test").putSafeTags("k", "v").build();
            MetricName copy = MetricName.builder().from(original).build();
            assertThat(copy).isEqualTo(original);
            assertThat(copy).hasSameHashCodeAs(original);
        }
    }

    @Nested
    class PreSizedBuilderTest {

        @Test
        public void matchesStandardBuilder() {
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
        public void noTags() {
            MetricName preSized =
                    MetricName.builderWithExpectedTags(0).safeName("test").build();
            MetricName standard = MetricName.builder().safeName("test").build();
            assertThat(preSized).isEqualTo(standard);
            assertThat(preSized).hasSameHashCodeAs(standard);
        }

        @Test
        public void differentInsertionOrder() {
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
        public void sizeMismatchShrinks() {
            MetricName result = MetricName.builderWithExpectedTags(3)
                    .safeName("test")
                    .putSafeTags("key1", "value1")
                    .putSafeTags("key2", "value2")
                    .build();
            MetricName expected = MetricName.builder()
                    .safeName("test")
                    .putSafeTags("key1", "value1")
                    .putSafeTags("key2", "value2")
                    .build();
            assertThat(result).isEqualTo(expected);
        }

        @Test
        public void notLexicographicalOrderSorts() {
            MetricName result = MetricName.builderWithExpectedTags(2)
                    .safeName("test")
                    .putSafeTags("key2", "value2")
                    .putSafeTags("key1", "value1")
                    .build();
            MetricName expected = MetricName.builder()
                    .safeName("test")
                    .putSafeTags("key1", "value1")
                    .putSafeTags("key2", "value2")
                    .build();
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    class NullValidation {

        @Test
        public void builderNullSafeName() {
            assertThatThrownBy(() -> MetricName.builder().safeName(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        public void builderNullTagKey() {
            assertThatThrownBy(() -> MetricName.builder().putSafeTags(null, "v"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        public void builderNullTagValue() {
            assertThatThrownBy(() -> MetricName.builder().putSafeTags("k", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        public void builderNullEntry() {
            assertThatThrownBy(() -> MetricName.builder().putSafeTags(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        public void builderNullFrom() {
            assertThatThrownBy(() -> MetricName.builder().from(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        public void builderNullSafeTagsMap() {
            assertThatThrownBy(() -> MetricName.builder().safeTags(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        public void builderNullPutAllSafeTagsMap() {
            assertThatThrownBy(() -> MetricName.builder().putAllSafeTags(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        public void buildWithoutSafeName() {
            assertThatThrownBy(() -> MetricName.builder().build()).isInstanceOf(NullPointerException.class);
        }

        @Test
        public void preSizedBuilderBuildWithoutSafeName() {
            assertThatThrownBy(() -> MetricName.builderWithExpectedTags(0).build())
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
