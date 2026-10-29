package com.ensox.ui.core;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Vanilla {@link Screen} hosting a widget tree. This is the only integration
 * point with Minecraft input: vanilla already delivers every mouse, scroll,
 * key and character event to the active screen, so no input mixins exist.
 *
 * <pre>
 * public final class MyScreen extends UiScreen {
 *     protected Widget build() {
 *         return Column.of(Label.of(title), Button.of(DONE, this::onClose))
 *             .style(s -&gt; s.fill().center().gap(4));
 *     }
 * }
 * </pre>
 *
 * <p>The tree is built once. Resizing only re-lays it out, and leaving the
 * screen (for example opening a child screen) detaches it so bindings close;
 * returning re-attaches the same tree with its state intact.</p>
 */
public abstract class UiScreen extends Screen {
    private UiRoot root;

    protected UiScreen(Component title) {
        super(title);
    }

    /** Creates the widget tree. Called once, on first {@link #init()}. */
    protected abstract Widget build();

    public UiRoot ui() {
        return root;
    }

    @Override
    protected void init() {
        if (root == null) {
            root = new UiRoot(build());
        } else if (!root.content().isAttached()) {
            root.attach();
        }
        root.resize(this.width, this.height);
    }

    @Override
    public void removed() {
        if (root != null) {
            root.detach();
        }
        super.removed();
    }

    /**
     * Whether the vanilla screen background (darkened world or menu panorama)
     * is drawn behind the tree. Off by default: screens draw their own
     * backgrounds as widgets.
     */
    protected boolean vanillaBackground() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (vanillaBackground()) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (root != null) {
            root.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (root != null) {
            root.pointerMoved(mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return root != null && root.pointerPressed(event.x(), event.y(), event.button(), event.modifiers(), doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return root != null && root.pointerReleased(event.x(), event.y(), event.button(), event.modifiers());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return root != null && root.pointerDragged(event.x(), event.y(), event.button(), event.modifiers(), deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        return root != null && root.scrolled(mouseX, mouseY, amountX, amountY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (root != null && root.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return root != null && root.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return root != null && root.textTyped(event.codepoint(), heldModifiers());
    }

    /** A CharacterEvent no longer carries modifiers (26.1): read the keys held right now. */
    private static int heldModifiers() {
        Minecraft minecraft = Minecraft.getInstance();
        return (minecraft.hasShiftDown() ? InputConstants.MOD_SHIFT : 0)
            | (minecraft.hasControlDown() ? InputConstants.MOD_CONTROL : 0)
            | (minecraft.hasAltDown() ? InputConstants.MOD_ALT : 0);
    }
}
