package com.ensox.ui.state;

import com.ensox.ui.core.Widget;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A signal that views a part of another signal: reading maps the parent value,
 * writing rebuilds the parent from the new part.
 *
 * <pre>
 * Lens&lt;ColorSource, Float&gt; opacity = Lens.of(source, ColorSource::opacity, ColorSource::withOpacity).attach(widget);
 * </pre>
 *
 * <p>Updates from the parent arrive only while the owner widget is attached
 * ({@link #attach}), so lenses created for short-lived widgets never pile up as
 * listeners on long-lived state.</p>
 */
public final class Lens<S, T> extends Signal<T> {
    private final Signal<S> parent;
    private final Function<S, T> get;
    private final BiFunction<S, T, S> put;

    private Lens(Signal<S> parent, Function<S, T> get, BiFunction<S, T, S> put) {
        super(get.apply(parent.get()));
        this.parent = parent;
        this.get = get;
        this.put = put;
    }

    public static <S, T> Lens<S, T> of(Signal<S> parent, Function<S, T> get, BiFunction<S, T, S> put) {
        return new Lens<>(parent, get, put);
    }

    /** Follows the parent for as long as {@code owner} is attached; returns this lens. */
    public Lens<S, T> attach(Widget owner) {
        owner.bind(parent, ignored -> pull());
        return this;
    }

    @Override
    public void set(T value) {
        if (Objects.equals(get(), value)) {
            return;
        }
        super.set(value);
        parent.set(put.apply(parent.get(), value));
    }

    private void pull() {
        super.set(get.apply(parent.get()));
    }
}
