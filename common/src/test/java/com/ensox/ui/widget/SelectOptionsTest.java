package com.ensox.ui.widget;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SelectOptionsTest {
    @Test void matchesLocalizedLabelsAndIdsCaseInsensitivelyInOriginalOrder() {
        var values = List.of("minecraft:diamond_sword", "pack:fire_sword", "minecraft:stone");
        SelectOptions<String> choices = new SelectOptions<>(values, values.getFirst(),
            value -> value + (value.endsWith("sword") ? " Алмазный меч" : " Камень"));
        choices.filter("  МЕЧ minecraft  ");
        assertEquals(List.of("minecraft:diamond_sword"), choices.visible());
        choices.filter("sword");
        assertEquals(values.subList(0, 2), choices.visible());
    }

    @Test void emptyResultsClearHighlightAndRestoringQueryRestoresNavigation() {
        SelectOptions<String> choices = new SelectOptions<>(List.of("a", "b", "c"), "b", value -> value);
        assertEquals("b", choices.highlighted());
        choices.filter("missing");
        assertTrue(choices.visible().isEmpty());
        assertEquals(-1, choices.highlightedIndex());
        choices.move(1);
        assertNull(choices.highlighted());
        choices.filter("");
        assertEquals("a", choices.highlighted());
        choices.move(-1);
        assertEquals("c", choices.highlighted());
        choices.move(1);
        assertEquals("a", choices.highlighted());
    }

    @Test void filteringRetainsAnExistingHighlightAndNeverMutatesTheSnapshot() {
        List<String> source = new ArrayList<>(List.of("apple", "apricot", "pear"));
        AtomicInteger indexed = new AtomicInteger();
        SelectOptions<String> choices = new SelectOptions<>(source, "apricot", value -> {
            indexed.incrementAndGet();
            return value;
        });
        source.clear();
        choices.filter("ap");
        assertEquals("apricot", choices.highlighted());
        choices.filter("pear");
        assertEquals("pear", choices.highlighted());
        choices.filter("");
        assertEquals(3, choices.visible().size());
        assertEquals(3, indexed.get(), "Large option lists must not recompute translated labels per keystroke");
    }
}
