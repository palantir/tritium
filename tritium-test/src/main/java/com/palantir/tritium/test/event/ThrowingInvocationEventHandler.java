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
import java.lang.reflect.Method;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings({"DesignForExtension", "deprecation", "UnnecessarilyFullyQualified"}) // backward-compatibility
public class ThrowingInvocationEventHandler
        implements com.palantir.tritium.event.InvocationEventHandler<com.palantir.tritium.event.InvocationContext> {

    private final boolean isEnabled;

    public ThrowingInvocationEventHandler(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    @Override
    public com.palantir.tritium.event.InvocationContext preInvocation(
            @Nonnull Object _instance, @Nonnull Method _method, @Nonnull Object[] _args) {
        throw new SafeIllegalStateException("preInvocation always throws");
    }

    @Override
    public void onSuccess(@Nullable com.palantir.tritium.event.InvocationContext _context, @Nullable Object _result) {
        throw new SafeIllegalStateException("onSuccess always throws");
    }

    @Override
    public void onFailure(@Nullable com.palantir.tritium.event.InvocationContext _context, @Nonnull Throwable _cause) {
        throw new SafeIllegalStateException("onFailure always throws");
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }
}
