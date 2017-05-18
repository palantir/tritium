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

package com.palantir.tritium.event;

import com.palantir.tritium.api.functions.BooleanSupplier;
import java.lang.reflect.Method;
import javax.annotation.Nonnull;

public enum InstrumentationFilters implements InstrumentationFilter {

    INSTRUMENT_ALL {
        @Override
        public boolean shouldInstrument(@Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
            return true;
        }
    },

    INSTRUMENT_NONE {
        @Override
        public boolean shouldInstrument(@Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
            return false;
        }
    };

    public static InstrumentationFilter from(final BooleanSupplier isEnabledSupplier) {
        return (instance, method, args) -> isEnabledSupplier.asBoolean();
    }
}
