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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Elements annotated with {@link PerformanceSensitive} indicate that there are performance considerations to balance
 * when modifying this element.
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.MODULE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PerformanceSensitive {

    /**
     * This element is sensitive to object allocations.
     */
    String Allocations = "Allocations";

    /**
     * This element is sensitive to cache access patterns.
     */
    String Cache = "Cache";

    /**
     * This element is sensitive to overall latency.
     */
    String Latency = "Latency";

    /**
     * This element is sensitive to overall throughput.
     */
    String Throughput = "Throughput";

    /**
     * The reasons this element is considered performance sensitive.
     */
    String[] value();
}
