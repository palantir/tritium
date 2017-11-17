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

import java.util.function.BooleanSupplier;
import org.junit.Before;
import org.junit.Test;

public class AbstractInvocationEventHandlerTest {

    @Before
    public void before() {
        System.clearProperty("instrument");
        System.getProperties().entrySet().removeIf(entry ->
                entry.getKey().toString().startsWith("instrument"));
        InstrumentationProperties.reload();
    }

    @Test
    public void testSystemPropertySupplierEnabledByDefault() {
        BooleanSupplier supplier = AbstractInvocationEventHandler
                .getSystemPropertySupplier(CompositeInvocationEventHandler.class);
        assertThat(supplier.getAsBoolean()).isTrue();
    }

    @Test
    public void testSystemPropertySupplierInstrumentFalse() {
        System.setProperty("instrument", "false");
        BooleanSupplier supplier = AbstractInvocationEventHandler
                .getSystemPropertySupplier(CompositeInvocationEventHandler.class);
        assertThat(supplier.getAsBoolean()).isFalse();
    }

    @Test
    public void testSystemPropertySupplierInstrumentTrue() {
        System.setProperty("instrument", "true");
        BooleanSupplier supplier = AbstractInvocationEventHandler
                .getSystemPropertySupplier(CompositeInvocationEventHandler.class);
        assertThat(supplier.getAsBoolean()).isTrue();
    }

    @Test
    public void testSystemPropertySupplierInstrumentClassFalse() {
        System.setProperty("instrument." + CompositeInvocationEventHandler.class.getName(), "false");
        BooleanSupplier supplier = AbstractInvocationEventHandler
                .getSystemPropertySupplier(CompositeInvocationEventHandler.class);
        assertThat(supplier.getAsBoolean()).isFalse();
    }

    @Test
    public void testSystemPropertySupplierInstrumentClassTrue() {
        System.clearProperty("instrument");
        System.setProperty("instrument." + CompositeInvocationEventHandler.class.getName(), "true");
        BooleanSupplier supplier = AbstractInvocationEventHandler
                .getSystemPropertySupplier(CompositeInvocationEventHandler.class);
        assertThat(supplier.getAsBoolean()).isTrue();
    }

}
