/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.test.event;

import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tritium.api.event.InvocationContext;
import com.palantir.tritium.api.event.InvocationEventHandler;
import java.lang.reflect.Method;
import org.checkerframework.checker.nullness.qual.Nullable;

@SuppressWarnings("DesignForExtension")
public class ThrowingInvocationEventHandler implements InvocationEventHandler<InvocationContext> {

    private final boolean isEnabled;

    public ThrowingInvocationEventHandler(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    @Override
    public InvocationContext preInvocation(
            @NonNull Object _instance, @NonNull Method _method, @NonNull Object[] _args) {
        throw new SafeIllegalStateException("preInvocation always throws");
    }

    @Override
    public void onSuccess(@Nullable InvocationContext _context, @Nullable Object _result) {
        throw new SafeIllegalStateException("onSuccess always throws");
    }

    @Override
    public void onFailure(@Nullable InvocationContext _context, @NonNull Throwable _cause) {
        throw new SafeIllegalStateException("onFailure always throws");
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }
}
