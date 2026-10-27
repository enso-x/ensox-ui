package com.ensox.ui.paint.element;

import com.ensox.base.ColorX;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public record GradientQuadElementRenderState(
    RenderPipeline pipeline,
    Matrix3x2f pose,
    ScreenRectangle bounds,
    ScreenRectangle scissorArea,
    ColorX colorTL,
    ColorX colorTR,
    ColorX colorBL,
    ColorX colorBR
) implements GuiElementRenderState {
    @Override
    public void buildVertices(VertexConsumer vertices) {
        vertices.addVertexWith2DPose(this.pose(), (float) this.bounds.left(), (float) this.bounds.top()).setColor(this.colorTL.toIntARGB());
        vertices.addVertexWith2DPose(this.pose(), (float) this.bounds.left(), (float) this.bounds.bottom()).setColor(this.colorBL.toIntARGB());
        vertices.addVertexWith2DPose(this.pose(), (float) this.bounds.right(), (float) this.bounds.bottom()).setColor(this.colorBR.toIntARGB());
        vertices.addVertexWith2DPose(this.pose(), (float) this.bounds.right(), (float) this.bounds.top()).setColor(this.colorTR.toIntARGB());
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
        return this.scissorArea != null ? this.scissorArea.intersection(this.bounds) : this.bounds;
    }
}
