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
                "b", "b1",
                "a", "a1")))
                .isEqualTo("test[a:a1,b:b1]");
    }

    @Test
    public void generateInvalid() {
        assertThatThrownBy(() -> TaggedMetric.of(null, Collections.emptyMap()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name");

        assertThatThrownBy(() -> TaggedMetric.of("test", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tags");

    }

}
