package com.ensox.ui.widget;

import com.ensox.base.ColorX;
import com.ensox.ui.core.Widget;
import com.ensox.ui.layout.Align;
import com.ensox.ui.layout.Sizing;
import com.ensox.ui.paint.Canvas;
import com.ensox.ui.state.Signal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Text. Content-sized labels are one line (wrapped at {@link #maxLineWidth});
 * labels with a fixed or fill width wrap to that width.
 *
 * <pre>
 * Label.of(TITLE).shadow(true).textAlign(Align.CENTER, Align.CENTER)
 * Label.of(countSignal.map(n -&gt; Component.literal(n + " items")))
 * </pre>
 *
 * <p>Labels never consume pointer input, so clicks reach the widget around them.</p>
 */
public class Label extends Widget {
    private final Font font = Minecraft.getInstance().font;
    private Component text;
    private List<FormattedCharSequence> lines = List.of();
    private int wrappedForWidth = -1;
    private ColorX color = ColorX.WHITE;
    private boolean shadow;
    private Align textAlignX = Align.START;
    private Align textAlignY = Align.START;
    private int lineHeight = font.lineHeight;
    private int lineSpacing = 2;
    private int maxLineWidth = Integer.MAX_VALUE;

    public Label(Component text) {
        this.text = text;
    }

    public static Label of(Component text) {
        return new Label(text);
    }

    public static Label of(Signal<Component> text) {
        Label label = new Label(text.get());
        label.bind(text, label::text);
        return label;
    }

    public Component text() {
        return text;
    }

    public Label text(Component text) {
        if (!this.text.equals(text)) {
            this.text = text;
            this.wrappedForWidth = -1;
            invalidateLayout();
        }
        return this;
    }

    public Label color(ColorX color) {
        this.color = color;
        return this;
    }

    public Label shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    /** Alignment of the text inside the label box. */
    public Label textAlign(Align x, Align y) {
        this.textAlignX = x;
        this.textAlignY = y;
        return this;
    }

    public Label lineHeight(int lineHeight) {
        this.lineHeight = lineHeight;
        invalidateLayout();
        return this;
    }

    public Label lineSpacing(int lineSpacing) {
        this.lineSpacing = lineSpacing;
        invalidateLayout();
        return this;
    }

    /** Wrap width for content-sized labels. */
    public Label maxLineWidth(int maxLineWidth) {
        this.maxLineWidth = maxLineWidth;
        this.wrappedForWidth = -1;
        invalidateLayout();
        return this;
    }

    private List<FormattedCharSequence> wrap(int width) {
        if (wrappedForWidth != width) {
            lines = font.split(text, Math.max(1, width));
            wrappedForWidth = width;
        }
        return lines;
    }

    private int wrapWidth() {
        return style().width().isContent() ? maxLineWidth : width() - style().padding().horizontal();
    }

    @Override
    protected int contentWidth() {
        int widest = 0;
        for (FormattedCharSequence line : wrap(maxLineWidth)) {
            widest = Math.max(widest, font.width(line));
        }
        return widest;
    }

    @Override
    protected int contentHeight(int contentWidth) {
        return textHeight(wrap(wrapWidth()));
    }

    private int textHeight(List<FormattedCharSequence> lines) {
        return lines.size() * (lineHeight + lineSpacing) - lineSpacing;
    }

    @Override
    protected void paint(Canvas canvas, Frame frame) {
        List<FormattedCharSequence> lines = wrap(wrapWidth());
        var content = contentBounds();
        int x = content.x();
        int y = content.y();
        Sizing width = style().width();
        Sizing height = style().height();
        if (width.kind() == Sizing.Kind.CONTENT) {
            x += width.value();
        }
        if (height.kind() == Sizing.Kind.CONTENT) {
            y += height.value();
        }
        y += textAlignY.offset(textHeight(lines), content.height());

        // Half-pixel nudge kept from the original label renderer: aligns the font baseline on the GUI scale grid.
        canvas.pushTranslation(0, 1f / Minecraft.getInstance().getWindow().getGuiScale());
        int argb = color.toIntARGB();
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            int lineX = x + textAlignX.offset(font.width(line), content.width());
            int lineY = y + i * (lineHeight + lineSpacing) + lineHeight - font.lineHeight;
            canvas.text(line, lineX, lineY, argb, shadow);
        }
        canvas.popTransform();
    }
}
