package com.ensox.ui.paint.element;

import com.ensox.base.ColorX;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/** A cubic horizontal Bezier submitted as one GUI element instead of one element per segment. */
public record BezierLineElementRenderState(
    RenderPipeline pipeline,
    Matrix3x2f pose,
    @Nullable ScreenRectangle scissorArea,
    int x0,
    int y0,
    int x1,
    int y1,
    double thickness,
    ColorX color,
    int segments
) implements GuiElementRenderState {
    @Override
    public void buildVertices(VertexConsumer vertices) {
        float handle = Math.max(28.0F, Math.abs(this.x1 - this.x0) * 0.45F);
        float previousX = this.x0;
        float previousY = this.y0;
        int packedColor = this.color.toIntARGB();

        for (int index = 1; index <= this.segments; index++) {
            float t = index / (float) this.segments;
            float inverse = 1.0F - t;
            float x = inverse * inverse * inverse * this.x0
                + 3.0F * inverse * inverse * t * (this.x0 + handle)
                + 3.0F * inverse * t * t * (this.x1 - handle)
                + t * t * t * this.x1;
            float y = inverse * inverse * inverse * this.y0
                + 3.0F * inverse * inverse * t * this.y0
                + 3.0F * inverse * t * t * this.y1
                + t * t * t * this.y1;
            addSegment(vertices, previousX, previousY, x, y, packedColor);
            previousX = x;
            previousY = y;
        }
    }

    private void addSegment(VertexConsumer vertices, float startX, float startY, float endX, float endY, int color) {
        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double length = Math.hypot(deltaX, deltaY);
        if (length < 1.0E-6D) return;

        double halfWidth = this.thickness * 0.5D;
        double offsetX = guiNormalX(deltaY, length, halfWidth);
        double offsetY = guiNormalY(deltaX, length, halfWidth);
        vertices.addVertexWith2DPose(this.pose, (float) (startX + offsetX), (float) (startY + offsetY)).setColor(color);
        vertices.addVertexWith2DPose(this.pose, (float) (startX - offsetX), (float) (startY - offsetY)).setColor(color);
        vertices.addVertexWith2DPose(this.pose, (float) (endX - offsetX), (float) (endY - offsetY)).setColor(color);
        vertices.addVertexWith2DPose(this.pose, (float) (endX + offsetX), (float) (endY + offsetY)).setColor(color);
    }

    // GUI uses face culling, so quads must follow vanilla's top-left -> bottom-left winding.
    static double guiNormalX(double deltaY, double length, double halfWidth) {
        return deltaY / length * halfWidth;
    }

    static double guiNormalY(double deltaX, double length, double halfWidth) {
        return -deltaX / length * halfWidth;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        float handle = Math.max(28.0F, Math.abs(this.x1 - this.x0) * 0.45F);
        int padding = Math.max(1, (int) Math.ceil(this.thickness));
        int left = (int) Math.floor(Math.min(Math.min(this.x0, this.x1), Math.min(this.x0 + handle, this.x1 - handle))) - padding;
        int top = Math.min(this.y0, this.y1) - padding;
        int right = (int) Math.ceil(Math.max(Math.max(this.x0, this.x1), Math.max(this.x0 + handle, this.x1 - handle))) + padding;
        int bottom = Math.max(this.y0, this.y1) + padding;
        ScreenRectangle bounds = new ScreenRectangle(left, top, right - left, bottom - top);
        return this.scissorArea == null ? bounds : this.scissorArea.intersection(bounds);
    }
}
