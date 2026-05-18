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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Ordering;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TagMapTest {

    @Test
    void testEmpty() {
        TagMap map = TagMap.of(ImmutableSortedMap.of());
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

    @Nested
    class OfFactory {

        @Test
        void tagMapReturnsIdentity() {
            TagMap original = TagMap.of(ImmutableMap.of("a", "1", "b", "2"));
            assertThat(TagMap.of(original)).isSameAs(original);
        }

        @Test
        void sortedMaps() {
            SortedMap<String, String> naturalMap = new TreeMap<>();
            naturalMap.put("b", "2");
            naturalMap.put("a", "1");
            assertThat(TagMap.of(naturalMap)).hasSize(2).containsEntry("a", "1").containsEntry("b", "2");

            SortedMap<String, String> reverseMap = new TreeMap<>(Comparator.reverseOrder());
            reverseMap.put("b", "2");
            reverseMap.put("a", "1");
            TagMap fromReverse = TagMap.of(reverseMap);
            assertThat(fromReverse).hasSize(2).containsEntry("a", "1").containsEntry("b", "2");
            assertThat(fromReverse.firstKey()).isEqualTo("a");
        }

        @Test
        void unsortedMap() {
            TagMap map = TagMap.of(ImmutableMap.of("c", "3", "a", "1", "b", "2"));
            assertThat(map).hasSize(3);
            assertThat(map.firstKey()).isEqualTo("a");
            assertThat(map.lastKey()).isEqualTo("c");
        }
    }

    @Nested
    class BuilderTest {

        @Test
        void sortsKeys() {
            TagMap map = new TagMap.Builder(3)
                    .put("c", "3")
                    .put("a", "1")
                    .put("b", "2")
                    .build();
            assertThat(map)
                    .hasSize(3)
                    .containsExactlyInAnyOrderEntriesOf(ImmutableMap.of("a", "1", "b", "2", "c", "3"));
            assertThat(map.keySet()).containsExactly("a", "b", "c");
            assertThat(map.firstKey()).isEqualTo("a");
            assertThat(map.lastKey()).isEqualTo("c");
        }

        @Test
        void alreadySorted() {
            TagMap map = new TagMap.Builder(2).put("a", "1").put("b", "2").build();
            assertThat(map).hasSize(2).containsExactlyInAnyOrderEntriesOf(ImmutableMap.of("a", "1", "b", "2"));
        }

        @Test
        void expansion() {
            TagMap map = new TagMap.Builder(1)
                    .put("a", "1")
                    .put("b", "2")
                    .put("c", "3")
                    .build();
            assertThat(map)
                    .hasSize(3)
                    .containsExactlyInAnyOrderEntriesOf(ImmutableMap.of("a", "1", "b", "2", "c", "3"));
        }

        @Test
        void expansionFromZero() {
            TagMap map = new TagMap.Builder(0).put("b", "2").put("a", "1").build();
            assertThat(map).hasSize(2).containsExactlyInAnyOrderEntriesOf(ImmutableMap.of("a", "1", "b", "2"));
            assertThat(map.firstKey()).isEqualTo("a");
        }

        @Test
        void contraction() {
            TagMap map = new TagMap.Builder(5).put("b", "2").put("a", "1").build();
            assertThat(map).hasSize(2).containsExactlyInAnyOrderEntriesOf(ImmutableMap.of("a", "1", "b", "2"));
        }

        @Test
        void empty() {
            TagMap map = new TagMap.Builder(3).build();
            assertThat(map).isSameAs(TagMap.EMPTY);
        }

        @Test
        void expansionAndSorting() {
            TagMap map = new TagMap.Builder(1)
                    .put("d", "4")
                    .put("b", "2")
                    .put("c", "3")
                    .put("a", "1")
                    .build();
            assertThat(map)
                    .hasSize(4)
                    .containsExactlyInAnyOrderEntriesOf(ImmutableMap.of("a", "1", "b", "2", "c", "3", "d", "4"));
            assertThat(map.firstKey()).isEqualTo("a");
            assertThat(map.lastKey()).isEqualTo("d");
        }
    }

    @Nested
    class WithEntry {

        @Test
        void updateExisting() {
            TagMap original = TagMap.of(Collections.singletonMap("foo", "bar"));
            assertThat(original.withEntry("foo", "bar")).isSameAs(original);

            TagMap updated = original.withEntry("foo", "baz");
            assertThat(updated)
                    .hasSize(1)
                    .containsEntry("foo", "baz")
                    .isNotSameAs(original)
                    .isNotEqualTo(original);
            assertThat(original).containsEntry("foo", "bar");
        }

        @Test
        void insertPositions() {
            TagMap base = TagMap.of(ImmutableMap.of("b", "2", "d", "4"));

            TagMap withBefore = base.withEntry("a", "1");
            assertThat(withBefore.firstKey()).isEqualTo("a");
            assertThat(withBefore).hasSize(3);

            TagMap withBetween = base.withEntry("c", "3");
            assertThat(withBetween).hasSize(3).containsEntry("c", "3");

            TagMap withAfter = base.withEntry("e", "5");
            assertThat(withAfter.lastKey()).isEqualTo("e");
            assertThat(withAfter).hasSize(3);

            TagMap fromEmpty = TagMap.EMPTY.withEntry("a", "1");
            assertThat(fromEmpty).hasSize(1).containsEntry("a", "1");
        }
    }

    @Nested
    class MapMethods {

        @Test
        void naturalOrder() {
            assertThat(TagMap.isNaturalOrder(Ordering.natural())).isTrue();
            assertThat(TagMap.isNaturalOrder(Comparator.naturalOrder())).isTrue();

            assertThat(TagMap.isNaturalOrder(Comparator.reverseOrder())).isFalse();
            Comparator<?> immutableSortedMapComparator = ImmutableSortedMap.naturalOrder()
                    .put("a", "b")
                    .put("c", "d")
                    .buildOrThrow()
                    .comparator();
            assertThat(TagMap.isNaturalOrder(immutableSortedMapComparator))
                    .as("Expected ImmutableSortedMap comparator %s to be natural", immutableSortedMapComparator)
                    .isTrue();
        }

        @Test
        void forEach() {
            TagMap map = TagMap.of(ImmutableMap.of("b", "2", "a", "1"));
            ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
            map.forEach(builder::put);
            assertThat(builder.buildOrThrow()).containsExactlyInAnyOrderEntriesOf(ImmutableMap.of("a", "1", "b", "2"));
        }

        @Test
        void testToString() {
            assertThat(TagMap.EMPTY.toString()).isEqualTo("{}");
            assertThat(TagMap.of(Collections.singletonMap("k", "v")).toString()).isEqualTo("{k=v}");
            assertThat(TagMap.of(ImmutableMap.of("a", "1", "b", "2")).toString())
                    .isEqualTo("{a=1, b=2}");
        }

        @Test
        void testEquals() {
            TagMap map = TagMap.of(ImmutableMap.of("a", "1", "b", "2"));
            assertThat(map.equals(map)).isTrue();
            assertThat(map).containsExactlyInAnyOrderEntriesOf(TagMap.of(ImmutableMap.of("a", "1", "b", "2")));
            assertThat(map).containsExactlyInAnyOrderEntriesOf(ImmutableMap.of("a", "1", "b", "2"));

            assertThat(map).isNotEqualTo(TagMap.of(ImmutableMap.of("a", "1", "b", "different")));
            assertThat(map).isNotEqualTo("not a map");
            assertThat(map).isNotEqualTo(ImmutableMap.of("a", "1"));
            assertThat(map).isNotEqualTo(ImmutableMap.of("a", "1", "b", "different"));
        }

        @Test
        void testHashCode() {
            assertThat(TagMap.EMPTY).hasSameHashCodeAs(ImmutableMap.of());
            assertThat(TagMap.of(ImmutableMap.of("a", "1", "b", "2")))
                    .hasSameHashCodeAs(ImmutableMap.of("a", "1", "b", "2"));
        }

        @Test
        void comparator() {
            assertThat(TagMap.EMPTY.comparator()).isEqualTo(Comparator.naturalOrder());
        }

        @Test
        void containsKeyAndValue() {
            TagMap map = TagMap.of(ImmutableMap.of("a", "1"));
            assertThat(map.containsKey("a")).isTrue();
            assertThat(map.containsKey("missing")).isFalse();
            assertThat(map.containsValue("1")).isTrue();
            assertThat(map.containsValue("missing")).isFalse();
        }

        @Test
        void getMissing() {
            assertThat(TagMap.of(ImmutableMap.of("a", "1")).get("b")).isNull();
        }

        @Test
        void keySetAndValues() {
            TagMap map = TagMap.of(ImmutableMap.of("c", "3", "a", "1", "b", "2"));
            assertThat(map.keySet()).containsExactly("a", "b", "c");
            assertThat(map.values()).containsExactly("1", "2", "3");
        }
    }

    @Nested
    class SortedMapViews {

        @Test
        void subMap() {
            TagMap map = TagMap.of(ImmutableMap.of("a", "1", "b", "2", "c", "3", "d", "4"));
            assertThat(map.subMap("b", "d")).containsExactlyInAnyOrderEntriesOf(ImmutableMap.of("b", "2", "c", "3"));
            assertThat(map.subMap("e", "f")).isEmpty();
            assertThat(TagMap.of(ImmutableMap.of("a", "1", "c", "3")).subMap("b", "c"))
                    .isEmpty();
        }

        @Test
        void headMap() {
            TagMap map = TagMap.of(ImmutableMap.of("a", "1", "b", "2", "c", "3"));
            assertThat(map.headMap("c")).containsExactlyInAnyOrderEntriesOf(ImmutableMap.of("a", "1", "b", "2"));
            assertThat(TagMap.of(ImmutableMap.of("b", "2", "c", "3")).headMap("a"))
                    .isEmpty();
        }

        @Test
        void tailMap() {
            TagMap map = TagMap.of(ImmutableMap.of("a", "1", "b", "2", "c", "3"));
            assertThat(map.tailMap("b")).containsExactlyInAnyOrderEntriesOf(ImmutableMap.of("b", "2", "c", "3"));
            assertThat(TagMap.of(ImmutableMap.of("a", "1", "b", "2")).tailMap("c"))
                    .isEmpty();
        }

        @Test
        void firstLastKeyOnEmpty() {
            assertThatThrownBy(TagMap.EMPTY::firstKey).isInstanceOf(NoSuchElementException.class);
            assertThatThrownBy(TagMap.EMPTY::lastKey).isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    class Immutability {

        @Test
        void mapMutatorsThrow() {
            assertThatThrownBy(() -> TagMap.EMPTY.put("k", "v")).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> TagMap.EMPTY.putAll(ImmutableMap.of("k", "v")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> TagMap.EMPTY.remove("k")).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(TagMap.EMPTY::clear).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void entrySetMutatorsThrow() {
            assertThatThrownBy(() -> TagMap.EMPTY.entrySet().add(new SimpleImmutableEntry<>("k", "v")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> TagMap.EMPTY.entrySet().remove(new SimpleImmutableEntry<>("k", "v")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> TagMap.EMPTY.entrySet().addAll(List.of(new SimpleImmutableEntry<>("k", "v"))))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> TagMap.EMPTY.entrySet().retainAll(List.of()))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> TagMap.EMPTY.entrySet().removeAll(List.of()))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> TagMap.EMPTY.entrySet().clear()).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class EntrySetTest {

        @Test
        void contains() {
            TagMap map = TagMap.of(ImmutableMap.of("a", "1"));
            assertThat(map.entrySet().contains(new SimpleImmutableEntry<>("a", "1")))
                    .isTrue();
            assertThat(map.entrySet().contains(new SimpleImmutableEntry<>("a", "2")))
                    .isFalse();
        }

        @Test
        void toArray() {
            TagMap map = TagMap.of(ImmutableMap.of("a", "1", "b", "2"));
            Object[] array = map.entrySet().toArray();
            assertThat(array).hasSize(2);
            assertThat(array[0]).isEqualTo(new SimpleImmutableEntry<>("a", "1"));
            assertThat(array[1]).isEqualTo(new SimpleImmutableEntry<>("b", "2"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void toArrayTyped() {
            TagMap map = TagMap.of(ImmutableMap.of("a", "1", "b", "2"));
            Map.Entry<String, String>[] smallResult = map.entrySet().toArray(new Map.Entry[0]);
            assertThat(smallResult).hasSize(2);
            assertThat(smallResult[0]).isEqualTo(new SimpleImmutableEntry<>("a", "1"));

            TagMap single = TagMap.of(ImmutableMap.of("a", "1"));
            Map.Entry<String, String>[] largeArray = new Map.Entry[3];
            largeArray[1] = new SimpleImmutableEntry<>("stale", "stale");
            largeArray[2] = new SimpleImmutableEntry<>("stale", "stale");
            Map.Entry<String, String>[] largeResult = single.entrySet().toArray(largeArray);
            assertThat(largeResult).isSameAs(largeArray);
            assertThat(largeResult[0]).isEqualTo(new SimpleImmutableEntry<>("a", "1"));
            assertThat(largeResult[1]).isNull();
            assertThat(largeResult[2]).isNull();
        }

        @Test
        void containsAll() {
            TagMap map = TagMap.of(ImmutableMap.of("a", "1", "b", "2"));
            assertThat(map.entrySet()
                            .containsAll(List.of(
                                    new SimpleImmutableEntry<>("a", "1"), new SimpleImmutableEntry<>("b", "2"))))
                    .isTrue();
            assertThat(TagMap.of(ImmutableMap.of("a", "1"))
                            .entrySet()
                            .containsAll(List.of(
                                    new SimpleImmutableEntry<>("a", "1"), new SimpleImmutableEntry<>("b", "2"))))
                    .isFalse();
        }

        @Test
        void testEquals() {
            TagMap map = TagMap.of(ImmutableMap.of("a", "1"));
            assertThat(map.entrySet().equals(map.entrySet())).isTrue();
            assertThat(map.entrySet())
                    .containsExactlyInAnyOrderElementsOf(
                            TagMap.of(ImmutableMap.of("a", "1")).entrySet());
            assertThat(map.entrySet())
                    .containsExactlyInAnyOrderElementsOf(
                            ImmutableMap.of("a", "1").entrySet());

            assertThat(map.entrySet())
                    .isNotEqualTo(TagMap.of(ImmutableMap.of("a", "2")).entrySet());
            assertThat(map.entrySet()).isNotEqualTo("not a set");
            assertThat(map.entrySet())
                    .isNotEqualTo(ImmutableMap.of("a", "1", "b", "2").entrySet());
        }

        @Test
        void hashCodeAndToString() {
            TagMap map = TagMap.of(ImmutableMap.of("a", "1", "b", "2"));
            assertThat(map.entrySet().hashCode())
                    .isEqualTo(ImmutableMap.of("a", "1", "b", "2").entrySet().hashCode());
            assertThat(map.entrySet().toString()).contains("a", "1");
        }
    }

    @Nested
    class IteratorTest {

        @Test
        void traversal() {
            TagMap map = TagMap.of(ImmutableMap.of("b", "2", "a", "1"));
            Iterator<Map.Entry<String, String>> it = map.entrySet().iterator();
            assertThat(it.hasNext()).isTrue();
            assertThat(it.next()).isEqualTo(new SimpleImmutableEntry<>("a", "1"));
            assertThat(it.hasNext()).isTrue();
            Map.Entry<String, String> second = it.next();
            assertThat(second).isEqualTo(new SimpleImmutableEntry<>("b", "2"));
            assertThat(it.hasNext()).isFalse();
            assertThatThrownBy(it::next).isInstanceOf(NoSuchElementException.class);
            assertThatThrownBy(it::remove).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class TagEntryTest {

        @Test
        void properties() {
            TagMap map = TagMap.of(ImmutableMap.of("k", "v"));
            Map.Entry<String, String> entry = map.entrySet().iterator().next();
            assertThat(entry.toString()).isEqualTo("{k=v}");
            assertThat(entry.equals(entry)).isTrue();
            assertThat(entry).isNotEqualTo("not an entry");
            assertThat(entry.hashCode()).isEqualTo(new SimpleImmutableEntry<>("k", "v").hashCode());
            assertThatThrownBy(() -> entry.setValue("new")).isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
