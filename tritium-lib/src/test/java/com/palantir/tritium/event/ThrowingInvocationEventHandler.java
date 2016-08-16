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
