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
public class TestImplementation implements TestInterface, Runnable, MoreSpecificReturn {

    private final AtomicInteger invocationCount = new AtomicInteger();
    private final TestException checkedException = new TestException("Testing checked Exception handling");
    private final TestThrowable testThrowable = new TestThrowable();
    private final OutOfMemoryError outOfMemoryError = new OutOfMemoryError("Testing OOM");

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
    public void multiArgumentMethod(String _string, int _count, Collection<String> _collection) {
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
        throw checkedException;
    }

    @Override
    public int throwsThrowable() throws Throwable {
        throw testThrowable;
    }

    @Override
    public void throwsOutOfMemoryError() {
        throw outOfMemoryError;
    }

    public int invocationCount() {
        return invocationCount.get();
    }

    @Override
    public String specificity() {
        return "specificity";
    }

    public static class TestException extends Exception {
        public TestException(String message) {
            super(message);
        }
    }

    @Override
    public String toString() {
        return TestImplementation.class.getName();
    }

    public static final class TestThrowable extends Throwable {
        TestThrowable() {
            super(TestThrowable.class.getSimpleName());
        }
    }
}
