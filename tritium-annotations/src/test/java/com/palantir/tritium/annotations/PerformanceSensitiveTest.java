/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.annotations;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.tritium.annotations.PerformanceSensitive.Consideration;
import org.junit.jupiter.api.Test;

public class PerformanceSensitiveTest {

    @Test
    void annotationValues() {
        assertThat(expensive()).isEqualTo(999_000_000);
    }

    @PerformanceSensitive({
        Consideration.Allocations,
        Consideration.Cache,
        Consideration.Latency,
        Consideration.Throughput,
    })
    static long expensive() {
        long sum = 0;
        for (int i = 0; i < 1_000; i++) {
            for (int j = 0; j < 1_000; j++) {
                sum += i + j;
            }
        }
        return sum;
    }
}
