package com.ensox.ui.widget;

import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * A category of a grouped {@link Select}: its row opens a submenu with the category's options.
 * Two groups are the same category when their keys are equal.
 *
 * @param ruleAbove draw a dotted rule above the category's row, to set a run of categories apart
 */
public record SelectGroup(String key, Component label, boolean ruleAbove) {
    public SelectGroup {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
    }

    public SelectGroup(String key, Component label) {
        this(key, label, false);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SelectGroup group && group.key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}
