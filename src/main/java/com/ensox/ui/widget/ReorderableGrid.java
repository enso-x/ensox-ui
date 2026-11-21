package com.ensox.ui.widget;

import com.ensox.ui.core.Container;
import com.ensox.ui.core.Widget;
import com.ensox.ui.event.KeyEvent;
import com.ensox.ui.event.PointerEvent;
import com.ensox.ui.event.UiEvent;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.layout.Sizing;
import com.ensox.ui.paint.Canvas;
import com.mojang.blaze3d.platform.InputConstants;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Virtualized card grid with a scroll viewport and provisional drag ordering.
 * Items are immutable snapshots addressed by stable keys. Interactive descendants
 * keep their own pointer gestures; only an unconsumed card press starts a drag.
 */
public final class ReorderableGrid<T> extends ScrollView {
    @FunctionalInterface public interface DropListener {
        void moved(String movedId, String anchorId, boolean after);
    }

    private final Surface<T> surface;

    public ReorderableGrid(Function<T, String> key, Function<T, Widget> factory) {
        this(new Surface<>(key, factory));
    }

    private ReorderableGrid(Surface<T> surface) {
        super(surface, false, true);
        this.surface = surface;
        surface.viewport = this;
        style().fill();
    }

    public ReorderableGrid<T> items(List<T> items) { surface.items(items); return this; }
    public ReorderableGrid<T> cellSize(int minimumWidth, int height) {
        if (minimumWidth < 1 || height < 1) throw new IllegalArgumentException("Positive card dimensions required");
        surface.minimumWidth = minimumWidth; surface.cellHeight = height; invalidateLayout(); return this;
    }
    public ReorderableGrid<T> gap(int gap) {
        if (gap < 0) throw new IllegalArgumentException("Negative gap");
        surface.gap = gap; invalidateLayout(); return this;
    }
    public ReorderableGrid<T> accentColor(int argb) { surface.accent = argb; return this; }
    public ReorderableGrid<T> onSelect(Consumer<String> listener) { surface.onSelect = listener; return this; }
    public ReorderableGrid<T> onMove(DropListener listener) { surface.onMove = listener; return this; }
    /** Selection/virtualization without drag ordering, for catalogs with no authored order. */
    public ReorderableGrid<T> reorderable(boolean enabled) {
        surface.cancelDrag(); surface.reorderable = enabled; return this;
    }
    public ReorderableGrid<T> selected(@Nullable String id) { surface.selected = id; return this; }
    public @Nullable String selected() { return surface.selected; }
    public boolean dragging() { return surface.drag != null && surface.drag.active(); }
    public void cancelDrag() { surface.cancelDrag(); }

    /** Independent scroll bars are still usable; content dragging belongs to the cards. */
    @Override public ReorderableGrid<T> dragToScroll(boolean enabled) {
        if (enabled) throw new IllegalArgumentException("Card dragging owns this grid's content gesture");
        return this;
    }

    @Override protected void onDetach() { cancelDrag(); super.onDetach(); }

    private static final class Surface<T> extends Container {
        private final Function<T, String> key;
        private final Function<T, Widget> factory;
        private final Map<String, Mounted<T>> mounted = new LinkedHashMap<>();
        private List<T> items = List.of();
        private List<String> ids = List.of();
        private Map<String, Integer> indices = Map.of();
        private ReorderableGrid<T> viewport;
        private int minimumWidth = 280, cellHeight = 160, gap = 8, accent = 0xFFB0DAFF;
        private ReorderGridLayout layout = ReorderGridLayout.create(0, 280, 160, 8, 0);
        private @Nullable Rect arrangedViewport;
        private int arrangedCellWidth;
        private @Nullable String selected;
        private @Nullable ReorderGridDrag drag;
        private boolean reorderable = true;
        private double pointerX, pointerY, grabX, grabY;
        private @Nullable Widget previousFocus;
        private Consumer<String> onSelect = ignored -> {};
        private DropListener onMove = (moved, anchor, after) -> {};

