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

import static java.lang.System.getenv;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static okhttp3.HttpUrl.parse;

import java.util.Optional;

import io.glutamate.lang.Environment;

public interface Registration {

    public static enum Version {
        LEGACY,
        V1,
    }

    void device(String deviceId, String username, String password) throws Exception;

    public static Optional<Registration> fromEnv() {
        return fromEnv(Tenant.TENANT);
    }

    public static Optional<Registration> fromEnv(final String tenantId) {

        String devRegUrl = getenv("DEVICE_REGISTRY_URL");
        if (devRegUrl == null) {
            final String regHost = getenv("HONO_SERVICE_DEVICE_REGISTRY_SERVICE_HOST");
            final String regPort = getenv("HONO_SERVICE_DEVICE_REGISTRY_SERVICE_PORT_HTTP");
            if (regHost != null) {
                devRegUrl = "http://" + regHost;
                if (regPort != null && !regPort.isEmpty()) {
                    devRegUrl += ":" + regPort;
                }
            }
        }

        if (devRegUrl == null || devRegUrl.isEmpty()) {
            System.out.println("Automatic registration is disabled");
            return empty();
        }

        final Version version = Environment.get("DEVICE_REGISTRY_VERSION")
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .map(Version::valueOf)
                .orElse(Version.V1);

        System.out.format("Device Registry - Version: %s, URL: %s%n", version, devRegUrl);

        switch (version) {
        case V1:
            return of(new RegistrationV1(tenantId, parse(devRegUrl)));
        default:
            return of(new RegistrationLegacy(tenantId, parse(devRegUrl)));
        }

    }
}
