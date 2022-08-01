/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Ordering;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class TagMapTest {

    @Test
    void testEmpty() {
        TagMap map = TagMap.of(ImmutableSortedMap.of());
        assertThat(map).isEmpty();
        assertThat(map).isEmpty();
        assertThat(map.entrySet()).isEmpty();
        assertThat(map.entrySet()).hasSize(0);
        assertThat(map.entrySet().iterator()).isExhausted();
    }

    @Test
    void testSingleton() {
        TagMap map = TagMap.of(Collections.singletonMap("foo", "bar"));
        assertThat(map).isNotEmpty();
        assertThat(map).hasSize(1);
        assertThat(map).containsEntry("foo", "bar");
        assertThat(map.get("foo")).isEqualTo("bar");
        assertThat(map.entrySet()).isNotEmpty();
        assertThat(map.entrySet()).hasSize(1);
        assertThat(map.entrySet().iterator()).hasNext();
        assertThat(map.entrySet().iterator().next()).isEqualTo(new SimpleImmutableEntry<>("foo", "bar"));
    }

    @Test
    void testExtraEntryKeyAlreadyExists() {
        TagMap original = TagMap.of(Collections.singletonMap("foo", "bar"));
        TagMap updated = original.withEntry("foo", "baz");
        assertThat(updated)
                .hasSize(1)
                .containsEntry("foo", "baz")
                .isEqualTo(ImmutableMap.of("foo", "baz"))
                .as("Original must not be mutated")
                .isNotSameAs(original)
                .isNotEqualTo(original);
        assertThat(original)
                .hasSize(1)
                .as("Original must not be mutated")
                .containsEntry("foo", "bar")
                .isNotEqualTo(updated);
    }

    @Test
    void testUpdateWithExisting() {
        TagMap map = TagMap.EMPTY.withEntry("foo", "bar");
        assertThat(map.withEntry("foo", "bar")).isSameAs(map);
    }

    @Test
    void testNaturalOrder() {
        assertThat(TagMap.isNaturalOrder(Ordering.natural())).isTrue();
        assertThat(TagMap.isNaturalOrder(Comparator.naturalOrder())).isTrue();

        assertThat(TagMap.isNaturalOrder(Comparator.reverseOrder())).isFalse();
        Comparator<?> immutableSortedMapComparator = ImmutableSortedMap.naturalOrder()
                .put("a", "b")
                .put("c", "d")
                .build()
                .comparator();
        assertThat(TagMap.isNaturalOrder(immutableSortedMapComparator))
                .as("Expected ImmutableSortedMap comparator %s to be natural", immutableSortedMapComparator)
                .isTrue();
    }
}