        private Surface(Function<T, String> key, Function<T, Widget> factory) {
            this.key = key; this.factory = factory;
            style().size(Sizing.fill(), Sizing.content());
            focusable(true);
        }

        private void items(List<T> next) {
            List<T> snapshot = List.copyOf(next);
            List<String> keys = snapshot.stream().map(key).map(Objects::requireNonNull).toList();
            Map<String, Integer> positions = new HashMap<>();
            for (int i = 0; i < keys.size(); i++) {
                if (positions.put(keys.get(i), i) != null) throw new IllegalArgumentException("Duplicate grid key: " + keys.get(i));
            }
            cancelDrag();
            items = snapshot; ids = keys; indices = positions;
            if (selected != null && !positions.containsKey(selected)) selected = null;
            // Updating snapshots keeps widgets and their keyboard focus when their data is unchanged.
            for (var entry : List.copyOf(mounted.entrySet())) {
                Integer index = indices.get(entry.getKey());
                if (index == null) unmount(entry.getKey());
                else if (!Objects.equals(entry.getValue().item, items.get(index))) replace(entry.getKey(), items.get(index));
            }
            invalidateLayout();
        }

        @Override protected void measureSelf(int aw, int ah, int sw, int sh) {
            setWidth(Math.max(0, aw));
            layout = ReorderGridLayout.create(width(), minimumWidth, cellHeight, gap, items.size());
            setHeight(layout.contentHeight());
        }

        @Override protected void arrangeChildren() {
            Rect view = viewport.contentBounds();
            // ScrollView probes full width before reserving its scrollbar on every
            // measurement. Only the final arranged geometry can invalidate a drag.
            if (arrangedViewport != null && (!arrangedViewport.equals(view) || arrangedCellWidth != layout.cellWidth())) cancelDrag();
            arrangedViewport = view;
            arrangedCellWidth = layout.cellWidth();
            syncMounted();
            place(0);
        }

        private void syncMounted() {
            Rect view = viewport.contentBounds();
            var visible = layout.visible(Math.max(0, view.y() - y()), view.height());
            Set<String> wanted = new LinkedHashSet<>();
            for (int slot = visible.start(); slot < visible.end(); slot++) {
                wanted.add(ids.get(drag == null ? slot : drag.sourceIndex(slot)));
            }
            String focused = cardId(root() == null ? null : root().focus().focused());
            if (focused != null) wanted.add(focused);
            if (drag != null) wanted.add(drag.moved());
            for (String id : List.copyOf(mounted.keySet())) if (!wanted.contains(id)) unmount(id);
            for (String id : wanted) {
                if (!mounted.containsKey(id)) {
                    T item = items.get(indices.get(id));
                    Mounted<T> cell = new Mounted<>(item, factory.apply(item));
                    mounted.put(id, cell);
                    add(cell.widget);
                }
                Mounted<T> cell = mounted.get(id);
                if (cell.measuredWidth != layout.cellWidth() || cell.measuredHeight != cellHeight) {
                    cell.widget.style().size(layout.cellWidth(), cellHeight);
                    cell.widget.measure(layout.cellWidth(), cellHeight);
                    cell.measuredWidth = layout.cellWidth(); cell.measuredHeight = cellHeight;
                }
            }
        }

        private void unmount(String id) {
            Mounted<T> removed = mounted.remove(id);
            if (removed != null) remove(removed.widget);
        }

        private void replace(String id, T item) {
            Mounted<T> old = mounted.get(id);
            Widget focused = root() == null ? null : root().focus().focused();
            List<Integer> path = focusPath(old.widget, focused);
            boolean keyboard = root() != null && root().focus().isFocusVisible();
            remove(old.widget);
            Mounted<T> replacement = new Mounted<>(item, factory.apply(item));
            replacement.x = old.x; replacement.y = old.y;
            mounted.put(id, replacement);
            add(replacement.widget);
            if (path != null && root() != null) {
                Widget target = replacement.widget;
                for (int child : path) {
                    if (child >= target.children().size()) { target = this; break; }
                    target = target.children().get(child);
                }
                root().focus().focus(target.isFocusable() ? target : this, keyboard);
            }
        }

