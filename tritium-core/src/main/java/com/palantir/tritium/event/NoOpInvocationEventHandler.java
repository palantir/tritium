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

import java.lang.reflect.Method;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** No-op implementation of {@link InvocationEventHandler}. */
public enum NoOpInvocationEventHandler implements InvocationEventHandler<InvocationContext> {
    INSTANCE;

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public InvocationContext preInvocation(@Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
        return DefaultInvocationContext.of(instance, method, args);
    }

    @Override
    public void onSuccess(@Nullable InvocationContext _context, @Nullable Object _result) {
        // no-op
    }

    @Override
    public void onFailure(@Nullable InvocationContext _context, @Nonnull Throwable _cause) {
        // no-op
    }
}
