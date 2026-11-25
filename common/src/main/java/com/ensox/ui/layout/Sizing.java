package com.ensox.ui.layout;

import com.ensox.base.Animatable;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * How a widget chooses its size on one axis.
 *
 * <ul>
 *     <li>{@link #fixed(int)}: exact size;</li>
 *     <li>{@link #content()}: size of the content plus padding, optionally
 *     with extra space on both sides;</li>
 *     <li>{@link #fill(int)}: percentage of the space the parent offers;</li>
 *     <li>{@link #grow(int)}: a share of the main-axis space left after the
 *     non-growing siblings of a flow, split between growing siblings by weight
 *     ({@code grow()} = 100) like CSS {@code flex-grow}; anywhere else (cross
 *     axis, non-flow parents) it takes all the offered space.</li>
 * </ul>
 *
 * <p>Rounding matches the previous Elx layout ({@code Math.round}) so ported
 * screens keep their exact pixel sizes.</p>
 */
public final class Sizing implements Animatable<Sizing> {
    public enum Kind {
        FIXED,
        CONTENT,
        FILL,
        GROW
    }

    private static final Sizing CONTENT = new Sizing(Kind.CONTENT, 0, null, 0);
    private static final Sizing FILL = new Sizing(Kind.FILL, 100, null, 0);
    private static final Sizing GROW = new Sizing(Kind.GROW, 100, null, 0);

    private final Kind kind;
    private final int value;
    /** Target of an in-progress animation; resolved lazily against the real space. */
    private final Sizing animationTarget;
    private final float animationDelta;

    private Sizing(Kind kind, int value, Sizing animationTarget, float animationDelta) {
        this.kind = kind;
        this.value = value;
        this.animationTarget = animationTarget;
        this.animationDelta = animationDelta;
    }

    public static Sizing fixed(int size) {
        return new Sizing(Kind.FIXED, size, null, 0);
    }

    public static Sizing content() {
        return CONTENT;
    }

    /** Content size plus {@code extra} on both sides. */
    public static Sizing content(int extra) {
        return extra == 0 ? CONTENT : new Sizing(Kind.CONTENT, extra, null, 0);
    }

    public static Sizing fill() {
        return FILL;
    }

    public static Sizing fill(int percent) {
        return percent == 100 ? FILL : new Sizing(Kind.FILL, percent, null, 0);
    }

    public static Sizing grow() {
        return GROW;
    }

    /** @param weight relative share of the remainder; {@code grow(2)} next to {@code grow(1)} gets twice the space */
    public static Sizing grow(int weight) {
        return weight == 100 ? GROW : new Sizing(Kind.GROW, weight, null, 0);
    }

    /** Growth weight of a {@code grow} sizing; 0 for other kinds. */
    public int weight() {
        return kind == Kind.GROW ? value : 0;
    }

    public Kind kind() {
        return kind;
    }

    public int value() {
        return value;
    }

    /** Content-dependent: the parent must offer its own available space to children on this axis. */
    public boolean isContent() {
        return kind == Kind.CONTENT || (animationTarget != null && animationTarget.isContent());
    }

    public boolean isGrow() {
        return kind == Kind.GROW;
    }

    /**
     * @param space   space offered on this axis, already without margins
     * @param content content size including padding; only evaluated when needed
     */
    public int resolve(int space, IntSupplier content) {
        int resolved = resolveOwn(space, content);
        if (animationTarget == null) {
            return resolved;
        }
        return Mth.lerpInt(animationDelta, resolved, animationTarget.resolve(space, content));
    }

    private int resolveOwn(int space, IntSupplier content) {
        return switch (kind) {
            case FIXED -> value;
            case FILL -> Math.round((value / 100f) * space);
            // The flow already sized the space to this child's share.
            case GROW -> space;
            case CONTENT -> content.getAsInt() + value * 2;
        };
    }

    /**
     * Animating between different kinds (for example {@code content()} to
     * {@code fixed(0)}) works because the interpolation is resolved during
     * layout, when the real sizes are known.
     */
    @Override
    public Sizing interpolate(Sizing next, float delta) {
        if (delta <= 0) {
            return this;
        }
        if (delta >= 1) {
            return next;
        }
        return new Sizing(kind, value, next, delta);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Sizing other && other.kind == kind && other.value == value
            && Objects.equals(other.animationTarget, animationTarget) && other.animationDelta == animationDelta;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value, animationTarget, animationDelta);
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase(Locale.ROOT) + "(" + value + ")";
    }
}
