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

import java.time.Instant;
import java.util.Map;

public interface EventWriter {

    void writeEvent(Instant timestamp, String table, String title, String description,
            Map<String, String> tags);

    default void writeEvent(final Instant timestamp, final String title, final String description) {
        writeEvent(timestamp, "events", title, description, null);
    }

    default void writeEvent(final String title, final String description) {
        writeEvent(Instant.now(), title, description);
    }
}
