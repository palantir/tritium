/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

/**
 * Represents the state when an invocation event occurred.
 * Backward-compatibility bridge type, will be removed in future major version bump.
 * @deprecated use {@link com.palantir.tritium.v1.api.event.InvocationContext}
 */
@Deprecated // remove post 1.0
public interface InvocationContext extends com.palantir.tritium.v1.api.event.InvocationContext {

    /**
     * Returns the invocation start time in nanoseconds local to this machine.
     *
     * @return start time
     * @deprecated use {@link com.palantir.tritium.v1.api.event.InvocationContext#getStartTimeNanos()}
     */
    @Override
    @Deprecated // remove post 1.0
    long getStartTimeNanos();

    /**
     * Returns the instance a method was invoked upon.
     *
     * @return instance invoked or null if a static method
     * @deprecated use {@link com.palantir.tritium.v1.api.event.InvocationContext#getInstance()}
     */
    @Nullable
    @Override
    @Deprecated // remove post 1.0
    Object getInstance();

    /**
     * Returns the method invoked.
     *
     * @return method invoked
     * @deprecated use {@link com.palantir.tritium.v1.api.event.InvocationContext#getMethod()}
     */
    @Override
    @Deprecated // remove post 1.0
    Method getMethod();

    /**
     * Returns the array of arguments for the specified invocation.
     *
     * @return arguments
     * @deprecated use {@link com.palantir.tritium.v1.api.event.InvocationContext#getArgs()}
     */
    @Override
    @Deprecated // remove post 1.0
    Object[] getArgs();
}
