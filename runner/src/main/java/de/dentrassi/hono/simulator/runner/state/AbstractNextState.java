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

public abstract class AbstractNextState implements State {

    private State nextState;

    public State next(final State state) {
        this.nextState = state;
        return this;
    }

    public <T extends State> T then(final T state) {
        next(state);
        return state;
    }

    protected void advance(final Context context) {
        context.advance(this.nextState);
    }

}
