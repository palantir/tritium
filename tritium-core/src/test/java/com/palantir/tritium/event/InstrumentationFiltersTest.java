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

package com.palantir.tritium.event;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.lang.reflect.Method;
import org.junit.Test;

public class InstrumentationFiltersTest {

    private TestInterface instance = mock(TestImplementation.class);
    private Method method = testMethod();
    private Object[] args = new Object[0];

    @Test
    public void testInstrumentAll() throws Exception {
        assertThat(InstrumentationFilters.INSTRUMENT_ALL.shouldInstrument(instance, method, args)).isTrue();
    }

    @Test
    public void testInstrumentNone() throws Exception {
        assertThat(InstrumentationFilters.INSTRUMENT_NONE.shouldInstrument(instance, method, args)).isFalse();
    }

    @Test
    public void testFromTrueSupplier() throws Exception {
        InstrumentationFilter filter = InstrumentationFilters.from(BooleanSupplier.TRUE);
        assertThat(filter.shouldInstrument(instance, method, args)).isTrue();
    }

    @Test
    public void testFromFalseSupplier() throws Exception {
        InstrumentationFilter filter = InstrumentationFilters.from(BooleanSupplier.FALSE);
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
