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
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.integers;
import static org.quicktheories.generators.SourceDSL.maps;
import static org.quicktheories.generators.SourceDSL.strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import java.util.Map;
import java.util.SortedMap;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.quicktheories.core.Gen;

@SuppressWarnings("JdkObsolete") // Test uses SortedMap
class ExtraEntrySortedMapTest {

    @Test
    void check_TagMap_has_the_same_behaviour_as_an_ImmutableSortedMap_with_an_extra_entry() {
        Gen<String> stringGen = strings().betweenCodePoints('A', 'z').ofLengthBetween(1, 10);
        Gen<Map<String, String>> mapGen = maps().of(stringGen, stringGen).ofSizeBetween(0, 10);
        Gen<Input> inputGen = rnd -> {
            Map<String, String> map = mapGen.generate(rnd);
            if (map.isEmpty()) {
                return new Input(map, 0, 0);
            }
            int size = map.size();
            Integer index1 = integers().from(0).upTo(size).generate(rnd);
            Integer index2 = integers().from(index1).upTo(size).generate(rnd);
            return new Input(map, index1, index2);
        };

        qt().withExamples(10_000)
                .forAll(inputGen, stringGen, stringGen)
                .assuming((input, extraKey, _extraValue) -> !input.map().containsKey(extraKey))
                .checkAssert((input, extraKey, extraValue) -> {
                    ImmutableSortedMap<String, String> base = ImmutableSortedMap.copyOf(input.map());

                    ImmutableSortedMap<String, String> guavaWithExtra =
                            ImmutableSortedMap.<String, String>naturalOrder()
                                    .putAll(base)
                                    .put(extraKey, extraValue)
                                    .buildOrThrow();

                    SortedMap<String, String> extraMap = TagMap.of(base).withEntry(extraKey, extraValue);
                    assertThat(extraMap)
                            .containsExactlyInAnyOrderEntriesOf(guavaWithExtra)
                            .hasSameHashCodeAs(guavaWithExtra);

                    String paramKey1 = Iterables.get(guavaWithExtra.keySet(), input.keyIndex1());
                    String paramValue1 = guavaWithExtra.get(paramKey1);
                    String paramKey2 = Iterables.get(guavaWithExtra.keySet(), input.keyIndex2());

                    ImmutableMap.<String, Function<SortedMap<String, String>, Object>>builder()
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
                            .buildOrThrow()
                            .forEach((methodCallName, methodCall) -> {
                                assertThat(methodCall.apply(extraMap))
                                        .describedAs(
                                                "%s() applied to both extra map %s and guava map %s",
                                                methodCallName, extraMap, guavaWithExtra)
                                        .isEqualTo(methodCall.apply(guavaWithExtra));
                            });
                });
    }

    private record Input(Map<String, String> map, int keyIndex1, int keyIndex2) {}
}
