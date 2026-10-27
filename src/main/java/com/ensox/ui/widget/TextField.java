package com.ensox.ui.widget;

import com.ensox.ui.core.Widget;
import com.ensox.ui.event.KeyEvent;
import com.ensox.ui.event.PointerEvent;
import com.ensox.ui.event.TextEvent;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.paint.Canvas;
import com.ensox.ui.paint.Paint;
import com.ensox.ui.state.Signal;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
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
 * Positions are UTF-16 indices that never split a surrogate pair.
 */
public class TextField extends Widget {
    private static final int UNDO_LIMIT = 100;

    private final Font font = Minecraft.getInstance().font;
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();

    private String text = "";
    private int caret;
    private int anchor;
    private int scroll;
    private int maxLength = 256;
    private Predicate<String> filter = value -> true;
    private @Nullable Component hint;
    private int textColor = 0xFFE0E0E0;
    private int disabledColor = 0xFF707070;
    private int hintColor = 0xFF707070;
    private boolean shadow = true;
    private boolean editable = true;
    private long focusTime;
    private boolean dragging;
    private Paint background = Paint.NONE;
    private Paint focusedBackground = Paint.NONE;
    private @Nullable Consumer<String> onChange;
    private @Nullable Consumer<String> onCommit;

    private record Snapshot(String text, int caret, int anchor) {
    }

    public TextField() {
        focusable(true);
        style().cursor(CursorTypes.IBEAM);
    }

    /** Two-way binding: edits write the signal, external signal changes replace the text. */
    public static TextField of(Signal<String> value) {
        TextField field = new TextField();
        field.bind(value, next -> {
            if (!field.text.equals(next)) {
                field.setText(next == null ? "" : next, false);
            }
        });
        field.onChange(value::set);
        return field;
    }

    // ---- configuration --------------------------------------------------------------------

    public String text() {
        return text;
    }

    /** Replaces the text without notifying listeners or recording undo. */
    public TextField text(String text) {
        setText(text, false);
        return this;
    }

    public TextField maxLength(int maxLength) {
        this.maxLength = maxLength;
        if (text.length() > maxLength) {
            setText(text.substring(0, maxLength), false);
        }
        return this;
    }

    public TextField filter(Predicate<String> filter) {
        this.filter = filter;
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
        this.editable = editable;
        return this;
    }

    /** Frame drawn behind the text; switches to {@code focused} while editing. */
    public TextField background(Paint normal, Paint focused) {
        this.background = normal;
        this.focusedBackground = focused;
        return this;
    }

