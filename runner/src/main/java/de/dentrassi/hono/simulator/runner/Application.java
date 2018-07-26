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
package de.dentrassi.hono.simulator.runner;

import java.time.Duration;

import de.dentrassi.hono.simulator.runner.scenario.Scenario1LowMax;

public class Application implements AutoCloseable {

    public static void main(final String[] args) {

        try (Application app = new Application("telemetry")) {
            app.run();
        }

    }

    private final Metrics metrics;

    public Application(final String type) {
        this.metrics = new Metrics("telemetry", Duration.ofMinutes(1));
    }

    public void run() {
        new Scenario1LowMax(this.metrics).run();
    }

    @Override
    public void close() {
        this.metrics.close();
    }

}
