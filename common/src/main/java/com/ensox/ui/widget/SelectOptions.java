package com.ensox.ui.widget;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/** One popup's searchable snapshot. Highlighting never commits the Select's value. */
final class SelectOptions<T> {
    private record Entry<T>(T value, String searchText) { }

    /** A matched part of a displayed label, {@code [start, end)} in its characters. */
    record Match(int start, int end) { }

    private final List<Entry<T>> all;
    private List<Entry<T>> visible;
    private int highlighted;
    private String[] terms = new String[0];

    SelectOptions(List<T> options, @Nullable T selected, Function<T, String> searchText) {
        all = options.stream().map(option -> new Entry<>(option, normalize(searchText.apply(option)))).toList();
        visible = all;
        highlighted = Math.max(0, indexOf(selected));
        if (visible.isEmpty()) highlighted = -1;
    }

    List<T> visible() { return visible.stream().map(Entry::value).toList(); }
    int highlightedIndex() { return highlighted; }
    @Nullable T highlighted() { return highlighted < 0 ? null : visible.get(highlighted).value(); }

    void filter(String query) {
        T previous = highlighted();
        String normalized = normalize(query).strip();
        terms = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        visible = all.stream().filter(entry -> matches(entry.searchText(), terms)).toList();
        highlighted = visible.isEmpty() ? -1 : Math.max(0, indexOf(previous));
    }

    int visibleCount() { return visible.size(); }

    int totalCount() { return all.size(); }

    /** Parts of {@code label} that match the current filter terms, sorted and merged. */
    List<Match> matches(String label) {
        if (terms.length == 0) return List.of();
        String text = normalize(label);
        List<Match> found = new ArrayList<>();
        for (String term : terms) {
            for (int at = text.indexOf(term); at >= 0; at = text.indexOf(term, at + 1)) {
                found.add(new Match(at, at + term.length()));
            }
        }
        found.sort(Comparator.comparingInt(Match::start));
        List<Match> merged = new ArrayList<>();
        for (Match match : found) {
            Match last = merged.isEmpty() ? null : merged.getLast();
            if (last != null && match.start() <= last.end()) {
                merged.set(merged.size() - 1, new Match(last.start(), Math.max(last.end(), match.end())));
            } else {
                merged.add(match);
            }
        }
        return merged;
    }

    void move(int delta) {
        if (!visible.isEmpty()) highlighted = Math.floorMod(highlighted + delta, visible.size());
    }

    private int indexOf(@Nullable T value) {
        for (int i = 0; i < visible.size(); i++) if (Objects.equals(visible.get(i).value(), value)) return i;
        return -1;
    }

    private static boolean matches(String text, String[] terms) {
        for (String term : terms) if (!text.contains(term)) return false;
        return true;
    }

    private static String normalize(@Nullable String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
