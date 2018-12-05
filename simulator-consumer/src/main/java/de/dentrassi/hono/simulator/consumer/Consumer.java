package de.dentrassi.hono.simulator.consumer;

import org.apache.qpid.proton.message.Message;
import io.micrometer.core.instrument.Counter;

public class Consumer {

    private final Counter counter;

    public Consumer(final Counter counter) {
        this.counter = counter;
    }

    public void handleMessage(final Message msg) {
        this.counter.increment();
    }

    public double count() {
        return counter.count();
    }
}
