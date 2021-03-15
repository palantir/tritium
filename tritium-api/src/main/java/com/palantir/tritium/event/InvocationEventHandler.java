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

/**
 * Interface for handing invocation events.
 * Back-compat bridge type, will be removed in future major version bump.
 *
 * @see java.lang.reflect.InvocationHandler
 * @param <C> invocation context
 * @deprecated use {@link com.palantir.tritium.api.event.InvocationEventHandler}
 */
@Deprecated
public interface InvocationEventHandler<C extends InvocationContext>
        extends com.palantir.tritium.api.event.InvocationEventHandler<C> {}
