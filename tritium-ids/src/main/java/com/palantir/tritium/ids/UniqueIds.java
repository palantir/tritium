/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

public final class UniqueIds {
    private static final SafeLogger log = SafeLoggerFactory.get(UniqueIds.class);
    private static final int CHUNK_SIZE = 16;

    private UniqueIds() {}

    private static final ThreadLocal<Random> SECURE_RANDOM = ThreadLocal.withInitial(UniqueIds::createSecureRandom);

    static SecureRandom createSecureRandom() {
        try {
            return SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            log.warn("Falling back to default SecureRandom", e);
            return new SecureRandom();
        }
    }

    /**
     * Returns a unique randomly generated {@link UUID}.
     */
    public static UUID randomUuidV4() {
        return randomUuidV4(SECURE_RANDOM.get());
    }

    @VisibleForTesting
    static UUID randomUuidV4(Random rand) {
        byte[] data = bytes(rand);
        Preconditions.checkArgument(data.length == CHUNK_SIZE, "Invalid data length, expected 16 bytes");
        data[6] = (byte) ((data[6] & 0x0f) | 0x40); // version 4
        data[8] = (byte) ((data[8] & 0x3f) | 0x80); // IETF variant

        long mostSigBits = 0;
        for (int i = 0; i < 8; i++) {
            mostSigBits = (mostSigBits << 8) | (data[i] & 0xff);
        }

        long leastSigBits = 0;
        for (int i = 8; i < 16; i++) {
            leastSigBits = (leastSigBits << 8) | (data[i] & 0xff);
        }

        return new UUID(mostSigBits, leastSigBits);
    }

    private static byte[] bytes(Random rand) {
        byte[] data = new byte[16];
        rand.nextBytes(data);
        return data;
    }
}
