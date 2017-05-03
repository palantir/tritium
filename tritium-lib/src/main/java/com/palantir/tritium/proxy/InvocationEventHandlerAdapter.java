/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

import com.palantir.tritium.event.InstrumentationFilter;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.reflect.Method;
import java.util.List;
import net.sf.cglib.proxy.InvocationHandler;

final class InvocationEventHandlerAdapter implements InvocationHandler {
    private final InvocationEventProxy<InvocationContext> proxy;
    private final InstrumentationFilter instrumentationFilter;

    private InvocationEventHandlerAdapter(InvocationEventProxy<InvocationContext> proxy,
            InstrumentationFilter instrumentationFilter) {
        this.proxy = proxy;
        this.instrumentationFilter = instrumentationFilter;
    }

    static <T> InvocationEventHandlerAdapter create(final T delegate,
            InstrumentationFilter instrumentationFilter,
            List<InvocationEventHandler<InvocationContext>> handlers) {
        InvocationEventProxy<InvocationContext> proxy = new InvocationEventProxy<InvocationContext>(handlers) {
            @Override
            T getDelegate() {
                return delegate;
            }
        };
        return new InvocationEventHandlerAdapter(proxy, instrumentationFilter);
    }

    @Override
    public Object invoke(Object instance, Method method, Object[] args) throws Throwable {
        if (instrumentationFilter.shouldInstrument(instance, method, args)) {
            return proxy.invoke(instance, method, args);
        }
        return method.invoke(proxy.getDelegate(), args);
    }
}
