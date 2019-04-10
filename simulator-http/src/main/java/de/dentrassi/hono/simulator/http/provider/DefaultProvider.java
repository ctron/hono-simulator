/*******************************************************************************
 * Copyright (c) 2017, 2019 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.simulator.http.provider;

import java.util.concurrent.Executor;

import de.dentrassi.hono.demo.common.EventWriter;
import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.simulator.http.Device;
import de.dentrassi.hono.simulator.http.DeviceProvider;
import de.dentrassi.hono.simulator.http.Statistics;

public class DefaultProvider implements DeviceProvider {

    @FunctionalInterface
    public interface Constructor {

        Device construct(Executor executor, String user, String deviceId, String tenant, String password,
                Register register, Payload payload, Statistics statistics,
                EventWriter eventWriter);
    }

    private final String name;
    private final DefaultProvider.Constructor constructor;

    public DefaultProvider(final String name, final Constructor constructor) {
        this.name = name;
        this.constructor = constructor;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Device createDevice(final Executor executor, final String user, final String deviceId, final String tenant,
            final String password, final Register register, final Payload payload,
            final Statistics statistics, final EventWriter eventWriter) {
        return this.constructor.construct(executor, user, deviceId, tenant, password, register, payload,
                statistics, eventWriter);
    }

}