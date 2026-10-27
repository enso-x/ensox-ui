package com.ensox.ui.paint.element;

import com.ensox.base.ColorX;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * A thick polyline through fractional points, submitted as one GUI element.
 *
 * <p>Points are not rounded to GUI pixels, so curves sampled densely stay
 * smooth at any GUI scale instead of turning into stairs. Neighbouring
 * segments share their joint vertices, offset along the bisector (miter,
 * limited), so bends have no gaps or notches.</p>
 *
 * @param points x0, y0, x1, y1, ... in GUI coordinates (at least two points)
 */
public record PolylineElementRenderState(
    RenderPipeline pipeline,
    Matrix3x2f pose,
    @Nullable ScreenRectangle scissorArea,
    float[] points,
    double thickness,
    ColorX color
) implements GuiElementRenderState {
    /** Caps the miter length at sharp bends (in half widths). */
    private static final double MITER_LIMIT = 2.0;

    @Override
    public void buildVertices(VertexConsumer vertices) {
        int count = points.length / 2;
        if (count < 2) {
            return;
        }
        int packed = color.toIntARGB();
        double half = thickness * 0.5;
        float[] offsets = new float[count * 2];
        for (int i = 0; i < count; i++) {
            double[] in = i > 0 ? normal(i - 1, i) : null;
            double[] out = i < count - 1 ? normal(i, i + 1) : null;
            double nx;
            double ny;
            double scale = half;
            if (in == null || out == null) {
                double[] only = in != null ? in : out;
                nx = only == null ? 0 : only[0];
                ny = only == null ? 0 : only[1];
            } else {
                nx = in[0] + out[0];
                ny = in[1] + out[1];
                double length = Math.hypot(nx, ny);
                if (length < 1.0E-6) {
                    nx = out[0];
                    ny = out[1];
                } else {
                    nx /= length;
                    ny /= length;
                    // Keep the stroke width constant across the bend: half / cos(angle / 2).
                    double cos = nx * out[0] + ny * out[1];
                    scale = half / Math.max(cos, 1.0 / MITER_LIMIT);
                }
            }
            offsets[i * 2] = (float) (nx * scale);
            offsets[i * 2 + 1] = (float) (ny * scale);
        }
        for (int i = 0; i < count - 1; i++) {
            float sx = points[i * 2];
            float sy = points[i * 2 + 1];
            float ex = points[i * 2 + 2];
            float ey = points[i * 2 + 3];
            if (sx == ex && sy == ey) {
                continue;
            }
            float sox = offsets[i * 2];
            float soy = offsets[i * 2 + 1];
            float eox = offsets[i * 2 + 2];
            float eoy = offsets[i * 2 + 3];
            // Same winding as BezierLineElementRenderState: GUI quads are face-culled.
            vertices.addVertexWith2DPose(pose, sx + sox, sy + soy).setColor(packed);
            vertices.addVertexWith2DPose(pose, sx - sox, sy - soy).setColor(packed);
            vertices.addVertexWith2DPose(pose, ex - eox, ey - eoy).setColor(packed);
            vertices.addVertexWith2DPose(pose, ex + eox, ey + eoy).setColor(packed);
        }
    }

    /** Unit GUI normal of segment a -> b, or the previous direction for zero-length segments. */
    private double[] normal(int a, int b) {
        double dx = points[b * 2] - points[a * 2];
        double dy = points[b * 2 + 1] - points[a * 2 + 1];
        double length = Math.hypot(dx, dy);
        if (length < 1.0E-6) {
            return new double[]{0, -1};
        }
        return new double[]{
            BezierLineElementRenderState.guiNormalX(dy, length, 1.0),
            BezierLineElementRenderState.guiNormalY(dx, length, 1.0)
        };
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i + 1 < points.length; i += 2) {
            minX = Math.min(minX, points[i]);
            maxX = Math.max(maxX, points[i]);
            minY = Math.min(minY, points[i + 1]);
            maxY = Math.max(maxY, points[i + 1]);
        }
        int padding = (int) Math.ceil(thickness * MITER_LIMIT) + 1;
        int left = (int) Math.floor(minX) - padding;
        int top = (int) Math.floor(minY) - padding;
        ScreenRectangle bounds = new ScreenRectangle(left, top,
            (int) Math.ceil(maxX) + padding - left, (int) Math.ceil(maxY) + padding - top);
        return scissorArea == null ? bounds : scissorArea.intersection(bounds);
    }
}
