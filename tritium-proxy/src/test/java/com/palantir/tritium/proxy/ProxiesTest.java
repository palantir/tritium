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

import com.google.common.collect.ImmutableSet;
import com.palantir.tritium.test.MoreSpecificReturn;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

final class ProxiesTest {

    @Test
    void testNewProxy() {
        TestInterface implementation = new TestImplementation();
        TestInterface proxy = Proxies.newProxy(
                TestInterface.class,
                implementation,
                (_delegate, method, args) -> method.invoke(implementation, args) + ", world");
        assertThat(proxy.test()).isEqualTo("hello, world");
    }

    @Test
    void testInterfacesClassOfQClassOfQ() {
        Class<?>[] interfaces = Proxies.interfaces(TestInterface.class, TestImplementation.class);
        assertThat(interfaces).containsExactly(TestInterface.class, Runnable.class, MoreSpecificReturn.class);
    }

    @Test
    void testCheckIsInterface() {
        Proxies.checkIsInterface(Runnable.class);
    }

    @Test
    void testCheckIsInterfaceOnClass() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Proxies.checkIsInterface(String.class));
    }

    @Test
    void testCheckAreAllInterfaces() {
        Proxies.checkAreAllInterfaces(ImmutableSet.of(TestInterface.class, Runnable.class, Callable.class, List.class));
    }

    @Test
    void testCheckAreAllInterfacesWithClass() {
        ImmutableSet<Class<?>> interfaces =
                ImmutableSet.of(TestInterface.class, String.class, Runnable.class, Callable.class, List.class);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Proxies.checkAreAllInterfaces(interfaces));
    }
}
