package com.ensox.ui.state;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Observable value. Widgets bind to signals instead of being rebuilt or
 * poked manually when data changes:
 *
 * <pre>
 * Signal&lt;Boolean&gt; enabled = Signal.of(config.enabled);
 * Switch.of(enabled);                                   // two-way
 * label.bind(enabled, on -&gt; label.text(on ? ON : OFF)); // one-way
 * </pre>
 *
 * <p>Listeners bound through {@code Widget.bind(...)} are removed when the
 * widget leaves the tree, so a closed screen never keeps receiving updates.</p>
 */
public class Signal<T> {
    private final CopyOnWriteArrayList<Consumer<? super T>> listeners = new CopyOnWriteArrayList<>();
    private T value;

    protected Signal(T value) {
        this.value = value;
    }

    public static <T> Signal<T> of(T value) {
        return new Signal<>(value);
    }

    public T get() {
        return value;
    }

    /** Sets the value and notifies listeners if it changed ({@link Objects#equals}). */
    public void set(T value) {
        if (Objects.equals(this.value, value)) {
            return;
        }
        this.value = value;
        for (Consumer<? super T> listener : listeners) {
            listener.accept(value);
        }
    }

    public void update(Function<T, T> updater) {
        set(updater.apply(value));
    }

    /** Listens to future changes only. */
    public Subscription subscribe(Consumer<? super T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** Calls {@code listener} with the current value now, then on every change. */
    public Subscription observe(Consumer<? super T> listener) {
        listener.accept(value);
        return subscribe(listener);
    }

    /**
     * Derived read-only signal. It stays subscribed to this signal for as long
     * as this signal lives; derive from long-lived state, not per frame.
     */
    public <R> Signal<R> map(Function<? super T, ? extends R> mapper) {
        Signal<R> derived = new Signal<>(mapper.apply(value));
        subscribe(next -> derived.set(mapper.apply(next)));
        return derived;
    }
}
