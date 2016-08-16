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

package com.palantir.tritium.proxy;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Proxies {

    private Proxies() {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a new proxy instance for the specified delegate bound to the given invocation handler.
     *
     * @param iface main interface to proxy
     * @param delegate delegate class whose interfaces ot proxy
     * @param handler proxy invocation handler
     * @return a new proxy instance that implements the specified interface as well as all the
     *         interfaces from the delegate class
     */
    public static <T, U extends T> T newProxy(Class<T> iface, U delegate, InvocationHandler handler) {
        checkNotNull(iface);
        checkNotNull(delegate);
        checkNotNull(handler);
        checkIsInterface(iface);

        return iface.cast(Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(),
                Proxies.interfaces(iface, delegate.getClass()),
                handler));
    }

    /**
     * Determine the superset of interfaces for the specified arguments.
     *
     * @param iface primary interface class
     * @param additionalInterfaces additional interfaces
     * @param delegateClass delegate class
     * @return the set of interfaces for the specified classes
     * @throws IllegalArgumentException if the specified interface class is not an interface
     */
    static Class<?>[] interfaces(Class<?> iface,
                                 Collection<Class<?>> additionalInterfaces,
                                 Class<?> delegateClass) {
        checkIsInterface(iface);
        checkNotNull(additionalInterfaces);
        checkNotNull(delegateClass);

        Set<Class<?>> interfaces = new LinkedHashSet<Class<?>>();
        interfaces.add(iface);
        interfaces.addAll(additionalInterfaces);
        if (delegateClass.isInterface()) {
            interfaces.add(delegateClass);
        }
        interfaces.addAll(Arrays.asList(delegateClass.getInterfaces()));

        checkAreAllInterfaces(interfaces);
        return interfaces.toArray(new Class<?>[0]);
    }

    /**
     * Determine the superset of interfaces for the specified arguments.
     *
     * @param iface primary interface class
     * @param delegateClass delegate class
     * @return the set of interfaces for the specified classes
     * @throws IllegalArgumentException if the specified interface class is not an interface
     */
    static Class<?>[] interfaces(Class<?> iface, Class<?> delegateClass) {
        return interfaces(iface, Collections.<Class<?>>emptySet(), delegateClass);
    }

    static void checkIsInterface(Class<?> iface) {
        if (!iface.isInterface()) {
            throw new IllegalArgumentException(iface + " is not an interface");
        }
    }

    static void checkAreAllInterfaces(Set<Class<?>> interfaces) {
        for (Class<?> possibleInterface : interfaces) {
            checkIsInterface(possibleInterface);
        }
    }

}
