/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.test;

import java.util.Collection;
import java.util.Set;

public interface TestInterface {

    String test();

    void multiArgumentMethod(String string, int count, Collection<String> foo);

    void bulk(Set<?> set);

    int throwsCheckedException() throws Exception;

    int throwsThrowable() throws Throwable;

}
