/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.event;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public final class DefaultInvocationContextTest {

    @Test
    public void testFactory() throws Exception {
        InvocationContext context = DefaultInvocationContext.of(this, Object.class.getDeclaredMethod("toString"), null);

        assertThat(context.getInstance(), equalTo(this));
        assertThat(context.getArgs(), equalTo(new Object[0]));
        assertThat(context.getMethod(), equalTo(Object.class.getDeclaredMethod("toString")));

        String toString = context.toString();
        assertThat(toString, containsString("startTimeNanos"));
        assertThat(toString, containsString("instance"));
        assertThat(toString, containsString("method"));
        assertThat(toString, containsString("args"));
    }

}
