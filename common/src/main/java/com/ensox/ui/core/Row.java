package com.ensox.ui.core;

/** Horizontal {@link Flow}. */
public class Row extends Flow {
    public Row() {
        super(Axis.HORIZONTAL);
    }

    public static Row of(Widget... children) {
        Row row = new Row();
        row.add(children);
        return row;
    }
}
