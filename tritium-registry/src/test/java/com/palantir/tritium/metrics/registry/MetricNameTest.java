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
                .build();
        MetricName two = MetricName.builder()
                .safeName("test")
                .putSafeTags("key", "value")
                .build();

        assertThat(one).isEqualTo(two);
        assertThat(two).isEqualTo(one);
        assertThat(one).isEqualByComparingTo(two);
        assertThat(one).usingComparator(MetricName.metricNameComparator).isEqualByComparingTo(two);
    }

    @Test
    public void compareNameOrder() {
        MetricName one = MetricName.builder()
                .safeName("a")
                .putSafeTags("key", "value")
                .build();
        MetricName two = MetricName.builder()
                .safeName("b")
                .putSafeTags("key", "value")
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
    public void compareSameDifferentTagOrder() {
        MetricName one = MetricName.builder()
                .safeName("test")
                .putSafeTags("a", "value")
                .putSafeTags("b", "value")
                .build();
        MetricName two = MetricName.builder()
                .safeName("test")
                .putSafeTags("b", "value")
                .putSafeTags("a", "value")
                .build();

        assertThat(one)
                .isEqualByComparingTo(two)
                .isEqualTo(two);
        assertThat(two)
                .isEqualByComparingTo(one)
                .isEqualTo(one);
    }
}
