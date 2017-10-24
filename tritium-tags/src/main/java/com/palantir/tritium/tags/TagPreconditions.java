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

package com.palantir.tritium.tags;

final class TagPreconditions {
    private TagPreconditions() {}

    static String checkValidTagComponent(String key, Iterable<String> delimiters) {
        checkNotBlank(key);
        for (String delimiter : delimiters) {
            checkNameDoesNotContain(key, delimiter);
        }
        return key;
    }

    static String checkNotBlank(String value) {
        if (isNullOrBlank(value)) {
            throw new IllegalArgumentException(String.format(
                    "'%s' must not be null or blank", value));
        }
        return value;
    }

    static void checkNameDoesNotContain(String name, String seq) {
        if (name.contains(seq)) {
            throw new IllegalArgumentException(String.format(
                    "'%s' must not contain '%s'", name, seq));
        }
    }

    static boolean isNullOrBlank(String string) {
        if (string == null || string.isEmpty()) {
            return true;
        }

        for (int i = 0; i < string.length(); i++) {
            if (!Character.isWhitespace(string.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    static boolean isNotNullOrBlank(String string) {
        return !isNullOrBlank(string);
    }

    static void checkNotNull(Object object, String message) {
        if (object == null) {
            throw new NullPointerException(message);
        }
    }
}
