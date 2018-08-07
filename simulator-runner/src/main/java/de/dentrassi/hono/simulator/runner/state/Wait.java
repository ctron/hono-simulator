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
package de.dentrassi.hono.simulator.runner.state;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Wait extends AbstractNextState {

    private static final Logger logger = LoggerFactory.getLogger(Wait.class);

    private final Duration duration;

    private Instant until;

    public Wait(final Duration duration) {
        this.duration = duration;
    }

    @Override
    public void start() {
        this.until = Instant.now().plus(this.duration);
        logger.info("Waiting for {} until {}", this.duration, this.until);
    }

    @Override
    public void check(final Context context) {
        if (Instant.now().isAfter(this.until)) {
            logger.info("Wait of {} complete ... advancing", this.duration);
            advance(context);
        }
    }

}
