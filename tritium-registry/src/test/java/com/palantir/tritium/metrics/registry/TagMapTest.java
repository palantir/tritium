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

import com.google.common.collect.ImmutableSortedMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class TagMapTest {

    @Test
    void testEmpty() {
        TagMap map = new TagMap(ImmutableSortedMap.of());
        assertThat(map).isEmpty();
        assertThat(map).hasSize(0);
        assertThat(map.entrySet()).isEmpty();
        assertThat(map.entrySet()).hasSize(0);
        assertThat(map.entrySet().iterator()).isExhausted();
    }

    @Test
    void testSingleton() {
        TagMap map = new TagMap(Collections.singletonMap("foo", "bar"));
        assertThat(map).isNotEmpty();
        assertThat(map).hasSize(1);
        assertThat(map).containsEntry("foo", "bar");
        assertThat(map.get("foo")).isEqualTo("bar");
        assertThat(map.entrySet()).isNotEmpty();
        assertThat(map.entrySet()).hasSize(1);
        assertThat(map.entrySet().iterator()).hasNext();
        assertThat(map.entrySet().iterator().next()).isEqualTo(new SimpleImmutableEntry<>("foo", "bar"));
    }
}
