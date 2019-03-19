/*******************************************************************************
 * Copyright (c) 2018, 2019 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/

package de.dentrassi.hono.demo.common;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import io.glutamate.lang.Environment;
import io.vertx.core.buffer.Buffer;

public final class Payload {

    private static final Payload INSTANCE;

    static {
        final int payloadSize = Environment.getAs("PAYLOAD_SIZE", 64, Integer::parseInt);

        if (payloadSize < 0) {
            INSTANCE = new Payload("application/octet-stream", new byte[0]);
        } else {
            final byte[] buffer = new byte[payloadSize];
            Arrays.fill(buffer, (byte) 0x42);
            INSTANCE = new Payload("application/octet-stream", buffer);
        }
    }

    private final String contentType;
    private final byte[] payload;
    private final Buffer buffer;

    private Payload(final String contentType, final byte[] payload) {
        this.contentType = contentType;
        this.payload = payload;
        this.buffer = Buffer.buffer(payload);
    }

    public static Payload payload() {
        return INSTANCE;
    }

    public void write(final OutputStream out) throws IOException {
        out.write(this.payload);
    }

    public String getContentType() {
        return this.contentType;
    }

    public byte[] getBytes() {
        return this.payload;
    }

    public Buffer getBuffer() {
        return this.buffer;
    }

}
