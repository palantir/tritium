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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import java.util.Map;
import java.util.SortedMap;
import java.util.function.Function;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

@SuppressWarnings("JdkObsolete")
class ExtraEntrySortedMapTest {

    @Property(tries = 10_000, seed = "3619154246571270871")
    void check_ExtraEntrySortedMap_has_the_same_behaviour_as_an_ImmutableSortedMap_with_an_extra_entry(
            @ForAll @Size(max = 10) Map<Short, Byte> initialValues,
            @ForAll Short extraKey,
            @ForAll Byte extraValue,
            @ForAll @IntRange(max = 5) int paramKeyIndex1,
            @ForAll @IntRange(min = 5, max = 10) int paramKeyIndex2) {

        Assume.that(!initialValues.containsKey(extraKey));
        Assume.that(paramKeyIndex1 <= paramKeyIndex2);
        Assume.that(paramKeyIndex1 < initialValues.size());
        Assume.that(paramKeyIndex2 < initialValues.size());

        SortedMap<Short, Byte> base = ImmutableSortedMap.copyOf(initialValues);

        SortedMap<Short, Byte> guavaWithExtra = ImmutableSortedMap.<Short, Byte>naturalOrder()
                .putAll(base)
                .put(extraKey, extraValue)
                .build();

        SortedMap<Short, Byte> extraMap = new ExtraEntrySortedMap<>(base, extraKey, extraValue);

        assertThat(extraMap).isEqualTo(guavaWithExtra);
        assertThat(extraMap).hasSameHashCodeAs(guavaWithExtra);

        Short paramKey1 = Iterables.get(guavaWithExtra.keySet(), paramKeyIndex1);
        Byte paramValue1 = guavaWithExtra.get(paramKey1);
        Short paramKey2 = Iterables.get(guavaWithExtra.keySet(), paramKeyIndex2);

        Map<String, Function<SortedMap<Short, Byte>, Object>> methodCalls =
                ImmutableMap.<String, Function<SortedMap<Short, Byte>, Object>>builder()
                        .put("subMap", sortedMap -> sortedMap.subMap(paramKey1, paramKey2))
                        .put("headMap", sortedMap -> sortedMap.headMap(paramKey1))
                        .put("tailMap", sortedMap -> sortedMap.tailMap(paramKey1))
                        .put("containsKey", sortedMap -> sortedMap.containsKey(paramKey1))
                        .put("containsValue", sortedMap -> sortedMap.containsValue(paramValue1))
                        .put("get", sortedMap -> sortedMap.get(paramKey1))
                        .put("firstKey", SortedMap::firstKey)
                        .put("lastKey", SortedMap::lastKey)
                        .put("size", SortedMap::size)
                        .put("isEmpty", SortedMap::isEmpty)
                        .put("keySet", SortedMap::keySet)
                        .put("entrySet", SortedMap::entrySet)
                        .put("values", shortByteSortedMap -> ImmutableList.copyOf(shortByteSortedMap.values()))
                        .build();

        methodCalls.forEach((methodCallName, methodCall) -> {
            assertThat(methodCall.apply(extraMap))
                    .describedAs(
                            "%s() applied to both extra map %s and guava map %s",
                            methodCallName, extraMap, guavaWithExtra)
                    .isEqualTo(methodCall.apply(guavaWithExtra));
        });
    }
}
