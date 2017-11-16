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

package com.palantir.tritium.test;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("DesignForExtension")
public class TestImplementation implements TestInterface, Runnable {

    private final AtomicInteger invocationCount = new AtomicInteger();

    @Override
    public void run() {
        test();
    }

    @Override
    public String test() {
        invocationCount.incrementAndGet();
        return "hello";
    }

    @Override
    public void multiArgumentMethod(String string, int count, Collection<String> foo) {
        test();
    }

    @SuppressWarnings("unused")
    @Override
    public void bulk(Set<?> set) {
        for (Object object : set) {
            test();
        }
    }

    @Override
    public int throwsCheckedException() throws Exception {
        throw new TestException("Testing checked Exception handling");
    }

    @Override
    public int throwsThrowable() throws Throwable {
        throw new AssertionError("Testing Error handling");
    }

    @Override
    public void throwsOutOfMemoryError() {
        throw new OutOfMemoryError("Testing OOM");
    }

    public int invocationCount() {
        return invocationCount.get();
    }

    public static class TestException extends Exception {
        public TestException(String message) {
            super(message);
        }
    }
}
