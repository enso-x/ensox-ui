package com.ensox.ui.widget;

import com.ensox.ui.core.Widget;
import com.ensox.ui.event.KeyEvent;
import com.ensox.ui.event.PointerEvent;
import com.ensox.ui.event.TextEvent;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.paint.Canvas;
import com.ensox.ui.paint.Paint;
import com.ensox.ui.state.Signal;
import com.ensox.ui.text.TextEditor;
import com.ensox.ui.text.TextLineView;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Single-line text input.
 *
 * <ul>
 *     <li>Caret and selection by mouse (click, drag, double-click selects a word)
 *     and keyboard (arrows, Home/End, Ctrl for word jumps, Shift to extend).</li>
 *     <li>Clipboard (Ctrl+C/X/V), select all, undo/redo (Ctrl+Z / Ctrl+Y or Ctrl+Shift+Z).</li>
 *     <li>{@link #filter(Predicate)} validates every edit as a whole string;
 *     rejected edits leave the text unchanged.</li>
 *     <li>{@link #onCommit(Consumer)} fires on Enter and when focus leaves;
 *     {@link #onChange(Consumer)} on every accepted edit.</li>
 * </ul>
 *
 * The editing itself lives in {@link TextEditor} and the painting in
 * {@link TextLineView}, shared with widgets that draw editable text themselves.
 */
public class TextField extends Widget {
    private final Font font = Minecraft.getInstance().font;
    private final TextEditor editor = new TextEditor();
    private final TextLineView view = new TextLineView(editor);

    private @Nullable Component hint;
    private int textColor = 0xFFE0E0E0;
    private int disabledColor = 0xFF707070;
    private int hintColor = 0xFF707070;
    private boolean shadow = true;
    private boolean dragging;
    private Paint background = Paint.NONE;
    private Paint focusedBackground = Paint.NONE;
    private @Nullable Consumer<String> onCommit;

    public TextField() {
        focusable(true);
        style().cursor(CursorTypes.IBEAM);
    }

    /** Two-way binding: edits write the signal, external signal changes replace the text. */
    public static TextField of(Signal<String> value) {
        TextField field = new TextField();
        field.bind(value, next -> {
            if (!field.text().equals(next)) {
                field.editor.setText(next == null ? "" : next, false);
            }
        });
        field.onChange(value::set);
        return field;
    }

    // ---- configuration --------------------------------------------------------------------

    public String text() {
        return editor.text();
    }

    /** Replaces the text without notifying listeners or recording undo. */
    public TextField text(String text) {
        editor.setText(text, false);
        return this;
    }

    public TextField maxLength(int maxLength) {
        editor.maxLength(maxLength);
        return this;
    }

    public TextField filter(Predicate<String> filter) {
        editor.filter(filter);
        return this;
    }

    public TextField hint(@Nullable Component hint) {
        this.hint = hint;
        return this;
    }

    public TextField textColor(int argb) {
        this.textColor = argb;
        return this;
    }

    public TextField hintColor(int argb) {
        this.hintColor = argb;
        return this;
    }

    public TextField shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public TextField editable(boolean editable) {
        editor.editable(editable);
        return this;
    }

    /** Frame drawn behind the text; switches to {@code focused} while editing. */
    public TextField background(Paint normal, Paint focused) {
        this.background = normal;
        this.focusedBackground = focused;
        return this;
    }

    public TextField onChange(Consumer<String> onChange) {
        editor.onChange(onChange);
        return this;
    }

    public TextField onCommit(Consumer<String> onCommit) {
        this.onCommit = onCommit;
        return this;
    }

    public void selectAll() {
        editor.selectAll();
    }

    private void commit() {
        if (onCommit != null) {
            onCommit.accept(text());
        }
    }

    // ---- layout and painting -------------------------------------------------------------------

    @Override
    protected int contentWidth() {
        return Math.max(font.width(text()), 8);
    }

    @Override
    protected int contentHeight(int contentWidth) {
        return font.lineHeight;
    }

    @Override
    protected void paint(Canvas canvas, Frame frame) {
        (isFocused() ? focusedBackground : background).paint(canvas, bounds());
        Rect box = contentBounds();
        int color = isEnabled() && editor.editable() ? textColor : disabledColor;
        canvas.pushClip(box);
        view.paint(canvas, font, box, color, shadow, isFocused(), hint, hintColor);
        canvas.popClip();
    }

    // ---- input --------------------------------------------------------------------------------------

    @Override
    protected void onPointer(PointerEvent event) {
        switch (event.type()) {
            case PRESS -> {
                if (!event.isPrimary()) {
                    return;
                }
                int index = view.indexAt(font, contentBounds(), event.x());
                if (event.isDoubleClick()) {
                    editor.selectWordAt(index);
                } else {
                    editor.moveCaret(index, event.modifiers().shift());
                }
                view.restartBlink();
                dragging = true;
                event.consume();
            }
            case DRAG -> {
                if (dragging) {
                    editor.moveCaret(view.indexAt(font, contentBounds(), event.x()), true);
                    view.restartBlink();
                    event.consume();
                }
            }
            case RELEASE, CANCEL -> dragging = false;
            default -> {
            }
        }
    }

    @Override
    protected void onFocusChanged(boolean focused) {
        view.restartBlink();
        if (!focused) {
            editor.deselect();
            commit();
        }
    }

    @Override
    protected void onText(TextEvent event) {
        if (isFocused() && editor.type(event)) {
            event.consume();
        }
    }

    @Override
    protected void onKey(KeyEvent event) {
        if (event.type() != KeyEvent.Type.PRESS || !isFocused()) {
            return;
        }
        boolean handled = editor.press(event);
        if (!handled && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
            // Escape and Tab stay unhandled: the root uses them for overlays, focus and closing.
            commit();
            handled = true;
        }
        if (handled) {
            view.restartBlink();
            event.consume();
        }
    }
}
