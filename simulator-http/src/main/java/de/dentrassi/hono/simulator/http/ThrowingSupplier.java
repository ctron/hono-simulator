package de.dentrassi.hono.simulator.http;

@FunctionalInterface
public interface ThrowingSupplier<T, X extends Throwable> {

    T get() throws X;
}
