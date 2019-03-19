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

import io.glutamate.lang.Environment;
import io.micrometer.core.instrument.Tag;

public enum Type {
    TELEMETRY,
    EVENT;

    private Tag tag;

    private Type() {
        this.tag = Tag.of("type", this.name().toLowerCase());
    }

    public Tag asTag() {
        return tag;
    }

    public static Type from(final String name) {
        return Type.valueOf(name.toUpperCase());
    }

    public static Type fromEnv() {
        return Environment.getAs("MESSAGE_TYPE", TELEMETRY, Type::from);
    }
}
