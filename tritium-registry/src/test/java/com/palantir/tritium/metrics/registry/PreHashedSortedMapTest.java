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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.google.common.collect.ImmutableSortedMap;
import org.junit.jupiter.api.Test;

class PreHashedSortedMapTest {

    @Test
    void testEqualsHashCode() {
        ImmutableSortedMap<String, Integer> map = generate(1_000);
        PreHashedSortedMap<String, Integer> preHashedSortedMap = new PreHashedSortedMap<>(map);
        assertThat(preHashedSortedMap)
                .hasSameHashCodeAs(map)
                .isEqualTo(map)
                .containsExactlyEntriesOf(map)
                .usingDefaultComparator()
                .isEqualTo(map);
    }

    private static ImmutableSortedMap<String, Integer> generate(int count) {
        ImmutableSortedMap.Builder<String, Integer> builder = ImmutableSortedMap.naturalOrder();
        for (int i = 0; i < count; i++) {
            builder.put(Integer.toString(i), i);
        }
        return builder.build();
    }
}
