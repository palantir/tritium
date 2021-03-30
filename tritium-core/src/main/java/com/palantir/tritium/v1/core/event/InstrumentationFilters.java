/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.v1.core.event;

import com.palantir.tritium.v1.api.event.InstrumentationFilter;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import javax.annotation.Nonnull;

/**
 * Implementations of {@link InstrumentationFilter}.
 */
public final class InstrumentationFilters {

    private InstrumentationFilters() {}

    private enum Filters implements InstrumentationFilter {
        ALL {
            @Override
            public boolean shouldInstrument(@Nonnull Object _ins, @Nonnull Method _method, @Nonnull Object[] _args) {
                return true;
            }
        },

        NONE {
            @Override
            public boolean shouldInstrument(@Nonnull Object _ins, @Nonnull Method _method, @Nonnull Object[] _args) {
                return false;
            }
        },
    }

    /** Instrument all invocations. */
    public static InstrumentationFilter instrumentAll() {
        return Filters.ALL;
    }

    /** Instrument no invocations. */
    public static InstrumentationFilter instrumentNone() {
        return Filters.NONE;
    }

    public static InstrumentationFilter from(BooleanSupplier isEnabledSupplier) {
        return (_instance, _method, _args) -> isEnabledSupplier.getAsBoolean();
    }
}
