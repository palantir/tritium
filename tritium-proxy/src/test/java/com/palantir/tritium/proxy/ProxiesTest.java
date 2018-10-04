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

package com.palantir.tritium.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.Test;

public class ProxiesTest {

    @Test
    public void testNewProxy() {
        TestInterface implementation = new TestImplementation();
        TestInterface proxy = Proxies.newProxy(TestInterface.class, implementation,
                (delegate, method, args) -> method.invoke(implementation, args) + ", world");
        assertEquals("hello, world", proxy.test());
    }

    @Test
    public void testInterfacesAdditionalInterfaces() {
        Class<?>[] interfaces = Proxies.interfaces(TestInterface.class, Runnable.class, ImmutableList.of(List.class));
        assertThat(interfaces).containsExactly(TestInterface.class, List.class, Runnable.class);
    }

    @Test
    public void testInterfacesClassOfQClassOfQ() {
        Class<?>[] interfaces = Proxies.interfaces(TestInterface.class, TestImplementation.class);
        assertThat(interfaces).containsExactly(TestInterface.class, Runnable.class);
    }

    @Test
    public void testCheckIsInterface() {
        Proxies.checkIsInterface(Runnable.class);
    }

    @Test
    public void testCheckIsInterfaceOnClass() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                Proxies.checkIsInterface(String.class));
    }

    @Test
    public void testCheckAreAllInterfaces() {
        Proxies.checkAreAllInterfaces(ImmutableSet.of(TestInterface.class,
                Runnable.class,
                Callable.class,
                List.class));
    }

    @Test
    public void testCheckAreAllInterfacesWithClass() {
        ImmutableSet<Class<?>> interfaces = ImmutableSet.of(TestInterface.class,
                String.class,
                Runnable.class,
                Callable.class,
                List.class);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                Proxies.checkAreAllInterfaces(interfaces));
    }

    @Test
    public void testInaccessibleConstructor() throws NoSuchMethodException {
        Constructor<Proxies> constructor = Proxies.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseExactlyInstanceOf(UnsupportedOperationException.class);
    }

}