    public TextField onChange(Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    public TextField onCommit(Consumer<String> onCommit) {
        this.onCommit = onCommit;
        return this;
    }

    public void selectAll() {
        anchor = 0;
        caret = text.length();
    }

    // ---- editing core -----------------------------------------------------------------------

    private void setText(String value, boolean notify) {
        String clipped = value.length() > maxLength ? value.substring(0, maxLength) : value;
        this.text = clipped;
        caret = Math.min(caret, text.length());
        anchor = Math.min(anchor, text.length());
        if (notify && onChange != null) {
            onChange.accept(text);
        }
    }

    private boolean hasSelection() {
        return caret != anchor;
    }

    private int selectionStart() {
        return Math.min(caret, anchor);
    }

    private int selectionEnd() {
        return Math.max(caret, anchor);
    }

    /** Replaces the selection with {@code insert}; returns false if the filter rejected the result. */
    private boolean replaceSelection(String insert) {
        if (!editable) {
            return false;
        }
        String filtered = StringUtil.filterText(insert);
        int start = selectionStart();
        int end = selectionEnd();
        int room = maxLength - (text.length() - (end - start));
        if (room < filtered.length()) {
            filtered = filtered.substring(0, Math.max(0, room));
            if (!filtered.isEmpty() && Character.isHighSurrogate(filtered.charAt(filtered.length() - 1))) {
                filtered = filtered.substring(0, filtered.length() - 1);
            }
        }
        String next = text.substring(0, start) + filtered + text.substring(end);
        if (next.equals(text) || !filter.test(next)) {
            return false;
        }
        pushUndo();
        text = next;
        caret = anchor = start + filtered.length();
        if (onChange != null) {
            onChange.accept(text);
        }
        return true;
    }

    private void pushUndo() {
        undo.push(new Snapshot(text, caret, anchor));
        if (undo.size() > UNDO_LIMIT) {
            undo.removeLast();
        }
        redo.clear();
    }

    private void restore(Deque<Snapshot> from, Deque<Snapshot> to) {
        if (from.isEmpty()) {
            return;
        }
        to.push(new Snapshot(text, caret, anchor));
        Snapshot snapshot = from.pop();
        if (!filter.test(snapshot.text())) {
            return;
        }
        text = snapshot.text();
        caret = snapshot.caret();
        anchor = snapshot.anchor();
        if (onChange != null) {
            onChange.accept(text);
        }
    }

    private void deleteBy(int direction, boolean word) {
        if (hasSelection()) {
            replaceSelection("");
            return;
        }
        int target = word ? wordBoundary(caret, direction) : step(caret, direction);
        anchor = target;
        replaceSelection("");
    }

    private int step(int from, int direction) {
        if (direction < 0) {
            return from <= 0 ? 0 : text.offsetByCodePoints(from, -1);
        }
        return from >= text.length() ? text.length() : text.offsetByCodePoints(from, 1);
    }

    /** Next word start (forward) or previous word start (backward), skipping spaces first. */
    private int wordBoundary(int from, int direction) {
        int i = from;
        if (direction < 0) {
            while (i > 0 && text.charAt(i - 1) == ' ') {
                i--;
            }
            while (i > 0 && text.charAt(i - 1) != ' ') {
                i--;
            }
        } else {
            while (i < text.length() && text.charAt(i) != ' ') {
                i++;
            }
            while (i < text.length() && text.charAt(i) == ' ') {
                i++;
            }
        }
        return i;
    }

    private void moveCaret(int position, boolean extend) {
        caret = Math.max(0, Math.min(text.length(), position));
        if (!extend) {
            anchor = caret;
        }
        focusTime = Util.getMillis();
    }

    private void commit() {
        if (onCommit != null) {
            onCommit.accept(text);
        }
    }

    // ---- layout and painting -------------------------------------------------------------------

    @Override
    protected int contentWidth() {
        return Math.max(font.width(text), 8);
    }

    @Override
    protected int contentHeight(int contentWidth) {
        return font.lineHeight;
    }

    private int innerWidth() {
        return Math.max(1, contentBounds().width());
    }

    /** Keeps the caret inside the visible window. */
    private void updateScroll() {
        scroll = Math.min(scroll, text.length());
        if (caret < scroll) {
            scroll = caret;
        }
        int width = innerWidth();
        while (scroll < caret && font.width(text.substring(scroll, caret)) > width) {
            scroll = step(scroll, 1);
        }
    }

    @Override
    protected void paint(Canvas canvas, Frame frame) {
        (isFocused() ? focusedBackground : background).paint(canvas, bounds());
        updateScroll();
        Rect box = contentBounds();
        int color = isEnabled() && editable ? textColor : disabledColor;
        String visible = font.plainSubstrByWidth(text.substring(scroll), box.width());
        int textY = box.y() + (box.height() - font.lineHeight) / 2;

        canvas.pushClip(box);
        if (visible.isEmpty() && !isFocused() && hint != null) {
            canvas.text(hint, box.x(), textY, hintColor, shadow);
        } else {
            canvas.text(visible, box.x(), textY, color, shadow);
        }

        int caretX = box.x() + font.width(text.substring(scroll, Math.min(text.length(), Math.max(scroll, caret))));
        if (hasSelection()) {
            int start = Math.max(scroll, selectionStart());
            int end = Math.min(scroll + visible.length(), selectionEnd());
            if (end > start) {
                int x0 = box.x() + font.width(text.substring(scroll, start));
                int x1 = box.x() + font.width(text.substring(scroll, end));
                canvas.graphics().textHighlight(x0, textY - 1, x1, textY + 1 + font.lineHeight, true);
            }
        }
        boolean blinkOn = (Util.getMillis() - focusTime) / 300L % 2L == 0L;
        if (isFocused() && blinkOn && caret >= scroll) {
            canvas.fill(Rect.of(caretX, textY - 1, 1, font.lineHeight + 2), color);
        }
        canvas.popClip();
    }

    // ---- input --------------------------------------------------------------------------------------

    private int indexAt(double mouseX) {
        Rect box = contentBounds();
        String visible = font.plainSubstrByWidth(text.substring(Math.min(scroll, text.length())), box.width());
        int offset = (int) Math.round(mouseX - box.x());
        String fitting = font.plainSubstrByWidth(visible, Math.max(0, offset));
        int index = scroll + fitting.length();
        // Snap to the nearer side of the character under the pointer.
        if (index < text.length()) {
            int next = step(index, 1);
            int left = font.width(text.substring(scroll, index));
            int right = font.width(text.substring(scroll, next));
            if (offset - left > right - offset) {
                index = next;
            }
        }
        return index;
    }

    @Override
    protected void onPointer(PointerEvent event) {
        switch (event.type()) {
            case PRESS -> {
                if (!event.isPrimary()) {
                    return;
                }
                int index = indexAt(event.x());
                if (event.isDoubleClick()) {
                    anchor = wordBoundary(step(index, 1), -1);
                    int end = index;
                    while (end < text.length() && text.charAt(end) != ' ') {
                        end++;
                    }
                    caret = end;
                } else {
                    moveCaret(index, event.modifiers().shift());
                }
                dragging = true;
                event.consume();
            }
            case DRAG -> {
                if (dragging) {
                    moveCaret(indexAt(event.x()), true);
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
        focusTime = Util.getMillis();
        if (!focused) {
            anchor = caret;
            commit();
        }
    }

    @Override
    protected void onText(TextEvent event) {
        if (!isFocused() || !editable) {
            return;
        }
        if (StringUtil.isAllowedChatCharacter(event.codepoint())) {
            replaceSelection(event.text());
        }
        event.consume();
    }

    @Override
    protected void onKey(KeyEvent event) {
        if (event.type() != KeyEvent.Type.PRESS || !isFocused()) {
            return;
        }
        var key = event.vanilla();
        boolean shift = key.hasShiftDown();
        boolean word = key.hasControlDownWithQuirk();
        boolean handled = true;
        if (key.isSelectAll()) {
            selectAll();
        } else if (key.isCopy()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(selectionStart(), selectionEnd()));
        } else if (key.isCut()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(selectionStart(), selectionEnd()));
            replaceSelection("");
        } else if (key.isPaste()) {
            replaceSelection(Minecraft.getInstance().keyboardHandler.getClipboard());
        } else if (word && event.key() == GLFW.GLFW_KEY_Z) {
            restore(shift ? redo : undo, shift ? undo : redo);
        } else if (word && event.key() == GLFW.GLFW_KEY_Y) {
            restore(redo, undo);
        } else {
            switch (event.key()) {
                case GLFW.GLFW_KEY_BACKSPACE -> deleteBy(-1, word);
                case GLFW.GLFW_KEY_DELETE -> deleteBy(1, word);
                case GLFW.GLFW_KEY_LEFT -> moveCaret(!shift && hasSelection() && !word
                    ? selectionStart() : word ? wordBoundary(caret, -1) : step(caret, -1), shift);
                case GLFW.GLFW_KEY_RIGHT -> moveCaret(!shift && hasSelection() && !word
                    ? selectionEnd() : word ? wordBoundary(caret, 1) : step(caret, 1), shift);
                case GLFW.GLFW_KEY_HOME -> moveCaret(0, shift);
                case GLFW.GLFW_KEY_END -> moveCaret(text.length(), shift);
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commit();
                // Escape and Tab stay unhandled: the root uses them for overlays, focus and closing.
                default -> handled = false;
            }
        }
        if (handled) {
            focusTime = Util.getMillis();
            event.consume();
        }
    }
}
