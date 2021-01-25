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

import com.palantir.tritium.api.event.InvocationContext;
import com.palantir.tritium.api.event.InvocationEventHandler;
import java.lang.reflect.Method;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** No-op implementation of {@link InvocationEventHandler}. */
public enum NoOpInvocationEventHandler implements InvocationEventHandler<InvocationContext> {
    INSTANCE;

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public InvocationContext preInvocation(@NonNull Object instance, @NonNull Method method, @NonNull Object[] args) {
        return DefaultInvocationContext.of(instance, method, args);
    }

    @Override
    public void onSuccess(@Nullable InvocationContext _context, @Nullable Object _result) {
        // no-op
    }

    @Override
    public void onFailure(@Nullable InvocationContext _context, @NonNull Throwable _cause) {
        // no-op
    }
}
