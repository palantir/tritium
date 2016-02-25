/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.event;

import java.lang.reflect.Method;
import javax.annotation.Nullable;

@SuppressWarnings("checkstyle:designforextension")
class ThrowingInvocationEventHandler implements InvocationEventHandler<InvocationContext> {

    private final boolean isEnabled;

    ThrowingInvocationEventHandler(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    @Override
    public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
        throw new IllegalStateException("preInvocation always throws");
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        throw new IllegalStateException("onSuccess always throws");

    }

    @Override
    public void onFailure(@Nullable InvocationContext context, Throwable cause) {
        throw new IllegalStateException("onFailure always throws");
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }
}
