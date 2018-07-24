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

import java.lang.ProcessBuilder.Redirect;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class ExecuteProcess implements State {

    private final long timeout;

    public ExecuteProcess(final long waitMillis) {
        this.timeout = waitMillis;
    }

    @Override
    public void check(final Context context) {

        final ProcessBuilder process = new ProcessBuilder(getCommand());

        process.redirectOutput(Redirect.INHERIT);
        process.redirectError(Redirect.INHERIT);

        try {
            process.start().waitFor(this.timeout, TimeUnit.MILLISECONDS);
        } catch (final Exception e) {
        }
    }

    protected abstract List<String> getCommand();

}
