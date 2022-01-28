/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import org.junit.jupiter.api.Test;

class RealMetricNameTest {

    @Test
    void testEquals() {
        MetricName a1 = RealMetricName.create("a");
        MetricName a2 = RealMetricName.create("a");
        MetricName a3 = RealMetricName.create(a1, "tag1", "value1");
        MetricName a4 = RealMetricName.create(a3, "tag2", "value2");
        MetricName b1 = RealMetricName.create("b");

        assertThat(a1)
                .hasSameHashCodeAs(a2)
                .isEqualTo(a2)
                .doesNotHaveSameHashCodeAs(a3)
                .isNotEqualTo(a3)
                .doesNotHaveSameHashCodeAs(a4)
                .isNotEqualTo(a4)
                .isEqualTo(a1)
                .doesNotHaveSameHashCodeAs(b1)
                .isNotEqualTo(b1)
                .isEqualTo(MetricName.builder().safeName("a").build());

        MetricName a5 = MetricName.builder()
                .safeName("a")
                .putSafeTags("tag1", "value1")
                .putSafeTags("tag2", "value2")
                .build();

        assertThat(a4).hasSameHashCodeAs(a5).isEqualTo(a5);
    }

    @Test
    void name() {
        MetricName one =
                MetricName.builder().safeName("one").putSafeTags("one", "two").build();
        MetricName two =
                MetricName.builder().safeName("two").putSafeTags("one", "two").build();

        assertThat(one)
                .isInstanceOf(RealMetricName.class)
                .doesNotHaveSameHashCodeAs(two)
                .isNotEqualTo(two);

        for (int i = 0; i < 1_000; i++) {
            int oneHash = one.hashCode();
            int twoHash = two.hashCode();
            assertThat(oneHash).isNotEqualTo(twoHash);
        }
    }
}
