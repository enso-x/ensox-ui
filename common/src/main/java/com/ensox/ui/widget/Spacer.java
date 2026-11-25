package com.ensox.ui.widget;

import com.ensox.ui.core.Widget;
import com.ensox.ui.layout.Sizing;

/** Empty widget; by default it grows to push its siblings apart in a row. */
public class Spacer extends Widget {
    public Spacer() {
        style().size(Sizing.grow(), Sizing.fixed(1));
    }

    public static Spacer fixed(int width, int height) {
        Spacer spacer = new Spacer();
        spacer.style().size(width, height);
        return spacer;
    }
}
