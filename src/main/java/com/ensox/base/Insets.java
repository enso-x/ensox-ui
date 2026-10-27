package com.ensox.base;

import net.minecraft.util.Mth;

/**
 * Four-sided spacing value used for padding, margins, nine-slice borders and
 * hit-area expansion.
 *
 * <p>Every API that takes spacing takes an {@code Insets}, so there is one
 * input format everywhere. The factories follow CSS shorthand order:</p>
 * <pre>
 * Insets.of(4)           // all sides
 * Insets.of(4, 8)        // vertical, horizontal
 * Insets.of(4, 8, 2)     // top, horizontal, bottom
 * Insets.of(4, 8, 2, 1)  // top, right, bottom, left
 * Insets.parse("4 8")    // the same shorthand from data files
 * </pre>
 */
public record Insets(int top, int right, int bottom, int left) implements Animatable<Insets> {
    private static final Insets NONE = new Insets(0, 0, 0, 0);

    public Insets inverted() {
        return new Insets(-this.top, -this.right, -this.bottom, -this.left);
    }

    public Insets add(Insets insets) {
        return new Insets(this.top + insets.top(), this.right + insets.right(), this.bottom + insets.bottom(), this.left + insets.left());
    }

    public Insets withTop(int top) {
        return new Insets(top, this.right, this.bottom, this.left);
    }

    public Insets withRight(int right) {
        return new Insets(this.top, right, this.bottom, this.left);
    }

    public Insets withBottom(int bottom) {
        return new Insets(this.top, this.right, bottom, this.left);
    }

    public Insets withLeft(int left) {
        return new Insets(this.top, this.right, this.bottom, left);
    }

    /** Sum of left and right. */
    public int horizontal() {
        return this.left + this.right;
    }

    /** Sum of top and bottom. */
    public int vertical() {
        return this.top + this.bottom;
    }

    public boolean isNone() {
        return this.top == 0 && this.right == 0 && this.bottom == 0 && this.left == 0;
    }

    @Override
    public Insets interpolate(Insets next, float delta) {
        return new Insets(
            Mth.lerpInt(delta, this.top, next.top),
            Mth.lerpInt(delta, this.right, next.right),
            Mth.lerpInt(delta, this.bottom, next.bottom),
            Mth.lerpInt(delta, this.left, next.left)
        );
    }

    public static Insets top(int top) {
        return new Insets(top, 0, 0, 0);
    }

    public static Insets right(int right) {
        return new Insets(0, right, 0, 0);
    }

    public static Insets bottom(int bottom) {
        return new Insets(0, 0, bottom, 0);
    }

    public static Insets left(int left) {
        return new Insets(0, 0, 0, left);
    }

    public static Insets of(int top, int right, int bottom, int left) {
        return new Insets(top, right, bottom, left);
    }

    public static Insets of(int top, int horizontal, int bottom) {
        return new Insets(top, horizontal, bottom, horizontal);
    }

    public static Insets of(int vertical, int horizontal) {
        return new Insets(vertical, horizontal, vertical, horizontal);
    }

    public static Insets of(int inset) {
        return inset == 0 ? NONE : new Insets(inset, inset, inset, inset);
    }

    public static Insets vertical(int inset) {
        return new Insets(inset, 0, inset, 0);
    }

    public static Insets horizontal(int inset) {
        return new Insets(0, inset, 0, inset);
    }

    public static Insets none() {
        return NONE;
    }

    /**
     * Parses CSS-like shorthand: one to four integers separated by spaces or
     * commas, interpreted exactly like the {@code of(...)} overloads.
     *
     * @throws IllegalArgumentException for an empty value, more than four
     *                                  numbers or a non-integer token
     */
    public static Insets parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Insets value is empty");
        }
        String[] tokens = value.trim().split("[\\s,]+");
        int[] numbers = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                numbers[i] = Integer.parseInt(tokens[i].endsWith("px") ? tokens[i].substring(0, tokens[i].length() - 2) : tokens[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid insets token '" + tokens[i] + "' in '" + value + "'", e);
            }
        }
        return switch (numbers.length) {
            case 1 -> of(numbers[0]);
            case 2 -> of(numbers[0], numbers[1]);
            case 3 -> of(numbers[0], numbers[1], numbers[2]);
            case 4 -> of(numbers[0], numbers[1], numbers[2], numbers[3]);
            default -> throw new IllegalArgumentException("Insets take 1 to 4 values, got " + numbers.length + " in '" + value + "'");
        };
    }
}
