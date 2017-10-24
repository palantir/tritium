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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Collections;
import org.junit.Test;

public class TaggedMetricTest {

    @Test
    public void generateSimple() {
        assertThat(TaggedMetric.of("test", Collections.emptyMap())).isEqualTo("test");
    }

    @Test
    public void generateSingleTag() {
        assertThat(TaggedMetric.of("test", Collections.singletonMap("key", "value")))
                .isEqualTo("test[key:value]");
    }

    @Test
    public void generateMultipleTagsOrdered() {
        assertThat(TaggedMetric.of("test", ImmutableMap.of(
                "beta", "b1",
                "alpha", "a1")))
                .isEqualTo("test[alpha:a1,beta:b1]");
    }

    @Test
    public void generateInvalid() {
        assertThatThrownBy(() -> TaggedMetric.of(null, Collections.emptyMap()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name");

        assertThatThrownBy(() -> TaggedMetric.of("test", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tags");

        assertThatThrownBy(() -> TaggedMetric.of("test", ImmutableMap.of(
                "key", "value",
                "KEY", "VALUE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Invalid tag 'KEY' with value 'VALUE' duplicates case-insensitive key 'key' with value 'value'");
    }

    @Test
    public void normalizeTags() {
        assertThat(TaggedMetric.normalizeTags(ImmutableMap.of()))
                .isSameAs(Collections.emptySortedMap())
                .isEmpty();

        assertThat(TaggedMetric.normalizeTags(ImmutableMap.of(
                "key", "value")))
                .containsExactly(Maps.immutableEntry("key", "value"));

        assertThat(TaggedMetric.normalizeTags(ImmutableMap.of(
                "a2345678901234567890", "value")))
                .containsExactly(Maps.immutableEntry("a2345678901234567890", "value"));

        assertThat(TaggedMetric.normalizeTags(ImmutableMap.of(
                "KEY", "value")))
                .containsExactly(Maps.immutableEntry("key", "value"));

        assertThat(TaggedMetric.normalizeTags(ImmutableMap.of(
                "Key-Name", "value")))
                .containsExactly(Maps.immutableEntry("key-name", "value"));

        assertThatThrownBy(() -> TaggedMetric.normalizeTags(ImmutableMap.of(
                "a23456789012345678901", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid metric name 'a23456789012345678901'");

        assertThatThrownBy(() -> TaggedMetric.normalizeTags(ImmutableMap.of(
                "key", "value",
                "KEY", "VALUE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Invalid tag 'KEY' with value 'VALUE' duplicates case-insensitive key 'key' with value 'value'");

        assertThatThrownBy(() -> TaggedMetric.normalizeTags(ImmutableMap.of(
                "key", "value",
                " key ", "value2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid metric name ' key '");

        assertThatThrownBy(() -> TaggedMetric.normalizeTags(ImmutableMap.of(
                "a", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid metric name 'a'");

        assertThatThrownBy(() -> TaggedMetric.normalizeTags(ImmutableMap.of(
                "a.1", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid metric name 'a.1'");
    }
}
