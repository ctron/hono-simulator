package de.dentrassi.hono.simulator.http;

@FunctionalInterface
public interface ThrowingFunction<T, R, X extends Throwable> {

    R apply(T t) throws X;
}
