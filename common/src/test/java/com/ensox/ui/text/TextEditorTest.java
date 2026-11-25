package com.ensox.ui.text;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextEditorTest {
    @Test
    void beginSelectsEverythingSoTypingReplacesIt() {
        TextEditor editor = new TextEditor();
        editor.begin("beam");
        editor.replaceSelection("glow");
        assertEquals("glow", editor.text());
        assertEquals(4, editor.caret());
    }

    @Test
    void firstCharacterCanBeReachedAndDeleted() {
        TextEditor editor = new TextEditor();
        editor.begin("beam");
        editor.moveCaret(1, false);
        editor.deleteBy(-1, false);
        assertEquals("eam", editor.text());
        editor.moveCaret(editor.text().length(), false);
        for (int i = 0; i < 3; i++) {
            editor.deleteBy(-1, false);
        }
        assertEquals("", editor.text());
    }

    @Test
    void insertsAtTheCaretAndUndoes() {
        List<String> changes = new ArrayList<>();
        TextEditor editor = new TextEditor().onChange(changes::add);
        editor.begin("beam");
        editor.moveCaret(0, false);
        editor.replaceSelection("big ");
        assertEquals("big beam", editor.text());
        editor.undo();
        assertEquals("beam", editor.text());
        editor.redo();
        assertEquals("big beam", editor.text());
        assertEquals(List.of("big beam", "beam", "big beam"), changes);
    }

    @Test
    void filterAndMaxLengthRejectOrClip() {
        TextEditor editor = new TextEditor().maxLength(5).filter(value -> !value.contains("x"));
        editor.begin("");
        assertFalse(editor.replaceSelection("x"));
        assertTrue(editor.replaceSelection("abcdefg"));
        assertEquals("abcde", editor.text());
    }

    @Test
    void wordNavigationSkipsSpaces() {
        TextEditor editor = new TextEditor();
        editor.begin("one two  three");
        assertEquals(4, editor.wordBoundary(0, 1));
        assertEquals(9, editor.wordBoundary(4, 1));
        assertEquals(4, editor.wordBoundary(9, -1));
        editor.selectWordAt(5);
        assertEquals("two", editor.selectedText());
    }
}
