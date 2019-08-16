/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/

package de.dentrassi.hono.demo.common;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;

public abstract class AbstractRegistration implements Registration {

    protected static final MediaType MT_JSON = MediaType.parse("application/json");

    protected final OkHttpClient http;
    protected final String tenantId;

    public AbstractRegistration(final String tenantId) {
        this.tenantId = tenantId;

        final OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
        if (Tls.insecure()) {
            Tls.makeOkHttpInsecure(httpBuilder);
        }
        this.http = httpBuilder.build();

    }

}
