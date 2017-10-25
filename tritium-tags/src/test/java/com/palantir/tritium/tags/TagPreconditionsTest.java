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

import org.junit.Test;

public class TagPreconditionsTest {

    @Test
    public void notBlank() {
        assertThat(TagPreconditions.isNullOrBlank("test")).isFalse();
        assertThat(TagPreconditions.isNullOrBlank(" test")).isFalse();
        assertThat(TagPreconditions.isNullOrBlank("test ")).isFalse();

        assertThat(TagPreconditions.isNotNullOrBlank("test")).isTrue();
        assertThat(TagPreconditions.isNotNullOrBlank(" test")).isTrue();
        assertThat(TagPreconditions.isNotNullOrBlank("test ")).isTrue();
    }

    @Test
    public void blank() {
        assertThat(TagPreconditions.isNullOrBlank(null)).isTrue();
        assertThat(TagPreconditions.isNullOrBlank("")).isTrue();
        assertThat(TagPreconditions.isNullOrBlank(" ")).isTrue();
        assertThat(TagPreconditions.isNullOrBlank("\n")).isTrue();

        assertThat(TagPreconditions.isNotNullOrBlank(null)).isFalse();
        assertThat(TagPreconditions.isNotNullOrBlank("")).isFalse();
        assertThat(TagPreconditions.isNotNullOrBlank(" ")).isFalse();
        assertThat(TagPreconditions.isNotNullOrBlank(" \t")).isFalse();
    }

}
