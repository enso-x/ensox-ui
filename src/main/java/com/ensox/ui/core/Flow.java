package com.ensox.ui.core;

import com.ensox.base.Insets;
import com.ensox.ui.layout.Position;
import com.ensox.ui.layout.Sizing;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays children out one after another along an axis ({@link Row},
 * {@link Column}).
 *
 * <p>Algorithm (sizes match the previous Elx flow layout):</p>
 * <ol>
 *     <li>sizes that do not depend on content are resolved first;</li>
 *     <li>children are measured in the child space (own size minus padding,
 *     or the offered space on content-sized axes);</li>
 *     <li>children growing on the main axis are measured last: the space left
 *     by the other flow children and gaps is split between them by weight
 *     ({@code grow()} = 100, {@code grow(n)} = n), like CSS {@code flex-grow};
 *     on the cross axis {@code grow} fills the cross space;</li>
 *     <li>content-sized axes take the run length / the largest child;</li>
 *     <li>arrange places flow children with the gap, then applies
 *     {@code align}; out-of-flow children are placed relative to the content box.</li>
 * </ol>
 */
public class Flow extends Container {
    public enum Axis {
        HORIZONTAL,
        VERTICAL
    }

    private final Axis axis;
    private int contentMain;
    private int contentCross;

    protected Flow(Axis axis) {
        this.axis = axis;
    }

    public Axis axis() {
        return axis;
    }

    @Override
    public Flow add(Widget... widgets) {
        super.add(widgets);
        return this;
    }

    @Override
    public Flow addAll(List<? extends Widget> widgets) {
        super.addAll(widgets);
        return this;
    }

    @Override
    protected void measureSelf(int availableWidth, int availableHeight, int spaceWidth, int spaceHeight) {
        Style style = style();
        Insets padding = style.padding();
        boolean contentWidth = style.width().isContent();
        boolean contentHeight = style.height().isContent();

        if (!contentWidth) {
            setWidth(style.width().resolve(availableWidth, () -> 0));
        }
        if (!contentHeight) {
            setHeight(style.height().resolve(availableHeight, () -> 0));
        }

        int childWidth = childSpace(contentWidth, spaceWidth, width(), padding.horizontal());
        int childHeight = childSpace(contentHeight, spaceHeight, height(), padding.vertical());
        measureChildren(childWidth, childHeight);

        int contentW = horizontal() ? contentMain : contentCross;
        int contentH = horizontal() ? contentCross : contentMain;
        if (contentWidth) {
            setWidth(style.width().resolve(availableWidth, () -> contentW + padding.horizontal()));
        }
        if (contentHeight) {
            setHeight(style.height().resolve(availableHeight, () -> contentH + padding.vertical()));
        }
    }

    private boolean horizontal() {
        return axis == Axis.HORIZONTAL;
    }

    private Sizing mainSizing(Widget child) {
        return horizontal() ? child.style().width() : child.style().height();
    }

    /**
     * Splits {@code remaining} between growing children by weight, like CSS
     * {@code flex-grow}. Leftover pixels go to the first children, so the
     * shares always add up to the remainder exactly.
     */
    private int[] distribute(int remaining, List<Widget> growing) {
        int[] shares = new int[growing.size()];
        long totalWeight = 0;
        for (Widget child : growing) {
            totalWeight += Math.max(0, mainSizing(child).weight());
        }
        if (totalWeight == 0) {
            return shares;
        }
        int assigned = 0;
        for (int i = 0; i < shares.length; i++) {
            shares[i] = (int) (remaining * (long) Math.max(0, mainSizing(growing.get(i)).weight()) / totalWeight);
            assigned += shares[i];
        }
        for (int i = 0; assigned < remaining && i < shares.length; i++) {
            if (mainSizing(growing.get(i)).weight() > 0) {
                shares[i]++;
                assigned++;
            }
        }
        return shares;
    }

    private void measureChildren(int childWidth, int childHeight) {
        List<Widget> children = visibleChildren();
        List<Widget> growing = new ArrayList<>();
        int flowCount = 0;
        int fixedMain = 0;

        for (Widget child : children) {
            boolean inFlow = child.style().position().inFlow();
            if (inFlow) {
                flowCount++;
            }
            // Only main-axis growth waits for the remainder; cross-axis grow simply fills the cross space.
            if (inFlow && mainSizing(child).isGrow()) {
                growing.add(child);
                continue;
            }
            child.measure(childWidth, childHeight);
            if (inFlow) {
                fixedMain += horizontal() ? child.outerWidth() : child.outerHeight();
            }
        }

        if (!growing.isEmpty()) {
            int gaps = style().gap() * Math.max(flowCount - 1, 0);
            int remaining = Math.max(0, (horizontal() ? childWidth : childHeight) - fixedMain - gaps);
            int[] shares = distribute(remaining, growing);
            for (int i = 0; i < growing.size(); i++) {
                // The share is the child's outer size (margins included); grow takes all of it.
                if (horizontal()) {
                    growing.get(i).measure(shares[i], childHeight);
                } else {
                    growing.get(i).measure(childWidth, shares[i]);
                }
            }
        }

        int main = 0;
        int cross = 0;
        int counted = 0;
        for (Widget child : children) {
            if (!child.style().position().inFlow()) {
                continue;
            }
            main += horizontal() ? child.outerWidth() : child.outerHeight();
            cross = Math.max(cross, horizontal() ? child.outerHeight() : child.outerWidth());
            counted++;
        }
        if (counted > 0) {
            main += style().gap() * (counted - 1);
        }
        contentMain = main;
        contentCross = cross;
    }

    @Override
    protected void arrangeChildren() {
        Insets padding = style().padding();
        int innerWidth = width() - padding.horizontal();
        int innerHeight = height() - padding.vertical();
        int mainShift = horizontal()
            ? style().alignX().offset(contentMain, innerWidth)
            : style().alignY().offset(contentMain, innerHeight);

        int cursor = 0;
        for (Widget child : visibleChildren()) {
            Position position = child.style().position();
            if (!position.inFlow()) {
                continue;
            }
            Insets margin = child.style().margin();
            int childX = x() + padding.left() + margin.left();
            int childY = y() + padding.top() + margin.top();
            if (horizontal()) {
                childX += cursor + mainShift;
                childY += style().alignY().offset(child.outerHeight(), innerHeight);
                cursor += child.outerWidth() + style().gap();
            } else {
                childY += cursor + mainShift;
                childX += style().alignX().offset(child.outerWidth(), innerWidth);
                cursor += child.outerHeight() + style().gap();
            }
            if (position.kind() == Position.Kind.OFFSET) {
                childX += position.x();
                childY += position.y();
            }
            child.arrange(childX, childY);
        }

        for (Widget child : visibleChildren()) {
            if (!child.style().position().inFlow()) {
                arrangeOutOfFlow(child);
            }
        }
    }
}
