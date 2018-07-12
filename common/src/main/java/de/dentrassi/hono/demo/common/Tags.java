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

import static java.util.Collections.singletonMap;

import java.util.Map;

public final class Tags {
    public static final Map<String, String> TELEMETRY = singletonMap("type", "telemetry");
    public static final Map<String, String> EVENT = singletonMap("type", "event");
}
