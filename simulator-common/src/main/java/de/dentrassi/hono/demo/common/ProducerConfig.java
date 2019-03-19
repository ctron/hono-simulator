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

import java.time.Duration;
import java.util.Objects;

import io.glutamate.lang.Environment;

public class ProducerConfig {

    private final Type type;
    private final Duration period;

    public ProducerConfig(final Type type, final Duration period) {
        this.type = Objects.requireNonNull(type);
        this.period = Objects.requireNonNull(period);
    }

    public Type getType() {
        return type;
    }

    public Duration getPeriod() {
        return period;
    }

    public static ProducerConfig fromEnv() {
            return new ProducerConfig(
                    Type.fromEnv(),
                Environment.getAs("PERIOD_MS", Long::parseLong)
                    .map(Duration::ofMillis)
                    .orElse(Duration.ofSeconds(1))
        );
    }
}
