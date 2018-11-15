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

import java.util.concurrent.atomic.AtomicLong;

public class Statistics {

    private final AtomicLong ticked = new AtomicLong();
    private final AtomicLong sent = new AtomicLong();

    public void sent() {
        sent.incrementAndGet();
    }

    public long collectSent() {
        return this.sent.getAndSet(0);
    }

    public void ticked() {
        this.ticked.incrementAndGet();
    }
}
