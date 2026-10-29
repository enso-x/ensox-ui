package com.ensox.ui.paint;

import com.ensox.base.ColorX;
import com.ensox.ui.asset.AssetKey;
import com.ensox.ui.asset.AssetLibrary;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.paint.element.BezierLineElementRenderState;
import com.ensox.ui.paint.element.GradientQuadElementRenderState;
import com.ensox.ui.paint.element.LineElementRenderState;
import com.ensox.ui.paint.element.PolylineElementRenderState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Drawing surface for widgets: a thin, rectangle-oriented facade over
 * {@link GuiGraphicsExtractor}. Everything drawn goes through the vanilla GUI render
 * state, so batching, strata and tooltips behave like vanilla.
 *
 * <p>{@link #graphics()} is the escape hatch for anything not covered here,
 * including custom {@code GuiElementRenderState}s.</p>
 */
public final class Canvas {
    private final GuiGraphicsExtractor graphics;
    private final Font font;
    /** Clip rectangles in GUI space, innermost last; mirrors the vanilla scissor stack. */
    private final Deque<Rect> clips = new ArrayDeque<>();

    public Canvas(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
        this.font = Minecraft.getInstance().font;
    }

    public GuiGraphicsExtractor graphics() {
        return graphics;
    }

    public Font font() {
        return font;
    }

    // ---- shapes -------------------------------------------------------------

    public void fill(Rect rect, int argb) {
        if (!rect.isEmpty() && (argb >>> 24) != 0) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), argb);
        }
    }

    public void fill(Rect rect, ColorX color) {
        fill(rect, color.toIntARGB());
    }

    public void verticalGradient(Rect rect, int topArgb, int bottomArgb) {
        if (!rect.isEmpty()) {
            graphics.fillGradient(rect.x(), rect.y(), rect.right(), rect.bottom(), topArgb, bottomArgb);
        }
    }

    public void outline(Rect rect, int argb) {
        if (!rect.isEmpty()) {
            graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), argb);
        }
    }

    public void horizontalLine(int x1, int x2, int y, int argb) {
        graphics.horizontalLine(x1, x2, y, argb);
    }

    public void verticalLine(int x, int y1, int y2, int argb) {
        graphics.verticalLine(x, y1, y2, argb);
    }

    /** Four-corner gradient, optionally through a custom GUI pipeline (for example an HSV spectrum). */
    public void gradient(RenderPipeline pipeline, Rect rect, ColorX topLeft, ColorX topRight,
                         ColorX bottomRight, ColorX bottomLeft) {
        if (rect.isEmpty()) {
            return;
        }
        submit(new GradientQuadElementRenderState(pipeline, new Matrix3x2f(graphics.pose()),
            new ScreenRectangle(rect.x(), rect.y(), rect.width(), rect.height()), scissorArea(),
            topLeft, topRight, bottomLeft, bottomRight));
    }

    public void gradient(Rect rect, ColorX topLeft, ColorX topRight, ColorX bottomRight, ColorX bottomLeft) {
        gradient(RenderPipelines.GUI, rect, topLeft, topRight, bottomRight, bottomLeft);
    }

    /** Anti-aliasing-free thick line between two points. */
    public void line(int x0, int y0, int x1, int y1, double thickness, ColorX color) {
        submit(new LineElementRenderState(RenderPipelines.GUI, new Matrix3x2f(graphics.pose()), scissorArea(),
            x0, y0, x1, y1, thickness, color));
    }

    /**
     * Thick polyline through fractional points ({@code x0, y0, x1, y1, ...}),
     * with mitred joints. Use it for sampled curves: points are not snapped to
     * GUI pixels, so the result stays smooth instead of stair-stepped.
     */
    public void polyline(float[] points, double thickness, ColorX color) {
        if (points.length >= 4) {
            submit(new PolylineElementRenderState(RenderPipelines.GUI, new Matrix3x2f(graphics.pose()), scissorArea(),
                points.clone(), thickness, color));
        }
    }

    /** Horizontal-tangent cubic Bezier between two points, as used for node graph wires. */
    public void bezier(int x0, int y0, int x1, int y1, double thickness, ColorX color, int segments) {
        submit(new BezierLineElementRenderState(RenderPipelines.GUI, new Matrix3x2f(graphics.pose()), scissorArea(),
            x0, y0, x1, y1, thickness, color, Math.max(1, segments)));
    }

    /**
     * Submits a custom GUI element. Use {@link #scissorArea()} for its scissor so
     * it respects widget clipping.
     */
    public void submit(GuiElementRenderState element) {
        graphics.guiRenderState.addGuiElement(element);
    }

    /** Current vanilla scissor (already transformed by the pose), or {@code null}. */
    public @Nullable ScreenRectangle scissorArea() {
        return graphics.scissorStack.peek();
    }

    // ---- sprites and textures -----------------------------------------------

    /** GUI-atlas sprite; stretch/tile/nine-slice comes from the sprite {@code .mcmeta}. */
    public void sprite(Identifier sprite, Rect rect) {
        sprite(sprite, rect, -1);
    }

    public void sprite(Identifier sprite, Rect rect, int tintArgb) {
        if (!rect.isEmpty()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, rect.x(), rect.y(), rect.width(), rect.height(), tintArgb);
        }
    }

    /** Library asset by key; see {@link AssetLibrary}. */
    public void asset(AssetKey key, Rect rect) {
        asset(key, rect, -1);
    }

    public void asset(AssetKey key, Rect rect, int tintArgb) {
        if (!rect.isEmpty()) {
            AssetLibrary.get(key).paint(this, rect, tintArgb);
        }
    }

    /** Icon by short name ({@code icon/<name>} in the library default namespace). */
    public void icon(String name, Rect rect, int tintArgb) {
        if (!rect.isEmpty()) {
            AssetLibrary.icon(name).paint(this, rect, tintArgb);
        }
    }

    /** Texture region scaled to {@code rect}. */
    public void texture(TextureRegion region, Rect rect, int tintArgb) {
        if (rect.isEmpty() || region.width() <= 0 || region.height() <= 0) {
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, region.texture(), rect.x(), rect.y(), region.u(), region.v(),
            rect.width(), rect.height(), region.width(), region.height(),
            region.textureWidth(), region.textureHeight(), tintArgb);
    }

    /** Texture region repeated 1:1 across {@code rect}; partial tiles are cropped, not squashed. */
    public void tiled(TextureRegion region, Rect rect, int tintArgb) {
        if (rect.isEmpty() || region.width() <= 0 || region.height() <= 0) {
            return;
        }
        for (int y = rect.y(); y < rect.bottom(); y += region.height()) {
            int h = Math.min(region.height(), rect.bottom() - y);
            for (int x = rect.x(); x < rect.right(); x += region.width()) {
                int w = Math.min(region.width(), rect.right() - x);
                graphics.blit(RenderPipelines.GUI_TEXTURED, region.texture(), x, y, region.u(), region.v(),
                    w, h, w, h, region.textureWidth(), region.textureHeight(), tintArgb);
            }
        }
    }

    public void nineSlice(NineSlice slice, Rect rect, int tintArgb) {
        if (rect.isEmpty()) {
            return;
        }
        TextureRegion r = slice.region();
        var b = slice.border();
        // Corners shrink proportionally when the target is smaller than the border sum.
        int left = Math.min(b.left(), rect.width() / 2);
        int right = Math.min(b.right(), rect.width() - left);
        int top = Math.min(b.top(), rect.height() / 2);
        int bottom = Math.min(b.bottom(), rect.height() - top);
        int innerW = rect.width() - left - right;
        int innerH = rect.height() - top - bottom;
        int srcInnerW = r.width() - b.left() - b.right();
        int srcInnerH = r.height() - b.top() - b.bottom();

        int x0 = rect.x(), x1 = x0 + left, x2 = rect.right() - right;
        int y0 = rect.y(), y1 = y0 + top, y2 = rect.bottom() - bottom;
        int u0 = r.u(), u1 = u0 + b.left(), u2 = u0 + r.width() - b.right();
        int v0 = r.v(), v1 = v0 + b.top(), v2 = v0 + r.height() - b.bottom();

        // corners
        part(r, x0, y0, left, top, u0, v0, left, top, tintArgb);
        part(r, x2, y0, right, top, u2, v0, right, top, tintArgb);
        part(r, x0, y2, left, bottom, u0, v2, left, bottom, tintArgb);
        part(r, x2, y2, right, bottom, u2, v2, right, bottom, tintArgb);
        // edges
        boolean tileEdges = slice.edges() == NineSlice.FillMode.TILE;
        area(r, Rect.of(x1, y0, innerW, top), u1, v0, srcInnerW, top, tileEdges, tintArgb);
        area(r, Rect.of(x1, y2, innerW, bottom), u1, v2, srcInnerW, bottom, tileEdges, tintArgb);
        area(r, Rect.of(x0, y1, left, innerH), u0, v1, left, srcInnerH, tileEdges, tintArgb);
        area(r, Rect.of(x2, y1, right, innerH), u2, v1, right, srcInnerH, tileEdges, tintArgb);
        // center
        area(r, Rect.of(x1, y1, innerW, innerH), u1, v1, srcInnerW, srcInnerH,
            slice.center() == NineSlice.FillMode.TILE, tintArgb);
    }

    private void area(TextureRegion r, Rect target, int u, int v, int srcW, int srcH, boolean tile, int tintArgb) {
        if (target.isEmpty() || srcW <= 0 || srcH <= 0) {
            return;
        }
        if (tile) {
            tiled(new TextureRegion(r.texture(), u, v, srcW, srcH, r.textureWidth(), r.textureHeight()), target, tintArgb);
        } else {
            part(r, target.x(), target.y(), target.width(), target.height(), u, v, srcW, srcH, tintArgb);
        }
    }

    private void part(TextureRegion r, int x, int y, int w, int h, int u, int v, int srcW, int srcH, int tintArgb) {
        if (w > 0 && h > 0 && srcW > 0 && srcH > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, r.texture(), x, y, u, v, w, h, srcW, srcH,
                r.textureWidth(), r.textureHeight(), tintArgb);
        }
    }

    /** Raw blit with an explicit pipeline, for custom GUI pipelines. */
    public void blit(RenderPipeline pipeline, TextureRegion region, Rect rect, int tintArgb) {
        graphics.blit(pipeline, region.texture(), rect.x(), rect.y(), region.u(), region.v(),
            rect.width(), rect.height(), region.width(), region.height(),
            region.textureWidth(), region.textureHeight(), tintArgb);
    }

    // ---- text and items -------------------------------------------------------

    public int textWidth(Component text) {
        return font.width(text);
    }

    public int textWidth(String text) {
        return font.width(text);
    }

    public int lineHeight() {
        return font.lineHeight;
    }

    public void text(Component text, int x, int y, int argb, boolean shadow) {
        graphics.text(font, text, x, y, argb, shadow);
    }

    public void text(FormattedCharSequence text, int x, int y, int argb, boolean shadow) {
        graphics.text(font, text, x, y, argb, shadow);
    }

    public void text(String text, int x, int y, int argb, boolean shadow) {
        graphics.text(font, text, x, y, argb, shadow);
    }

    public void item(ItemStack stack, int x, int y) {
        graphics.item(stack, x, y);
    }

    // ---- clipping and transforms -----------------------------------------------

    /** Clips to {@code rect} intersected with the current clip; pair with {@link #popClip()}. */
    public void pushClip(Rect rect) {
        Rect clip = clips.isEmpty() ? rect : clips.peekLast().intersect(rect);
        clips.addLast(clip);
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
    }

    public void popClip() {
        clips.removeLast();
        graphics.disableScissor();
    }

    /** Current clip, or {@code null} when nothing is clipped. */
    public Rect clip() {
        return clips.peekLast();
    }

    public boolean isVisible(Rect rect) {
        Rect clip = clips.peekLast();
        return clip == null || clip.intersects(rect);
    }

    public void pushTranslation(float dx, float dy) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(dx, dy);
    }

    /**
     * Translates, then scales uniformly: content drawn in local units appears
     * {@code scale} times larger at {@code (dx, dy)} (zoomable canvases). Clips
     * pushed before stay in screen space.
     */
    public void pushTransform(float dx, float dy, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(dx, dy);
        graphics.pose().scale(scale, scale);
    }

    public void popTransform() {
        graphics.pose().popMatrix();
    }
}
