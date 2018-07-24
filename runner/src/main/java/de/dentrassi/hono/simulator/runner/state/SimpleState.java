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

public class SimpleState extends AbstractNextState {

    private final Runnable runnable;

    public SimpleState(final Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public void check(final Context context) {
        this.runnable.run();
        advance(context);
    }

}
