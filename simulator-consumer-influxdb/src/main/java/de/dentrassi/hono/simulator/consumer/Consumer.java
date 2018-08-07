package de.dentrassi.hono.simulator.consumer;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Consumer {

    private static final Logger logger = LoggerFactory.getLogger(Consumer.class);

    private final AtomicLong counter = new AtomicLong();
    private final InfluxDbConsumer consumer;

    public Consumer(final InfluxDbConsumer consumer) {
        this.consumer = consumer;
    }

    public AtomicLong getCounter() {
        return this.counter;
    }

    public void handleMessage(final Message msg) {
        this.counter.incrementAndGet();

        if (this.consumer != null) {
            final String body = bodyAsString(msg);
            if (body != null) {
                this.consumer.consume(msg, body);
            }
        }
    }

    private static String bodyAsString(final Message msg) {

        final Section body = msg.getBody();

        if (body instanceof AmqpValue) {

            final Object value = ((AmqpValue) body).getValue();

            if (value == null) {
                logger.info("Missing body value");
                return null;
            }

            if (value instanceof String) {
                return (String) value;
            } else if (value instanceof byte[]) {
                return new String((byte[]) value, StandardCharsets.UTF_8);
            } else {
                logger.info("Unsupported body type: {}", value.getClass());
                return null;
            }
        } else if (body instanceof Data) {
            return StandardCharsets.UTF_8.decode(((Data) body).getValue().asByteBuffer()).toString();
        } else {
            logger.info("Unsupported body type: {}", body.getClass());
            return null;
        }
    }

}
