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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings("PreferSafeLoggingPreconditions") // this module depends only on JDK
public final class Proxies {

    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];

    private Proxies() {}

    /**
     * Creates a new proxy instance for the specified delegate bound to the given invocation handler.
     *
     * @param iface main interface to proxy
     * @param delegate delegate class whose interfaces to proxy
     * @param handler proxy invocation handler
     * @return a new proxy instance that implements the specified interface as well as all the interfaces from the
     *     delegate class
     */
    public static <T, U extends T> T newProxy(Class<T> iface, U delegate, InvocationHandler handler) {
        Objects.requireNonNull(iface, "interface");
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(handler, "handler");
        checkIsInterface(iface);

        return iface.cast(Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(), Proxies.interfaces(iface, delegate.getClass()), handler));
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
        checkIsInterface(iface);
        Objects.requireNonNull(delegateClass, "delegateClass");

        if (delegateClass.isInterface()) {
            if (iface.equals(delegateClass)) {
                return new Class<?>[] {iface};
            }
            return new Class<?>[] {iface, delegateClass};
        }

        Class<?>[] delegateInterfaces = delegateClass.getInterfaces();
        int expectedSize = delegateInterfaces.length + 1;
        Set<Class<?>> interfaces = new LinkedHashSet<>((int) ((float) expectedSize / 0.75F + 1.0F));
        interfaces.add(iface);
        for (Class<?> possibleInterface : delegateInterfaces) {
            interfaces.add(checkIsInterface(possibleInterface));
        }
        return interfaces.toArray(EMPTY_CLASS_ARRAY);
    }

    static Class<?> checkIsInterface(Class<?> iface) {
        if (!iface.isInterface()) {
            throw new IllegalArgumentException(iface + " is not an interface");
        }
        return iface;
    }
}
