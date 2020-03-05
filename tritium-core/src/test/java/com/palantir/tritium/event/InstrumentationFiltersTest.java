/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

import static com.palantir.tritium.event.InstrumentationFilters.INSTRUMENT_NON_FUTURE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

final class InstrumentationFiltersTest {

    private static final Object[] NO_ARGS = new Object[0];

    private final TestInterface instance = mock(TestImplementation.class);

    @Test
    void testInstrumentAll() {
        assertThat(InstrumentationFilters.INSTRUMENT_ALL.shouldInstrument(instance, method("test"), NO_ARGS))
                .isTrue();
    }

    @Test
    void testInstrumentNone() {
        assertThat(InstrumentationFilters.INSTRUMENT_NONE.shouldInstrument(instance, method("test"), NO_ARGS))
                .isFalse();
    }

    @Test
    void testFromTrueSupplier() {
        InstrumentationFilter filter = InstrumentationFilters.from((java.util.function.BooleanSupplier) () -> true);
        assertThat(filter.shouldInstrument(instance, method("test"), NO_ARGS)).isTrue();
    }

    @Test
    void testFromFalseSupplier() {
        InstrumentationFilter filter = InstrumentationFilters.from((java.util.function.BooleanSupplier) () -> false);
        assertThat(filter.shouldInstrument(instance, method("test"), NO_ARGS)).isFalse();
    }

    @Test
    void testSynchronousOnly() {
        assertThat(INSTRUMENT_NON_FUTURE.shouldInstrument(instance, method("test"), NO_ARGS))
                .isTrue();
        assertThat(INSTRUMENT_NON_FUTURE.shouldInstrument(instance, method("throwsThrowable"), NO_ARGS))
                .isTrue();
        assertThat(INSTRUMENT_NON_FUTURE.shouldInstrument(instance, method("throwsCheckedException"), NO_ARGS))
                .isTrue();

        assertThat(INSTRUMENT_NON_FUTURE.shouldInstrument(instance, method("future"), NO_ARGS))
                .isFalse();
        assertThat(INSTRUMENT_NON_FUTURE.shouldInstrument(instance, method("listenableFuture"), NO_ARGS))
                .isFalse();
        assertThat(INSTRUMENT_NON_FUTURE.shouldInstrument(instance, method("completableFuture"), NO_ARGS))
                .isFalse();
    }

    private static Method method(String methodName, Class<?>... parameterTypes) {
        try {
            return TestInterface.class.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
