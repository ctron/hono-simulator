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
package de.dentrassi.hono.simulator.runner.scenario;

import java.time.Duration;

import de.dentrassi.hono.simulator.runner.Metrics;

/**
 * Variation of Scenario1 for maxInstances=1.
 * <p>
 * The default deployment of Hono uses one worker thread per service. The baseline architecture for scale-test-2 uses 9
 * threads per service in order to improve performance. This test deploys the same number of services, but each in a
 * single JVM in order to compare the difference.
 * </p>
 * <p>
 * For this test to run in a reasonable amount of time, the durations have been shorted.
 * </p>
 */
public class Scenario1LowMax extends AbstractSimpleScaleUpScenario {

    private static final int MAX_ADAPTER_INSTANCES = 16 * 9;

    private static final int MAX_SIMULATOR_INSTANCES = 64;

    private static final double MAX_FAILURE_RATE = 0.02;

    public Scenario1LowMax(final Metrics metrics) {
        super(metrics, "scenario1-low-max");
    }

    @Override
    protected int getMaximumAdapterInstances() {
        return MAX_ADAPTER_INSTANCES;
    }

    @Override
    protected double getMaximumFailureRatio() {
        return MAX_FAILURE_RATE;
    }

    @Override
    protected int getMaximumSimulatorInstances() {
        return MAX_SIMULATOR_INSTANCES;
    }

    @Override
    protected Duration getImproveDuration() {
        return Duration.ofMinutes(1);
    }

    @Override
    protected Duration getWaitForStableDuration() {
        return Duration.ofMinutes(5);
    }

    @Override
    protected Duration getSampleDuration() {
        return Duration.ofMinutes(2);
    }

}
