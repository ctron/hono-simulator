/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/

package de.dentrassi.hono.demo.common;

import java.util.Optional;
import java.util.function.Function;

public final class Environment {

    private Environment() {
    }

    public static Optional<String> get(final String name) {
        return Optional.ofNullable(System.getenv(name));
    }

    public static <T> Optional<T> getAs(final String name, final Function<String, T> converter) {
        return get(name).map(converter);
    }

    public static <T> T getAs(final String name, final T defaultValue, final Function<String, T> converter) {
        return get(name).map(converter).orElse(defaultValue);
    }

    public static void is(final String name, final Runnable runnable) {
        if (getAs(name, false, Boolean::parseBoolean)) {
            runnable.run();
        }
    }

}
