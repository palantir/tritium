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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

final class InstrumentationFiltersTest {

    private final TestInterface instance = mock(TestImplementation.class);
    private final Method method = testMethod();
    private final Object[] args = new Object[0];

    @Test
    void testInstrumentAll() {
        assertThat(InstrumentationFilters.INSTRUMENT_ALL.shouldInstrument(instance, method, args)).isTrue();
    }

    @Test
    void testInstrumentNone() {
        assertThat(InstrumentationFilters.INSTRUMENT_NONE.shouldInstrument(instance, method, args)).isFalse();
    }

    @Test
    void testFromTrueSupplier() {
        InstrumentationFilter filter = InstrumentationFilters.from((java.util.function.BooleanSupplier) () -> true);
        assertThat(filter.shouldInstrument(instance, method, args)).isTrue();
    }

    @Test
    void testFromFalseSupplier() {
        InstrumentationFilter filter = InstrumentationFilters.from((java.util.function.BooleanSupplier) () -> false);
        assertThat(filter.shouldInstrument(instance, method, args)).isFalse();
    }

    private static Method testMethod() {
        try {
            return TestInterface.class.getDeclaredMethod("test");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
