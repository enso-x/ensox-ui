package com.ensox.ui.text;

import com.ensox.ui.geom.Rect;
import com.ensox.ui.paint.Canvas;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Paints a {@link TextEditor} into a box and maps pointer positions back to
 * text indices: horizontal scrolling that keeps the caret visible, selection
 * highlight and a blinking caret.
 *
 * <p>Coordinates are those of the current canvas transform, so it also works
 * inside a scaled node. It does not clip: the visible text is cut to the box
 * width, and a widget that needs a hard edge pushes its own clip.</p>
 */
public final class TextLineView {
    private final TextEditor editor;
    private int scroll;
    private long blinkFrom;

    public TextLineView(TextEditor editor) {
        this.editor = editor;
    }

    /** Shows the caret solid again: call after input so it never blinks out while typing. */
    public void restartBlink() {
        blinkFrom = Util.getMillis();
    }

    /** Text index under {@code x}, snapped to the nearer side of the character there. */
    public int indexAt(Font font, Rect box, double x) {
        String text = editor.text();
        int from = Math.min(scroll, text.length());
        String visible = font.plainSubstrByWidth(text.substring(from), box.width());
        int offset = (int) Math.round(x - box.x());
        int index = from + font.plainSubstrByWidth(visible, Math.max(0, offset)).length();
        if (index < text.length()) {
            int next = editor.step(index, 1);
            int left = font.width(text.substring(from, index));
            int right = font.width(text.substring(from, next));
            if (offset - left > right - offset) {
                index = next;
            }
        }
        return index;
    }

    /**
     * @param focused draws the caret and keeps it in view
     * @param hint    shown when the text is empty and the line is not focused
     */
    public void paint(Canvas canvas, Font font, Rect box, int color, boolean shadow, boolean focused,
                      @Nullable Component hint, int hintColor) {
        String text = editor.text();
        updateScroll(font, box.width());
        String visible = font.plainSubstrByWidth(text.substring(scroll), box.width());
        int textY = box.y() + (box.height() - font.lineHeight) / 2;

        if (visible.isEmpty() && !focused && hint != null) {
            canvas.text(hint, box.x(), textY, hintColor, shadow);
        } else {
            canvas.text(visible, box.x(), textY, color, shadow);
        }
        if (editor.hasSelection()) {
            int start = Math.max(scroll, editor.selectionStart());
            int end = Math.min(scroll + visible.length(), editor.selectionEnd());
            if (end > start) {
                int x0 = box.x() + font.width(text.substring(scroll, start));
                int x1 = box.x() + font.width(text.substring(scroll, end));
                canvas.graphics().textHighlight(x0, textY - 1, x1, textY + 1 + font.lineHeight, true);
            }
        }
        boolean blinkOn = (Util.getMillis() - blinkFrom) / 300L % 2L == 0L;
        int caret = editor.caret();
        if (focused && blinkOn && caret >= scroll) {
            int caretX = box.x() + font.width(text.substring(scroll, Math.min(text.length(), caret)));
            canvas.fill(Rect.of(Math.min(caretX, box.right() - 1), textY - 1, 1, font.lineHeight + 2), color);
        }
    }

    /** Keeps the caret inside the visible window. */
    private void updateScroll(Font font, int width) {
        String text = editor.text();
        int caret = editor.caret();
        scroll = Math.min(scroll, text.length());
        if (caret < scroll) {
            scroll = caret;
        }
        while (scroll < caret && font.width(text.substring(scroll, caret)) > Math.max(1, width)) {
            scroll = editor.step(scroll, 1);
        }
    }
}
