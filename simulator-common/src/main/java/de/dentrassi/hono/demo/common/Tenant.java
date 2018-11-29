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

import io.glutamate.lang.Environment;

public final class Tenant {

    public static final String TENANT;

    static {
        TENANT = Environment.get("HONO_TENANT").orElse("DEFAULT_TENANT");
    }
}
