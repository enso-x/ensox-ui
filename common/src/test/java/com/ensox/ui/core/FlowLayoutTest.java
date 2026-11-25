package com.ensox.ui.core;

import com.ensox.base.Insets;
import com.ensox.ui.layout.Align;
import com.ensox.ui.layout.Position;
import com.ensox.ui.layout.Sizing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowLayoutTest {
    /** Leaf with a fixed intrinsic size, for content sizing. */
    private static final class Box extends Widget {
        private final int contentW;
        private final int contentH;

        Box(int contentW, int contentH) {
            this.contentW = contentW;
            this.contentH = contentH;
        }

        @Override
        protected int contentWidth() {
            return contentW;
        }

        @Override
        protected int contentHeight(int contentWidth) {
            return contentH;
        }
    }

    private static UiRoot layout(Widget content, int width, int height) {
        UiRoot root = new UiRoot(content);
        root.resize(width, height);
        root.layoutIfNeeded();
        return root;
    }

    @Test
    void columnStacksChildrenWithGapAndPadding() {
        Box a = new Box(10, 5);
        Box b = new Box(20, 7);
        Column column = Column.of(a, b);
        column.style(s -> s.padding(Insets.of(2, 3)).gap(4));
        layout(column, 200, 100);

        assertEquals(20 + 6, column.width());
        assertEquals(5 + 4 + 7 + 4, column.height());
        assertEquals(3, a.x());
        assertEquals(2, a.y());
        assertEquals(2 + 5 + 4, b.y());
    }

    @Test
    void growChildTakesRemainderOfMainAxis() {
        Box fixed = new Box(30, 10);
        Box grow = new Box(0, 10);
        grow.style(s -> s.width(Sizing.grow()));
        Row row = Row.of(fixed, grow);
        row.style(s -> s.width(Sizing.fixed(100)).gap(5));
        layout(row, 300, 50);

        assertEquals(100 - 30 - 5, grow.width());
        assertEquals(35, grow.x());
    }

    @Test
    void growSiblingsShareTheRemainderEquallyWithGaps() {
        Box a = new Box(0, 10);
        Box b = new Box(0, 10);
        Box c = new Box(0, 10);
        for (Box box : List.of(a, b, c)) {
            box.style(s -> s.width(Sizing.grow()));
        }
        Row row = Row.of(a, b, c);
        row.style(s -> s.width(Sizing.fixed(244)).gap(4));
        layout(row, 300, 50);

        // 244 - 2 * 4 = 236 -> 79 + 79 + 78; leftover pixels go first.
        assertEquals(79, a.width());
        assertEquals(79, b.width());
        assertEquals(78, c.width());
        assertEquals(244, c.x() + c.width());
    }

    @Test
    void growWeightsSplitProportionally() {
        Box fixed = new Box(10, 10);
        Box two = new Box(0, 10);
        Box one = new Box(0, 10);
        two.style(s -> s.width(Sizing.grow(2)));
        one.style(s -> s.width(Sizing.grow(1)));
        Row row = Row.of(fixed, two, one);
        row.style(s -> s.width(Sizing.fixed(100)));
        layout(row, 300, 50);

        assertEquals(60, two.width());
        assertEquals(30, one.width());
    }

    @Test
    void growShareIncludesTheChildMargins() {
        Box a = new Box(0, 10);
        Box b = new Box(0, 10);
        a.style(s -> s.width(Sizing.grow()).margin(Insets.of(0, 5)));
        b.style(s -> s.width(Sizing.grow()));
        Row row = Row.of(a, b);
        row.style(s -> s.width(Sizing.fixed(100)));
        layout(row, 300, 50);

        assertEquals(40, a.width());
        assertEquals(50, b.width());
    }

    @Test
    void crossAxisGrowFillsAndDoesNotStealMainSpace() {
        Box tall = new Box(20, 0);
        tall.style(s -> s.height(Sizing.grow()));
        Box rest = new Box(0, 10);
        rest.style(s -> s.width(Sizing.grow()));
        Row row = Row.of(tall, rest);
        row.style(s -> s.size(Sizing.fixed(100), Sizing.fixed(40)));
        layout(row, 300, 300);

        assertEquals(40, tall.height());
        assertEquals(80, rest.width());
    }

    @Test
    void fillUsesPercentOfChildSpaceWithElxRounding() {
        Box half = new Box(0, 0);
        half.style(s -> s.size(Sizing.fill(33), Sizing.fill(50)));
        Column column = Column.of(half);
        column.style(s -> s.size(Sizing.fixed(101), Sizing.fixed(41)).padding(Insets.of(1)));
        layout(column, 300, 300);

        assertEquals(Math.round(0.33f * 99), half.width());
        assertEquals(Math.round(0.5f * 39), half.height());
    }

    @Test
    void centeringUsesFloorDivisionOnBothAxes() {
        Box child = new Box(5, 5);
        Row row = Row.of(child);
        row.style(s -> s.size(Sizing.fixed(10), Sizing.fixed(10)).center());
        layout(row, 100, 100);

        assertEquals(2, child.x());
        assertEquals(2, child.y());
    }

    @Test
    void oversizedChildOverflowsSymmetricallyWhenCentered() {
        Box child = new Box(13, 1);
        Row row = Row.of(child);
        row.style(s -> s.size(Sizing.fixed(10), Sizing.fixed(10)).align(Align.CENTER, Align.START));
        layout(row, 100, 100);

        assertEquals(-2, child.x());
    }

    @Test
    void anchorPlacesOutOfFlowChildWithoutTakingSpace() {
        Box flow = new Box(10, 10);
        Box anchored = new Box(4, 4);
        anchored.style(s -> s.position(Position.anchor(100, 50)));
        Row row = Row.of(flow, anchored);
        row.style(s -> s.size(Sizing.fixed(50), Sizing.fixed(20)));
        layout(row, 100, 100);

        assertEquals(46, anchored.x());
        assertEquals(8, anchored.y());
        assertEquals(0, flow.x());
    }

    @Test
    void minMaxClampContainerSize() {
        Column column = Column.of(new Box(10, 10));
        column.style(s -> s.size(Sizing.fill(), Sizing.fill()).maxSize(80, 40));
        layout(column, 200, 200);

        assertEquals(80, column.width());
        assertEquals(40, column.height());
    }

    @Test
    void invisibleChildrenTakeNoSpace() {
        Box hidden = new Box(10, 10);
        hidden.style(s -> s.visible(false));
        Box shown = new Box(10, 10);
        Column column = Column.of(hidden, shown);
        column.style(s -> s.gap(5));
        layout(column, 100, 100);

        assertEquals(10, column.height());
        assertEquals(0, shown.y());
    }

    @Test
    void styleChangeRequestsRelayout() {
        Box box = new Box(10, 10);
        Column column = Column.of(box);
        UiRoot root = layout(column, 100, 100);

        box.style().margin(Insets.of(3));
        root.layoutIfNeeded();
        assertEquals(3, box.x());
        assertEquals(16, column.height());
    }
}
