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

package de.dentrassi.hono.simulator.http;

import de.dentrassi.hono.demo.common.Register;
import okhttp3.OkHttpClient;

public interface DeviceProvider {

    String getName();

    Device createDevice(String user, String deviceId, String tenant, String password,
            OkHttpClient client, Register register, Statistics telemetryStatistics, Statistics eventStatistics);
}
