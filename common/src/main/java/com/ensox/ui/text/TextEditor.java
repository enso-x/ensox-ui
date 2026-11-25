package com.ensox.ui.text;

import com.ensox.ui.event.KeyEvent;
import com.ensox.ui.event.TextEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StringUtil;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Editing state of one line of text, without layout or painting: caret,
 * selection, undo/redo and the keyboard behaviour of a text field.
 *
 * <p>{@link com.ensox.ui.widget.TextField} is built on it, and so is any widget
 * that draws editable text itself (inline names on a zoomable canvas, say), so
 * every text input in the UI edits the same way. {@link TextLineView} paints it.</p>
 *
 * <ul>
 *     <li>{@link #filter(Predicate)} validates every edit as a whole string;
 *     rejected edits leave the text unchanged.</li>
 *     <li>{@link #onChange(Consumer)} fires on every accepted edit, not on
 *     {@link #setText} without notification.</li>
 * </ul>
 *
 * Positions are UTF-16 indices that never split a surrogate pair.
 */
public final class TextEditor {
    private static final int UNDO_LIMIT = 100;

    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();

    private String text = "";
    private int caret;
    private int anchor;
    private int maxLength = 256;
    private Predicate<String> filter = value -> true;
    private boolean editable = true;
    private @Nullable Consumer<String> onChange;

    private record Snapshot(String text, int caret, int anchor) {
    }

    // ---- configuration --------------------------------------------------------------------

    public String text() {
        return text;
    }

    public int caret() {
        return caret;
    }

    public int maxLength() {
        return maxLength;
    }

    public TextEditor maxLength(int maxLength) {
        this.maxLength = maxLength;
        if (text.length() > maxLength) {
            setText(text.substring(0, maxLength), false);
        }
        return this;
    }

    public TextEditor filter(Predicate<String> filter) {
        this.filter = filter;
        return this;
    }

    public boolean editable() {
        return editable;
    }

    public TextEditor editable(boolean editable) {
        this.editable = editable;
        return this;
    }

    public TextEditor onChange(@Nullable Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    /** Replaces the text (clipped to the max length), keeping caret and selection inside it. */
    public void setText(String value, boolean notify) {
        text = value.length() > maxLength ? value.substring(0, maxLength) : value;
        caret = Math.min(caret, text.length());
        anchor = Math.min(anchor, text.length());
        if (notify && onChange != null) {
            onChange.accept(text);
        }
    }

    /** Starts a new editing session: the text, all of it selected, and no undo history. */
    public void begin(String value) {
        setText(value, false);
        undo.clear();
        redo.clear();
        selectAll();
    }

    // ---- selection ------------------------------------------------------------------------

    public boolean hasSelection() {
        return caret != anchor;
    }

    public int selectionStart() {
        return Math.min(caret, anchor);
    }

    public int selectionEnd() {
        return Math.max(caret, anchor);
    }

    public String selectedText() {
        return text.substring(selectionStart(), selectionEnd());
    }

    public void selectAll() {
        anchor = 0;
        caret = text.length();
    }

    /** Collapses the selection onto the caret. */
    public void deselect() {
        anchor = caret;
    }

    public void moveCaret(int position, boolean extend) {
        caret = Math.max(0, Math.min(text.length(), position));
        if (!extend) {
            anchor = caret;
        }
    }

    /** Selects the word around {@code index} (double click). */
    public void selectWordAt(int index) {
        anchor = wordBoundary(step(index, 1), -1);
        int end = index;
        while (end < text.length() && text.charAt(end) != ' ') {
            end++;
        }
        caret = end;
    }

    // ---- editing --------------------------------------------------------------------------

    /** Replaces the selection with {@code insert}; returns false if nothing changed or the filter rejected it. */
    public boolean replaceSelection(String insert) {
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

    /** Deletes the selection, or one character (or word) in {@code direction}. */
    public void deleteBy(int direction, boolean word) {
        if (!hasSelection()) {
            anchor = word ? wordBoundary(caret, direction) : step(caret, direction);
        }
        replaceSelection("");
    }

    public void undo() {
        restore(undo, redo);
    }

    public void redo() {
        restore(redo, undo);
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

    // ---- navigation -----------------------------------------------------------------------

    /** One code point from {@code from} in {@code direction}, clamped to the text. */
    public int step(int from, int direction) {
        if (direction < 0) {
            return from <= 0 ? 0 : text.offsetByCodePoints(from, -1);
        }
        return from >= text.length() ? text.length() : text.offsetByCodePoints(from, 1);
    }

    /** Next word start (forward) or previous word start (backward), skipping spaces first. */
    public int wordBoundary(int from, int direction) {
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

    // ---- input --------------------------------------------------------------------------------

    /** Types a character; returns whether the event belongs to the text (consume it then). */
    public boolean type(TextEvent event) {
        if (!editable) {
            return false;
        }
        if (StringUtil.isAllowedChatCharacter(event.codepoint())) {
            replaceSelection(event.text());
        }
        return true;
    }

    /**
     * Handles a key press: clipboard, select all, undo/redo, deletion and caret
     * movement. Enter, Escape and Tab stay unhandled: the owner decides whether
     * they commit, cancel, move focus or close.
     *
     * @return whether the key was handled (consume it then)
     */
    public boolean press(KeyEvent event) {
        var key = event.vanilla();
        boolean shift = key.hasShiftDown();
        boolean word = key.hasControlDownWithQuirk();
        if (key.isSelectAll()) {
            selectAll();
        } else if (key.isCopy()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
        } else if (key.isCut()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
            replaceSelection("");
        } else if (key.isPaste()) {
            replaceSelection(Minecraft.getInstance().keyboardHandler.getClipboard());
        } else if (word && event.key() == InputConstants.KEY_Z) {
            if (shift) {
                redo();
            } else {
                undo();
            }
        } else if (word && event.key() == InputConstants.KEY_Y) {
            redo();
        } else {
            switch (event.key()) {
                case InputConstants.KEY_BACKSPACE -> deleteBy(-1, word);
                case InputConstants.KEY_DELETE -> deleteBy(1, word);
                case InputConstants.KEY_LEFT -> moveCaret(!shift && hasSelection() && !word
                    ? selectionStart() : word ? wordBoundary(caret, -1) : step(caret, -1), shift);
                case InputConstants.KEY_RIGHT -> moveCaret(!shift && hasSelection() && !word
                    ? selectionEnd() : word ? wordBoundary(caret, 1) : step(caret, 1), shift);
                case InputConstants.KEY_HOME -> moveCaret(0, shift);
                case InputConstants.KEY_END -> moveCaret(text.length(), shift);
                default -> {
                    return false;
                }
            }
        }
        return true;
    }
}
