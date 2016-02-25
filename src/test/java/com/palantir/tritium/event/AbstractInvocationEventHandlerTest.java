/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.event;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.Iterator;
import java.util.Map.Entry;
import org.junit.Before;
import org.junit.Test;

public final class AbstractInvocationEventHandlerTest {

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
                .getAsBoolean(),
                equalTo(true));
    }

    @Test
    public void testSystemPropertySupplierInstrumentFalse() {
        System.setProperty("instrument", "false");
        assertThat(AbstractInvocationEventHandler
                .getSystemPropertySupplier(CompositeInvocationEventHandler.class)
                .getAsBoolean(),
                equalTo(false));
    }

    @Test
    public void testSystemPropertySupplierInstrumentTrue() {
        System.setProperty("instrument", "true");
        assertThat(AbstractInvocationEventHandler
                .getSystemPropertySupplier(CompositeInvocationEventHandler.class)
                .getAsBoolean(),
                equalTo(true));
    }

    @Test
    public void testSystemPropertySupplierInstrumentClassFalse() {
        System.setProperty("instrument." + CompositeInvocationEventHandler.class.getName(), "false");
        assertThat(AbstractInvocationEventHandler
                .getSystemPropertySupplier(CompositeInvocationEventHandler.class)
                .getAsBoolean(),
                equalTo(false));
    }

    @Test
    public void testSystemPropertySupplierInstrumentClassTrue() {
        System.clearProperty("instrument");
        System.setProperty("instrument." + CompositeInvocationEventHandler.class.getName(), "true");
        assertThat(AbstractInvocationEventHandler
                .getSystemPropertySupplier(CompositeInvocationEventHandler.class)
                .getAsBoolean(),
                equalTo(true));
    }

}
