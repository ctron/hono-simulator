/*******************************************************************************
 * Copyright (c) 2017, 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.demo.common;

import java.security.SecureRandom;
import java.util.Random;

public final class Select {

    private static final Random RANDOM = new SecureRandom();

    private Select() {
    }

    public static String oneOf(final String list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        return oneOf(list.split("\\s*,\\s*"));
    }

    public static String oneOf(final String... strings) {
        if (strings == null || strings.length <= 0) {
            return null;
        }
        return strings[RANDOM.nextInt(strings.length)];
    }
}
