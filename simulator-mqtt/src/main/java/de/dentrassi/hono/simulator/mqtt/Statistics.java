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

package de.dentrassi.hono.simulator.mqtt;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

public class Statistics {

    private final Counter ticked;
    private final Counter sent;

    public Statistics(final MeterRegistry metrics, final Tags commonTags) {
        this.sent = metrics.counter("messages_sent", commonTags);
        this.ticked = metrics.counter("messages_scheduled", commonTags);
    }

    public void sent() {
        this.sent.increment();
    }

    public void ticked() {
        this.ticked.increment();
    }
}
