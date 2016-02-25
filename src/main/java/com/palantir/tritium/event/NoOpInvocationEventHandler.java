/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.event;

import java.lang.reflect.Method;
import javax.annotation.Nullable;

/**
 * No-op implementation of {@link InvocationEventHandler}.
 */
public enum NoOpInvocationEventHandler implements InvocationEventHandler<InvocationContext> {

    INSTANCE;

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
        return DefaultInvocationContext.of(instance, method, args);
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        // no-op
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, Throwable cause) {
        // no-op
    }

}
