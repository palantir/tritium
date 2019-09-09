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

package com.palantir.tritium.proxy;

import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.event.InvocationEventHandler;

class InstrumentationProxy<T, U> extends InvocationEventProxy<U> {

    private final T delegate;

    InstrumentationProxy(InstrumentationFilter instrumentationFilter, InvocationEventHandler<U> handler, T delegate) {
        super(handler, instrumentationFilter);
        this.delegate = delegate;
    }

    @Override
    T getDelegate() {
        return delegate;
    }

}
