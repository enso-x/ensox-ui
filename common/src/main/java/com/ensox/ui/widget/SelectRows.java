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
 * (a grouped option carries its category as a prefix). Grouped and unfiltered, the list is a tree:
 * one row per category, in the order of their first options, and ungrouped options in place.
 */
final class SelectRows<T> {
    sealed interface Row<T> permits Option, Group { }

    record Option<T>(T value, @Nullable SelectGroup prefix) implements Row<T> { }

    record Group<T>(SelectGroup group, List<T> members) implements Row<T> {
        Group {
            members = List.copyOf(members);
        }
    }

    private final SelectOptions<T> options;
    private final @Nullable Function<T, SelectGroup> grouping;
    private final List<Row<T>> tree;
    private int treeHighlight;

    SelectRows(SelectOptions<T> options, @Nullable Function<T, SelectGroup> grouping) {
        this.options = options;
        this.grouping = grouping;
        this.tree = grouping == null ? List.of() : tree(options.all(), grouping);
        T selected = options.highlighted();
        treeHighlight = tree.isEmpty() ? -1 : 0;
        for (int i = 0; i < tree.size(); i++) {
            if (contains(tree.get(i), selected)) {
                treeHighlight = i;
                break;
            }
        }
    }

    private static <T> List<Row<T>> tree(List<T> all, Function<T, SelectGroup> grouping) {
        List<Row<T>> rows = new ArrayList<>();
        Map<SelectGroup, Integer> at = new LinkedHashMap<>();
        Map<SelectGroup, List<T>> members = new LinkedHashMap<>();
        for (T value : all) {
            SelectGroup group = grouping.apply(value);
            if (group == null) {
                rows.add(new Option<>(value, null));
                continue;
            }
            if (!at.containsKey(group)) {
                at.put(group, rows.size());
                rows.add(null);
                members.put(group, new ArrayList<>());
            }
            members.get(group).add(value);
        }
        at.forEach((group, index) -> rows.set(index, new Group<>(group, members.get(group))));
        return List.copyOf(rows);
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

    /** True while the list shows categories (grouped and not filtering). */
    boolean isTree() {
        return grouping != null && !options.isFiltering();
    }

    @Nullable SelectGroup groupOf(T value) {
        return grouping == null ? null : grouping.apply(value);
    }

    List<Row<T>> rows() {
        if (isTree()) {
            return tree;
        }
        return options.visible().stream().<Row<T>>map(value -> new Option<>(value, groupOf(value))).toList();
    }

    int highlightedIndex() {
        return isTree() ? treeHighlight : options.highlightedIndex();
    }

    @Nullable Row<T> highlighted() {
        if (isTree()) {
            return treeHighlight < 0 ? null : tree.get(treeHighlight);
        }
        T value = options.highlighted();
        return value == null ? null : new Option<>(value, groupOf(value));
    }

    void highlight(int index) {
        if (isTree()) {
            if (index >= 0 && index < tree.size()) treeHighlight = index;
        }
    }

    void move(int delta) {
        if (isTree()) {
            if (!tree.isEmpty()) treeHighlight = Math.floorMod(treeHighlight + delta, tree.size());
        } else {
            options.move(delta);
        }
    }

    void filter(String query) {
        options.filter(query);
    }
}
