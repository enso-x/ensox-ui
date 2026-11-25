package com.ensox.ui.core;

/** Vertical {@link Flow}. */
public class Column extends Flow {
    public Column() {
        super(Axis.VERTICAL);
    }

    public static Column of(Widget... children) {
        Column column = new Column();
        column.add(children);
        return column;
    }
}
