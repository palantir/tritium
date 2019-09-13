/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.tracing;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NullAway") // mock injection
public class ReflectiveTracerTest {

    @Test
    public void createValid() throws Exception {
        Method startMethod = MockTracer.class.getMethod("start", String.class);
        Method completeMethod = MockTracer.class.getMethod("stop");
        ReflectiveTracer reflectiveTracer = new ReflectiveTracer(startMethod, completeMethod);
        reflectiveTracer.startSpan("test");
        reflectiveTracer.completeSpan();
    }

    @Test
    public void createInvalid() throws Exception {
        Method validStartMethod = MockTracer.class.getMethod("start", String.class);
        Method validCompleteMethod = MockTracer.class.getMethod("stop");
        Method invalidStartMethod = MockTracer.class.getMethod("badStart", Integer.class);
        Method invalidCompleteMethod = MockTracer.class.getMethod("badStop", String.class);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ReflectiveTracer(invalidStartMethod, validCompleteMethod))
                .withMessageStartingWith("startSpan method should take 1 String argument:")
                .withMessageContaining("method=public static void ")
                .withMessageContaining("MockTracer.badStart(java.lang.Integer)")
                .withMessageContaining("argumentTypes=[java.lang.Integer]");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ReflectiveTracer(validStartMethod, invalidCompleteMethod))
                .withMessageStartingWith("completeSpan method should take 0 arguments: ")
                .withMessageContaining("method=public static void ")
                .withMessageContaining("MockTracer.badStop(java.lang.String)")
                .withMessageContaining("argumentTypes=[java.lang.String]}");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ReflectiveTracer(invalidStartMethod, invalidCompleteMethod))
                .withMessageStartingWith("startSpan method should take 1 String argument: ")
                .withMessageContaining("method=public static void ")
                .withMessageContaining("MockTracer.badStart(java.lang.Integer)")
                .withMessageContaining("argumentTypes=[java.lang.Integer]");
    }

    public static final class MockTracer {
        private MockTracer() {}

        public static void start(String unused) {}

        public static void stop() {}

        public static void badStart(Integer unused) {}

        public static void badStop(String unused) {}
    }

}
