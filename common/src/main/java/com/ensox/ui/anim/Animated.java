package com.ensox.ui.anim;

import com.ensox.base.Animatable;
import com.ensox.base.Easing;
import com.ensox.ui.core.Widget;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A value that moves smoothly to a target over real time.
 *
 * <pre>
 * Animated&lt;Sizing&gt; height = Animated.of(this, Sizing.content(), v -&gt; content.style().height(v));
 * height.animateTo(Sizing.fixed(0), 200, Easing.EASE_IN_OUT);
 * </pre>
 *
 * <p>The value is applied through {@code apply} on every frame while it moves.
 * It is ticked by the owner's {@link com.ensox.ui.core.UiRoot} only while
 * running, so idle animations cost nothing.</p>
 */
public final class Animated<T extends Animatable<T>> {
    private final Widget owner;
    private final Consumer<T> apply;
    private T from;
    private T to;
    private T current;
    private Easing easing = Easing.LINEAR;
    private float durationMs;
    private float elapsedMs;
    private boolean running;

    private Animated(Widget owner, T initial, Consumer<T> apply) {
        this.owner = owner;
        this.apply = apply;
        this.from = initial;
        this.to = initial;
        this.current = initial;
    }

    public static <T extends Animatable<T>> Animated<T> of(Widget owner, T initial, Consumer<T> apply) {
        Animated<T> animated = new Animated<>(owner, initial, apply);
        apply.accept(initial);
        return animated;
    }

    public T get() {
        return current;
    }

    public T target() {
        return to;
    }

    public boolean isRunning() {
        return running;
    }

    /** Starts moving from the current value to {@code target}. */
    public void animateTo(T target, int durationMs, Easing easing) {
        Objects.requireNonNull(target);
        if (durationMs <= 0 || owner.root() == null) {
            snapTo(target);
            return;
        }
        this.from = current;
        this.to = target;
        this.easing = easing;
        this.durationMs = durationMs;
        this.elapsedMs = 0;
        if (!running) {
            running = true;
            owner.root().animations().add(this::tick);
        }
    }

    public void snapTo(T value) {
        this.from = value;
        this.to = value;
        this.current = value;
        this.running = false;
        apply.accept(value);
    }

    /** @return {@code false} once finished, which removes it from the root. */
    private boolean tick(float deltaSeconds) {
        if (!running) {
            return false;
        }
        elapsedMs += deltaSeconds * 1000f;
        float progress = Math.min(1f, elapsedMs / durationMs);
        current = progress >= 1f ? to : from.interpolate(to, easing.apply(progress));
        apply.accept(current);
        if (progress >= 1f) {
            running = false;
        }
        return running;
    }
}
