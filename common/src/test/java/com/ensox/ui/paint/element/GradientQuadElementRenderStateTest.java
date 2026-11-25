package com.ensox.ui.paint.element;

import com.ensox.base.ColorX;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GradientQuadElementRenderStateTest {
    private static GradientQuadElementRenderState gradient(Matrix3x2f pose, ScreenRectangle clip) {
        return new GradientQuadElementRenderState(RenderPipelines.GUI, pose, new ScreenRectangle(10, 20, 30, 40), clip,
            ColorX.of(0xFFFF0000), ColorX.of(0xFF00FF00), ColorX.of(0xFF0000FF), ColorX.WHITE);
    }

    @Test void boundsAreTransformedBeforeIntersectingTheScreenScissor() {
        var state = gradient(new Matrix3x2f().translation(100, 50).scale(2), new ScreenRectangle(125, 100, 40, 50));
        assertEquals(new ScreenRectangle(125, 100, 40, 50), state.bounds());
        assertEquals(new ScreenRectangle(120, 90, 60, 80), gradient(new Matrix3x2f().translation(100, 50).scale(2), null).bounds());
        assertNull(gradient(new Matrix3x2f().translation(100, 50), new ScreenRectangle(0, 0, 10, 10)).bounds());
    }

    @Test void rotationUsesAllCornersAndClippingDoesNotChangeGradientGeometry() {
        // Exact quarter-turn avoids introducing floating-point noise into the expected pixel bounds.
        Matrix3x2f pose = new Matrix3x2f(0, 1, -1, 0, 100, 50);
        var state = gradient(pose, new ScreenRectangle(45, 65, 25, 10));
        assertEquals(new ScreenRectangle(40, 60, 40, 30), gradient(pose, null).bounds());
        assertEquals(new ScreenRectangle(45, 65, 25, 10), state.bounds());
        CapturingVertices vertices = new CapturingVertices();
        state.buildVertices(vertices);
        assertEquals(List.of(new Vertex(80, 60, 0xFFFF0000), new Vertex(40, 60, 0xFF0000FF),
            new Vertex(40, 90, 0xFFFFFFFF), new Vertex(80, 90, 0xFF00FF00)), vertices.values);
    }

    private record Vertex(float x, float y, int color) { }

    private static final class CapturingVertices implements VertexConsumer {
        final List<Vertex> values = new ArrayList<>();
        private float x, y;
        @Override public VertexConsumer addVertex(float x, float y, float z) { this.x = x; this.y = y; return this; }
        @Override public VertexConsumer setColor(int color) { values.add(new Vertex(x, y, color)); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { throw new AssertionError(); }
        @Override public VertexConsumer setUv(float u, float v) { throw new AssertionError(); }
        @Override public VertexConsumer setUv1(int u, int v) { throw new AssertionError(); }
        @Override public VertexConsumer setUv2(int u, int v) { throw new AssertionError(); }
        @Override public VertexConsumer setUv3(float u, float v) { throw new AssertionError(); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { throw new AssertionError(); }
        @Override public VertexConsumer setLineWidth(float width) { throw new AssertionError(); }
    }
}
