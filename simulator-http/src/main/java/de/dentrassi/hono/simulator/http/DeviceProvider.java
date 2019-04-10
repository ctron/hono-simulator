/*******************************************************************************
 * Copyright (c) 2018, 2019 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/

package de.dentrassi.hono.simulator.http;

import java.util.concurrent.Executor;

import de.dentrassi.hono.demo.common.EventWriter;
import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.Register;

public interface DeviceProvider {

    String getName();

    Device createDevice(Executor executor, String user, String deviceId, String tenant, String password,
            Register register, Payload payload, Statistics statistics, EventWriter eventWriter);
}
