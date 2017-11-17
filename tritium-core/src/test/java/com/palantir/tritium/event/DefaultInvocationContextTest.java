/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class DefaultInvocationContextTest {

    @Test
    public void testFactory() throws Exception {
        InvocationContext context = DefaultInvocationContext.of(this, Object.class.getDeclaredMethod("toString"), null);

        assertThat(context.getInstance()).isEqualTo(this);
        assertThat(context.getArgs()).isEqualTo(new Object[0]);
        assertThat(context.getMethod()).isEqualTo(Object.class.getDeclaredMethod("toString"));

        String toString = context.toString();
        assertThat(toString).contains("startTimeNanos");
        assertThat(toString).contains("instance");
        assertThat(toString).contains("method");
        assertThat(toString).contains("args");
    }

}
