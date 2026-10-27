package com.ensox.ui.event;

/** A typed character (full Unicode code point, including characters outside the BMP). */
public final class TextEvent extends UiEvent {
    private final int codepoint;
    private final Modifiers modifiers;

    public TextEvent(int codepoint, Modifiers modifiers) {
        this.codepoint = codepoint;
        this.modifiers = modifiers;
    }

    public int codepoint() {
        return codepoint;
    }

    public String text() {
        return Character.toString(codepoint);
    }

    public Modifiers modifiers() {
        return modifiers;
    }
}
