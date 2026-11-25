package com.ensox.ui.paint.element;

import com.ensox.base.ColorX;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/** Gradient geometry stays local; bounds and scissor are in screen space, as in vanilla GUI states. */
public record GradientQuadElementRenderState(
    RenderPipeline pipeline,
    Matrix3x2f pose,
    ScreenRectangle rectangle,
    @Nullable ScreenRectangle scissorArea,
    ColorX colorTL,
    ColorX colorTR,
    ColorX colorBL,
    ColorX colorBR
) implements GuiElementRenderState {
    @Override
    public void buildVertices(VertexConsumer vertices) {
        vertices.addVertexWith2DPose(this.pose, this.rectangle.left(), this.rectangle.top()).setColor(this.colorTL.toIntARGB());
        vertices.addVertexWith2DPose(this.pose, this.rectangle.left(), this.rectangle.bottom()).setColor(this.colorBL.toIntARGB());
        vertices.addVertexWith2DPose(this.pose, this.rectangle.right(), this.rectangle.bottom()).setColor(this.colorBR.toIntARGB());
        vertices.addVertexWith2DPose(this.pose, this.rectangle.right(), this.rectangle.top()).setColor(this.colorTR.toIntARGB());
    }

    @Override
    public RenderPipeline pipeline() {
        return this.pipeline;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return this.scissorArea;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        ScreenRectangle screenBounds = this.rectangle.transformMaxBounds(this.pose);
        return this.scissorArea != null ? this.scissorArea.intersection(screenBounds) : screenBounds;
    }
}
