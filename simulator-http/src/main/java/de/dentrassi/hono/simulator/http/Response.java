/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.simulator.http;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public interface Response {

    int code();

    String bodyAsString(Charset charset);

    default String bodyAsString() {
        return bodyAsString(StandardCharsets.UTF_8);
    }
}