        private static @Nullable List<Integer> focusPath(Widget card, @Nullable Widget focus) {
            if (focus == null) return null;
            List<Integer> reversed = new ArrayList<>();
            for (Widget cursor = focus; cursor != card; cursor = cursor.parent()) {
                if (cursor.parent() == null) return null;
                reversed.add(cursor.parent().children().indexOf(cursor));
            }
            return reversed.reversed();
        }

        private @Nullable String cardId(@Nullable Widget widget) {
            while (widget != null && widget.parent() != this) widget = widget.parent();
            if (widget == null) return null;
            for (var entry : mounted.entrySet()) if (entry.getValue().widget == widget) return entry.getKey();
            return null;
        }

        private void place(double deltaSeconds) {
            double alpha = 1 - Math.exp(-16 * Math.max(0, deltaSeconds));
            for (var entry : mounted.entrySet()) {
                Mounted<T> cell = entry.getValue();
                int index = indices.get(entry.getKey());
                Rect target = layout.slot(drag == null ? index : drag.visualIndex(index));
                if (Double.isNaN(cell.x)) { cell.x = target.x(); cell.y = target.y(); }
                if (drag != null && drag.active() && drag.moved().equals(entry.getKey())) {
                    cell.x = pointerX - grabX - x(); cell.y = pointerY - grabY - y();
                } else {
                    cell.x = approach(cell.x, target.x(), alpha);
                    cell.y = approach(cell.y, target.y(), alpha);
                }
                cell.widget.arrange(x() + (int) Math.round(cell.x), y() + (int) Math.round(cell.y));
            }
        }

        private static double approach(double value, double target, double alpha) {
            double next = value + (target - value) * alpha;
            return Math.abs(target - next) < .25 ? target : next;
        }

        @Override protected void paint(Canvas canvas, Frame frame) {
            autoScroll(frame.deltaSeconds());
            syncMounted();
            place(frame.deltaSeconds());
            if (drag != null && drag.active()) {
                Rect slot = layout.slot(drag.destination()).offset(x(), y());
                canvas.fill(slot, 0x30000000);
                canvas.outline(slot, accent);
            }
            Widget focused = root() == null ? null : root().focus().focused();
            if (focused != previousFocus) {
                String id = cardId(focused);
                if (id != null) reveal(id);
                previousFocus = focused;
            }
        }

        private void autoScroll(float deltaSeconds) {
            if (drag == null || !drag.active()) return;
            Rect view = dropViewport();
            if (pointerX < view.x() || pointerX >= view.right()) return;
            double edge = Math.min(32, view.height() / 3.0);
            double speed = pointerY < view.y() + edge ? -Math.clamp((view.y() + edge - pointerY) / Math.max(1, edge), 0, 1)
                : pointerY > view.bottom() - edge ? Math.clamp((pointerY - view.bottom() + edge) / Math.max(1, edge), 0, 1) : 0;
            viewport.scrollBy(0, speed * 360 * Math.min(.1, Math.max(0, deltaSeconds)), false);
            updateDrag();
        }

        @Override protected void renderChildren(Canvas canvas, Frame frame) {
            for (var entry : mounted.entrySet()) {
                if (drag != null && drag.active() && drag.moved().equals(entry.getKey())) continue;
                renderCell(canvas, frame, entry.getKey(), entry.getValue());
            }
            if (drag != null && drag.active()) {
                // Vanilla batches equal pipelines; a new stratum guarantees the floating card stays on top.
                canvas.graphics().nextStratum();
                renderCell(canvas, frame, drag.moved(), mounted.get(drag.moved()));
            }
        }

        private void renderCell(Canvas canvas, Frame frame, String id, Mounted<T> cell) {
            cell.widget.render(canvas, frame);
            if (id.equals(selected)) canvas.outline(cell.widget.bounds(), accent);
        }

        @Override public @Nullable Widget hitTest(double px, double py) {
            if (!bounds().contains(px, py)) return null;
            if (drag != null && drag.active()) return this;
            return super.hitTest(px, py);
        }

