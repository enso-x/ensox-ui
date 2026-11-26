package com.ensox.ui.widget;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The rows one Select list shows. Without grouping, or while a filter is typed, every row is an option
 * (a grouped option carries its nearest category as a prefix). Grouped and unfiltered, the list is a tree:
 * one row per category, in the order of their first options, and ungrouped options in place. A category's
 * submenu lists its subcategories first, then a rule, then its own options.
 */
final class SelectRows<T> {
    sealed interface Row<T> permits Option, Group { }

    /** @param ruleAbove the first option after the subcategories of a submenu */
    record Option<T>(T value, @Nullable SelectGroup prefix, boolean ruleAbove) implements Row<T> {
        Option(T value, @Nullable SelectGroup prefix) {
            this(value, prefix, false);
        }
    }

    /** @param members every option under this category, at any depth */
    record Group<T>(SelectGroup group, List<Row<T>> children, List<T> members) implements Row<T> {
        Group {
            children = List.copyOf(children);
            members = List.copyOf(members);
        }
    }

    private final SelectOptions<T> options;
    private final @Nullable Function<T, List<SelectGroup>> paths;
    private final List<Row<T>> tree;
    /** A submenu: always its category's tree, never filtered. */
    private final boolean submenu;
    private int treeHighlight;

    SelectRows(SelectOptions<T> options, @Nullable Function<T, List<SelectGroup>> paths) {
        this(options, paths, paths == null ? List.of() : build(options.all(), paths, 0), false, options.highlighted());
    }

    /** @param selected the row holding it starts highlighted; otherwise the first row */
    private SelectRows(SelectOptions<T> options, @Nullable Function<T, List<SelectGroup>> paths, List<Row<T>> tree,
                       boolean submenu, @Nullable T selected) {
        this.options = options;
        this.paths = paths;
        this.tree = tree;
        this.submenu = submenu;
        treeHighlight = tree.isEmpty() ? -1 : 0;
        for (int i = 0; i < tree.size(); i++) {
            if (contains(tree.get(i), selected)) {
                treeHighlight = i;
                break;
            }
        }
    }

    /** The rows of {@code group}'s submenu, the current value highlighted when it is among them. */
    static <T> SelectRows<T> submenu(Group<T> group, @Nullable T selected) {
        return new SelectRows<>(new SelectOptions<>(group.members(), selected, value -> ""), null, group.children(), true, selected);
    }

    private static <T> List<Row<T>> build(List<T> values, Function<T, List<SelectGroup>> paths, int depth) {
        List<Row<T>> rows = new ArrayList<>();
        Map<SelectGroup, Integer> at = new LinkedHashMap<>();
        Map<SelectGroup, List<T>> members = new LinkedHashMap<>();
        for (T value : values) {
            List<SelectGroup> path = Objects.requireNonNullElse(paths.apply(value), List.of());
            if (path.size() <= depth) {
                rows.add(new Option<>(value, null));
                continue;
            }
            SelectGroup group = path.get(depth);
            if (!at.containsKey(group)) {
                at.put(group, rows.size());
                rows.add(null);
                members.put(group, new ArrayList<>());
            }
            members.get(group).add(value);
        }
        at.forEach((group, index) ->
            rows.set(index, new Group<>(group, build(members.get(group), paths, depth + 1), members.get(group))));
        if (depth == 0) {
            return List.copyOf(rows);
        }
        // A submenu: subcategories first, then a rule, then the category's own options.
        List<Row<T>> ordered = new ArrayList<>();
        rows.stream().filter(row -> row instanceof Group<T>).forEach(ordered::add);
        boolean afterGroups = !ordered.isEmpty();
        for (Row<T> row : rows) {
            if (row instanceof Option<T> option) {
                ordered.add(new Option<>(option.value(), null, afterGroups));
                afterGroups = false;
            }
        }
        return List.copyOf(ordered);
    }

    private static <T> boolean contains(Row<T> row, @Nullable T value) {
        return switch (row) {
            case Option<T> option -> Objects.equals(option.value(), value);
            case Group<T> group -> group.members().contains(value);
        };
    }

    SelectOptions<T> options() {
        return options;
    }

    /** True while the list shows categories (grouped and not filtering, or a submenu). */
    boolean isTree() {
        return submenu || paths != null && !options.isFiltering();
    }

    /** The category nearest to {@code value}, shown as its prefix in flat results. */
    @Nullable SelectGroup prefixOf(T value) {
        if (paths == null) return null;
        List<SelectGroup> path = paths.apply(value);
        return path == null || path.isEmpty() ? null : path.getLast();
    }

    List<Row<T>> rows() {
        if (isTree()) {
            return tree;
        }
        return options.visible().stream().<Row<T>>map(value -> new Option<>(value, prefixOf(value))).toList();
    }

    int highlightedIndex() {
        return isTree() ? treeHighlight : options.highlightedIndex();
    }

    @Nullable Row<T> highlighted() {
        if (isTree()) {
            return treeHighlight < 0 ? null : tree.get(treeHighlight);
        }
        T value = options.highlighted();
        return value == null ? null : new Option<>(value, prefixOf(value));
    }

    void highlight(int index) {
        if (isTree() && index >= 0 && index < tree.size()) treeHighlight = index;
    }

    void move(int delta) {
        if (isTree()) {
            if (!tree.isEmpty()) treeHighlight = Math.floorMod(treeHighlight + delta, tree.size());
        } else {
            options.move(delta);
        }
    }

    void filter(String query) {
        if (!submenu) options.filter(query);
    }
}
