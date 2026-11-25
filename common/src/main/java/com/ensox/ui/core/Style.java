package com.ensox.ui.core;

import com.ensox.base.Insets;
import com.ensox.ui.layout.Align;
import com.ensox.ui.layout.Position;
import com.ensox.ui.layout.Sizing;
import com.ensox.ui.paint.Paint;
import com.mojang.blaze3d.platform.cursor.CursorType;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Presentation and layout properties of one widget.
 *
 * <p>Every setter returns the style, so a widget is described in one chain.
 * Reusable looks are plain {@code Consumer<Style>} constants applied with
 * {@link #apply(Consumer[])} or {@code Widget.style(STYLE, s -> ...)}:</p>
 * <pre>
 * static final Consumer&lt;Style&gt; PANEL = s -&gt; s.padding(Insets.of(10, 9)).gap(6).background(Theme.PANEL);
 *
 * Column.of(header, body).style(PANEL, s -&gt; s.size(Sizing.fill(), Sizing.fill()).maxSize(390, 260));
 * </pre>
 *
 * <p>Layout-affecting setters mark the layout dirty only when the value
 * actually changes; the tree is re-laid out once before the next frame.</p>
 */
public final class Style {
    private final Widget owner;

    private Sizing width = Sizing.content();
    private Sizing height = Sizing.content();
    private int minWidth;
    private int minHeight;
    private int maxWidth = Integer.MAX_VALUE;
    private int maxHeight = Integer.MAX_VALUE;
    private Insets padding = Insets.none();
    private Insets margin = Insets.none();
    private int gap;
    private Align alignX = Align.START;
    private Align alignY = Align.START;
    private Position position = Position.flow();
    private boolean clip = true;
    private boolean visible = true;
    private Paint background = Paint.NONE;
    private Paint foreground = Paint.NONE;
    private @Nullable CursorType cursor;
    private @Nullable Supplier<List<Component>> tooltip;

    Style(Widget owner) {
        this.owner = owner;
    }

    @SafeVarargs
    public final Style apply(Consumer<Style>... styles) {
        for (Consumer<Style> style : styles) {
            style.accept(this);
        }
        return this;
    }

    // ---- size ------------------------------------------------------------

    public Style width(Sizing width) {
        if (!this.width.equals(width)) {
            this.width = Objects.requireNonNull(width);
            owner.invalidateLayout();
        }
        return this;
    }

    public Style height(Sizing height) {
        if (!this.height.equals(height)) {
            this.height = Objects.requireNonNull(height);
            owner.invalidateLayout();
        }
        return this;
    }

    public Style width(int fixed) {
        return width(Sizing.fixed(fixed));
    }

    public Style height(int fixed) {
        return height(Sizing.fixed(fixed));
    }

    public Style size(Sizing width, Sizing height) {
        return width(width).height(height);
    }

    public Style size(int width, int height) {
        return width(Sizing.fixed(width)).height(Sizing.fixed(height));
    }

    /** Fills the parent on both axes. */
    public Style fill() {
        return size(Sizing.fill(), Sizing.fill());
    }

    public Style minSize(int minWidth, int minHeight) {
        if (this.minWidth != minWidth || this.minHeight != minHeight) {
            this.minWidth = Math.max(0, minWidth);
            this.minHeight = Math.max(0, minHeight);
            owner.invalidateLayout();
        }
        return this;
    }

    /** Maximum size; {@code <= 0} means unbounded. */
    public Style maxSize(int maxWidth, int maxHeight) {
        int w = maxWidth <= 0 ? Integer.MAX_VALUE : maxWidth;
        int h = maxHeight <= 0 ? Integer.MAX_VALUE : maxHeight;
        if (this.maxWidth != w || this.maxHeight != h) {
            this.maxWidth = w;
            this.maxHeight = h;
            owner.invalidateLayout();
        }
        return this;
    }

    // ---- spacing -----------------------------------------------------------

    public Style padding(Insets padding) {
        if (!this.padding.equals(padding)) {
            this.padding = Objects.requireNonNull(padding);
            owner.invalidateLayout();
        }
        return this;
    }

    /** CSS shorthand, see {@link Insets#parse(String)}. */
    public Style padding(String shorthand) {
        return padding(Insets.parse(shorthand));
    }

    public Style margin(Insets margin) {
        if (!this.margin.equals(margin)) {
            this.margin = Objects.requireNonNull(margin);
            owner.invalidateLayout();
        }
        return this;
    }

    public Style margin(String shorthand) {
        return margin(Insets.parse(shorthand));
    }

    /** Space between flow children. */
    public Style gap(int gap) {
        if (this.gap != gap) {
            this.gap = gap;
            owner.invalidateLayout();
        }
        return this;
    }

    // ---- placement -----------------------------------------------------------

    /** Alignment of this container's children. */
    public Style align(Align x, Align y) {
        if (this.alignX != x || this.alignY != y) {
            this.alignX = x;
            this.alignY = y;
            owner.invalidateLayout();
        }
        return this;
    }

    public Style alignX(Align x) {
        return align(x, alignY);
    }

    public Style alignY(Align y) {
        return align(alignX, y);
    }

    /** Centers children on both axes. */
    public Style center() {
        return align(Align.CENTER, Align.CENTER);
    }

    public Style position(Position position) {
        if (!this.position.equals(position)) {
            this.position = Objects.requireNonNull(position);
            owner.invalidateLayout();
        }
        return this;
    }

    /** Whether children are clipped to the content box. On by default. */
    public Style clip(boolean clip) {
        this.clip = clip;
        return this;
    }

    /** Allows children to draw outside the content box. */
    public Style overflow() {
        return clip(false);
    }

    /** Invisible widgets take no space, are not drawn and receive no input. */
    public Style visible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            owner.invalidateLayout();
        }
        return this;
    }

    // ---- look and behavior ---------------------------------------------------

    public Style background(Paint background) {
        this.background = Objects.requireNonNull(background);
        return this;
    }

    /** Drawn over the content and children, for frames and overlays. */
    public Style foreground(Paint foreground) {
        this.foreground = Objects.requireNonNull(foreground);
        return this;
    }

    /** Cursor shown while hovering; inherited by descendants that set none. */
    public Style cursor(@Nullable CursorType cursor) {
        this.cursor = cursor;
        return this;
    }

    public Style tooltip(@Nullable Component tooltip) {
        this.tooltip = tooltip == null ? null : () -> List.of(tooltip);
        return this;
    }

    /** Lazily evaluated tooltip lines; return an empty list to show none. */
    public Style tooltip(@Nullable Supplier<List<Component>> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    // ---- getters -------------------------------------------------------------

    public Sizing width() {
        return width;
    }

    public Sizing height() {
        return height;
    }

    public int minWidth() {
        return minWidth;
    }

    public int minHeight() {
        return minHeight;
    }

    public int maxWidth() {
        return maxWidth;
    }

    public int maxHeight() {
        return maxHeight;
    }

    public Insets padding() {
        return padding;
    }

    public Insets margin() {
        return margin;
    }

    public int gap() {
        return gap;
    }

    public Align alignX() {
        return alignX;
    }

    public Align alignY() {
        return alignY;
    }

    public Position position() {
        return position;
    }

    public boolean clip() {
        return clip;
    }

    public boolean visible() {
        return visible;
    }

    public Paint background() {
        return background;
    }

    public Paint foreground() {
        return foreground;
    }

    public @Nullable CursorType cursor() {
        return cursor;
    }

    public @Nullable Supplier<List<Component>> tooltip() {
        return tooltip;
    }
}