        @Override protected void onPointer(PointerEvent event) {
            switch (event.type()) {
                case PRESS -> {
                    if (!event.isPrimary()) return;
                    String id = cardId(event.target());
                    if (id == null) return;
                    // Disabled controls do not consume events, but are still not a drag handle.
                    for (Widget cursor = event.target(); cursor != this; cursor = cursor.parent()) {
                        if (cursor.isFocusable()) return;
                    }
                    Mounted<T> cell = mounted.get(id);
                    pointerX = event.x(); pointerY = event.y();
                    grabX = pointerX - cell.widget.x(); grabY = pointerY - cell.widget.y();
                    drag = new ReorderGridDrag(ids, id, pointerX, pointerY);
                    requestFocus();
                    event.consume();
                }
                case DRAG -> {
                    if (drag == null) return;
                    pointerX = event.x(); pointerY = event.y(); updateDrag(); event.consume();
                }
                case RELEASE -> {
                    if (drag == null) return;
                    ReorderGridDrag completed = drag;
                    pointerX = event.x(); pointerY = event.y();
                    boolean inside = dropViewport().contains(pointerX, pointerY);
                    if (completed.active()) updateDrag();
                    cancelDrag();
                    event.consume();
                    if (!inside) return;
                    if (completed.active()) completed.drop().ifPresent(move -> onMove.moved(move.moved(), move.anchor(), move.after()));
                    else { selected = completed.moved(); onSelect.accept(selected); }
                }
                case CANCEL -> cancelDrag();
                default -> { }
            }
        }

        private void updateDrag() {
            if (drag != null) {
                drag.update(pointerX, pointerY, layout.nearestIndex(pointerX - x(), pointerY - y()));
                // Crossing the click threshold cancels selection when this catalog cannot reorder.
                if (!reorderable && drag.active()) cancelDrag();
            }
        }

        private void cancelDrag() { drag = null; }

        private Rect dropViewport() {
            Rect view = viewport.contentBounds();
            return Rect.of(x(), view.y(), width(), view.height());
        }

        @Override protected void intercept(UiEvent event) {
            if (drag != null && drag.active() && event instanceof KeyEvent key) {
                if (key.type() == KeyEvent.Type.PRESS && key.key() == InputConstants.KEY_ESCAPE) cancelDrag();
                // Tab/Enter must not activate controls on a card while it is floating.
                event.consume();
            }
        }

        @Override protected void onKey(KeyEvent event) {
            if (event.type() != KeyEvent.Type.PRESS) return;
            if (event.key() == InputConstants.KEY_ESCAPE && drag != null) {
                cancelDrag(); event.consume(); return;
            }
            if (!isFocused() || ids.isEmpty()) return;
            int index = selected == null ? 0 : indices.getOrDefault(selected, 0);
            int next = switch (event.key()) {
                case InputConstants.KEY_LEFT -> index - 1;
                case InputConstants.KEY_RIGHT -> index + 1;
                case InputConstants.KEY_UP -> index - layout.columns();
                case InputConstants.KEY_DOWN -> index + layout.columns();
                case InputConstants.KEY_HOME -> 0;
                case InputConstants.KEY_END -> ids.size() - 1;
                default -> -1;
            };
            if (next == -1 && event.key() != InputConstants.KEY_LEFT && event.key() != InputConstants.KEY_UP) return;
            selected = ids.get(Math.clamp(next, 0, ids.size() - 1));
            reveal(selected); onSelect.accept(selected); event.consume();
        }

        private void reveal(String id) {
            Rect slot = layout.slot(indices.get(id));
            double offset = viewport.scrollY();
            if (slot.y() < offset) viewport.scrollTo(0, slot.y(), false);
            else if (slot.bottom() > offset + viewport.contentBounds().height())
                viewport.scrollTo(0, slot.bottom() - viewport.contentBounds().height(), false);
        }
    }

    private static final class Mounted<T> {
        private final T item;
        private final Widget widget;
        private int measuredWidth = -1, measuredHeight = -1;
        private double x = Double.NaN, y;
        private Mounted(T item, Widget widget) { this.item = item; this.widget = widget; }
    }
}
