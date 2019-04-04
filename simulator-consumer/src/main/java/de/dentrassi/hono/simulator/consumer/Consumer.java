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

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;
import io.micrometer.core.instrument.Counter;

public class Consumer {

    private final Counter messages;
    private final Counter payload;

    public Consumer(final Counter messages, final Counter payload) {
        this.messages = messages;
        this.payload = payload;
    }

    public void handleMessage(final Message msg) {
        this.messages.increment();

        final Section body = msg.getBody();
        if (body instanceof Data) {
            final Binary value = ((Data) body).getValue();
            if (value != null) {
                this.payload.increment(value.getLength());
            }
        }
    }

    public double count() {
        return messages.count();
    }
}
