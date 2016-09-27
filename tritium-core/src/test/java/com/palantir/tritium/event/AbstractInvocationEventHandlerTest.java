/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.Iterator;
import java.util.Map.Entry;
import org.junit.Before;
import org.junit.Test;

public class AbstractInvocationEventHandlerTest {

    @Before
    public void setUp() {
        System.clearProperty("instrument");
        Iterator<Entry<Object, Object>> props = System.getProperties().entrySet().iterator();
        while (props.hasNext()) {
            Entry<Object, Object> entry = props.next();
            if (entry.getKey().toString().startsWith("instrument")) {
                props.remove();
            }
        }
    }

    @Test
    public void testSystemPropertySupplierEnabledByDefault() {
        assertThat(AbstractInvocationEventHandler
                .getSystemPropertySupplier(CompositeInvocationEventHandler.class)
                .asBoolean(),
                equalTo(true));
    }

    @Test
    public void testSystemPropertySupplierInstrumentFalse() {
        System.setProperty("instrument", "false");
        assertThat(AbstractInvocationEventHandler
                .getSystemPropertySupplier(CompositeInvocationEventHandler.class)
                .asBoolean(),
                equalTo(false));
    }

    @Test
    public void testSystemPropertySupplierInstrumentTrue() {
        System.setProperty("instrument", "true");
        assertThat(AbstractInvocationEventHandler
                .getSystemPropertySupplier(CompositeInvocationEventHandler.class)
                .asBoolean(),
                equalTo(true));
    }

    @Test
    public void testSystemPropertySupplierInstrumentClassFalse() {
        System.setProperty("instrument." + CompositeInvocationEventHandler.class.getName(), "false");
        assertThat(AbstractInvocationEventHandler
                .getSystemPropertySupplier(CompositeInvocationEventHandler.class)
                .asBoolean(),
                equalTo(false));
    }

    @Test
    public void testSystemPropertySupplierInstrumentClassTrue() {
        System.clearProperty("instrument");
        System.setProperty("instrument." + CompositeInvocationEventHandler.class.getName(), "true");
        assertThat(AbstractInvocationEventHandler
                .getSystemPropertySupplier(CompositeInvocationEventHandler.class)
                .asBoolean(),
                equalTo(true));
    }

}
