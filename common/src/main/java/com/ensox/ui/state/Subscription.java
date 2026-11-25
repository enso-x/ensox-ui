package com.ensox.ui.state;

/** Handle returned by every subscription; closing it removes the listener. Idempotent. */
@FunctionalInterface
public interface Subscription extends AutoCloseable {
    Subscription NONE = () -> {
    };

    @Override
    void close();
}
