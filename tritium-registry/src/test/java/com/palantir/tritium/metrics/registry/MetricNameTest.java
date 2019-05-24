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

import org.junit.Test;

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

        assertThat(one).isEqualTo(two);
        assertThat(two).isEqualTo(one);
        assertThat(one).isEqualByComparingTo(two);
        assertThat(one).usingComparator(MetricNames.metricNameComparator).isEqualByComparingTo(two);
    }

    @Test
    public void compareNameOrder() {
        MetricName one = MetricName.builder()
                .safeName("a")
                .putSafeTags("key1", "value1")
                .putSafeTags("key2", "value2")
                .build();
        MetricName two = MetricName.builder()
                .safeName("b")
                .putSafeTags("key1", "value1")
                .putSafeTags("key2", "value2")
                .build();

        assertThat(one).isLessThan(two);
        assertThat(two).isGreaterThan(one);
    }

    @Test
    public void compareSameName() {
        MetricName one = MetricName.builder()
                .safeName("test")
                .putSafeTags("a", "value")
                .build();
        MetricName two = MetricName.builder()
                .safeName("test")
                .putSafeTags("b", "value")
                .build();

        assertThat(one).isLessThan(two);
        assertThat(two).isGreaterThan(one);
    }

    @Test
    public void compareTagsName() {
        MetricName one = MetricName.builder()
                .safeName("a")
                .putSafeTags("keyA", "value1")
                .putSafeTags("keyB", "value2")
                .putSafeTags("keyB", "value1")
                .build();
        MetricName two = MetricName.builder()
                .safeName("a")
                .putSafeTags("keyC", "value2")
                .putSafeTags("keyA", "value1")
                .putSafeTags("keyB", "value1")
                .build();

        assertThat(one).isLessThan(two);
        assertThat(two).isGreaterThan(one);
    }

    @Test
    public void compareTagsSize() {
        MetricName one = MetricName.builder()
                .safeName("a")
                .build();
        MetricName two = MetricName.builder()
                .safeName("a")
                .putSafeTags("keyA", "value1")
                .build();

        assertThat(one).isLessThan(two);
        assertThat(two).isGreaterThan(one);
    }

    @Test
    public void compareTagsValue() {
        MetricName one = MetricName.builder()
                .safeName("a")
                .putSafeTags("keyA", "value1")
                .putSafeTags("keyB", "value2")
                .build();
        MetricName two = MetricName.builder()
                .safeName("a")
                .putSafeTags("keyB", "value3")
                .putSafeTags("keyA", "value1")
                .build();

        assertThat(one).isLessThan(two);
        assertThat(two).isGreaterThan(one);
    }
}
