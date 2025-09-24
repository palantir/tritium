/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.ids;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.jspecify.annotations.Nullable;

public final class Digests {
    private Digests() {}

    static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;

    /**
     * Alternative implementation with explicit constant-time length handling.
     * This version processes up to maxLength bytes regardless of actual length.
     * Use when array lengths themselves must be kept secret.
     *
     * @param first First byte array
     * @param second Second byte array
     * @return true if arrays are equal up to their actual lengths, false otherwise
     */
    @SuppressWarnings("ArrayEquality") // explicitly providing constant time
    public static boolean constantTimeEquals(byte @Nullable [] first, byte @Nullable [] second) {
        if (first == null || second == null) {
            return first == second;
        }

        // Ensure we don't go out of bounds
        int firstLen = first.length;
        int secondLen = second.length;

        if (firstLen == 0) {
            return secondLen == 0;
        }

        // Process up to minLength bytes
        int minLength = Math.min(firstLen, secondLen);
        int vectorLength = SPECIES.length();
        if (minLength < vectorLength) {
            return MessageDigest.isEqual(first, second);
        }

        int bound = minLength - (minLength % vectorLength);

        ByteVector accumulatorVector = ByteVector.zero(SPECIES);
        int index = 0;
        for (; index < bound; index += vectorLength) {
            // Safe array access with bounds checking
            ByteVector firstVector = safeLoadVector(first, index, Math.min(vectorLength, firstLen - index));
            ByteVector secondVector = safeLoadVector(second, index, Math.min(vectorLength, secondLen - index));
            ByteVector xor = firstVector.lanewise(VectorOperators.XOR, secondVector);
            accumulatorVector = accumulatorVector.lanewise(VectorOperators.OR, xor);
        }

        byte accumulator = accumulatorVector.reduceLanes(VectorOperators.OR);

        // Process remaining bytes
        for (; index < minLength; index++) {
            accumulator |= (byte) (first[index] ^ second[index]);
        }

        // Check both accumulator and length difference
        int lengthDiff = firstLen ^ secondLen;
        return (accumulator | lengthDiff) == 0;
    }

    /**
     * Safely loads a vector from an array with bounds checking.
     * Pads with zeros if the requested range extends beyond array bounds.
     */
    private static ByteVector safeLoadVector(byte[] arr, int offset, int length) {
        if (offset >= arr.length || length <= 0) {
            return ByteVector.zero(SPECIES);
        }

        int safeLength = Math.min(length, Math.min(SPECIES.length(), arr.length - offset));

        if (safeLength == SPECIES.length()) {
            return ByteVector.fromArray(SPECIES, arr, offset);
        } else {
            // Partial load with masking
            byte[] temp = new byte[SPECIES.length()];
            System.arraycopy(arr, offset, temp, 0, safeLength);
            return ByteVector.fromArray(SPECIES, temp, 0);
        }
    }

    // Get Unsafe instance through reflection
    private static final sun.misc.Unsafe UNSAFE;
    private static final long BYTE_ARRAY_BASE_OFFSET;

    static {
        try {
            Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (sun.misc.Unsafe) theUnsafe.get(null);
            BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Unsafe instance", e);
        }
    }

    /**
     * Alternative implementation with unrolled loop for better performance.
     * Processes 32 bytes per iteration (4 getLong calls).
     *
     * @param first First byte array
     * @param second Second byte array
     * @return true if arrays are equal, false otherwise
     */
    public static boolean constantTimeEqualsUnrolled(byte[] first, byte[] second) {
        if (first == null || second == null) {
            return first == second;
        }

        int firstLen = first.length;
        int secondLen = second.length;
        if (firstLen == 0) {
            return secondLen == 0;
        }

        final int length = Math.min(firstLen, secondLen);
        if (length < 32) {
            return MessageDigest.isEqual(first, second);
        }

        long accumulator = firstLen ^ secondLen;
        int offset = 0;

        // Process 32 bytes at a time (4 longs)
        final int unrolledCount = length >>> 5; // length / 32

        for (int i = 0; i < unrolledCount; i++) {
            long a0 = UNSAFE.getLong(first, BYTE_ARRAY_BASE_OFFSET + offset);
            long b0 = UNSAFE.getLong(second, BYTE_ARRAY_BASE_OFFSET + offset);

            long a1 = UNSAFE.getLong(first, BYTE_ARRAY_BASE_OFFSET + offset + 8);
            long b1 = UNSAFE.getLong(second, BYTE_ARRAY_BASE_OFFSET + offset + 8);

            long a2 = UNSAFE.getLong(first, BYTE_ARRAY_BASE_OFFSET + offset + 16);
            long b2 = UNSAFE.getLong(second, BYTE_ARRAY_BASE_OFFSET + offset + 16);

            long a3 = UNSAFE.getLong(first, BYTE_ARRAY_BASE_OFFSET + offset + 24);
            long b3 = UNSAFE.getLong(second, BYTE_ARRAY_BASE_OFFSET + offset + 24);

            accumulator |= (a0 ^ b0) | (a1 ^ b1) | (a2 ^ b2) | (a3 ^ b3);
            offset += 32;
        }

        // Process remaining 8-byte chunks
        final int remainingLongs = (length - offset) >>> 3;
        for (int i = 0; i < remainingLongs; i++) {
            long aLong = UNSAFE.getLong(first, BYTE_ARRAY_BASE_OFFSET + offset);
            long bLong = UNSAFE.getLong(second, BYTE_ARRAY_BASE_OFFSET + offset);
            accumulator |= (aLong ^ bLong);
            offset += 8;
        }

        // Process remaining bytes
        for (int i = offset; i < length; i++) {
            accumulator |= (first[i] ^ second[i]);
        }

        return accumulator == 0L;
    }
}
