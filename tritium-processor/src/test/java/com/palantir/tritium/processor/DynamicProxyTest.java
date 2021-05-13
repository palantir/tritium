/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

/** Verification that our behavior matches dynamic proxies. */
final class DynamicProxyTest {

    @Test
    void testVarargsDynamicProxy() {
        Varargs proxy = (Varargs) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[] {Varargs.class}, (_proxy, _method, args) -> args);
        Object[] resultEmpty = (Object[]) proxy.foo(1);
        assertThat(resultEmpty).containsExactly(1, new double[0]);
        Object[] resultVararg = (Object[]) proxy.foo(1, 2.1D);
        assertThat(resultVararg).containsExactly(1, new double[] {2.1D});
    }

    @Test
    void testVarargsMethodLookup() throws Exception {
        assertThat(Varargs.class.getMethod("foo", int.class, double[].class)).isNotNull();
    }

    public interface Varargs {

        Object foo(int value, double... var);
    }
}
