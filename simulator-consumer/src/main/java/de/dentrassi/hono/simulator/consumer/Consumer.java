/*******************************************************************************
 * Copyright (c) 2017, 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/

package de.dentrassi.hono.simulator.consumer;

import org.apache.qpid.proton.message.Message;
import io.micrometer.core.instrument.Counter;

public class Consumer {

    private final Counter counter;

    public Consumer(final Counter counter) {
        this.counter = counter;
    }

    public void handleMessage(final Message msg) {
        this.counter.increment();
    }

    public double count() {
        return counter.count();
    }
}
