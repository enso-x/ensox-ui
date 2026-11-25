package com.ensox.ui.paint.element;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BezierLineElementRenderStateTest {
    @Test
    void horizontalSegmentUsesVanillaGuiFrontFaceNormal() {
        double deltaX = 20.0D;
        double length = Math.abs(deltaX);

        assertEquals(0.0D, BezierLineElementRenderState.guiNormalX(0.0D, length, 1.0D));
        assertEquals(-1.0D, BezierLineElementRenderState.guiNormalY(deltaX, length, 1.0D));
    }
}
