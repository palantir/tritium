/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.event;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public final class NoOpInvocationEventHandlerTest {

    @Test
    public void testIsEnabled() {
        assertThat(NoOpInvocationEventHandler.INSTANCE.isEnabled(), equalTo(false));
    }

}
