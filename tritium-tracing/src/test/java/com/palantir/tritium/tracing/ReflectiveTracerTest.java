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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
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
                .withMessage("startSpan method should take 1 String argument, was [java.lang.Integer]");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ReflectiveTracer(validStartMethod, invalidCompleteMethod))
                .withMessage("completeSpan method should take 0 arguments, was [java.lang.String]");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ReflectiveTracer(invalidStartMethod, invalidCompleteMethod))
                .withMessage("startSpan method should take 1 String argument, was [java.lang.Integer]");
    }

    public static final class MockTracer {
        public static void start(String name) {}

        public static void stop() {}

        public static void badStart(Integer id) {}

        public static void badStop(String name) {}
    }

}
