package com.ensox.ui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberPrecisionTest {
    @Test
    void widenedFloatStepsKeepTheirDecimalPlaces() {
        assertEquals(2, Slider.stepScale(0.01F));
        assertEquals(1, Slider.stepScale(0.1F));
        assertEquals(3, Slider.stepScale(0.005F));
        assertEquals(0, Slider.stepScale(1.0F));
    }

    @Test
    void exactDoubleStepsAreUnchanged() {
        assertEquals(2, Slider.stepScale(0.01));
        assertEquals(4, Slider.stepScale(0.0001));
        assertEquals(1, Slider.stepScale(2.5));
        assertEquals(0, Slider.stepScale(0));
    }

    @Test
    void typedValueWithFloatStepIsShownAsTyped() {
        NumberField.Format format = NumberField.Format.decimals(NumberField.Format.decimalsForStep(0.01F));
        assertEquals("0.20", format.format(format.parse("0.2")));
        // A float-backed value widened to double still prints cleanly.
        assertEquals("0.20", format.format(0.2F));
    }
}
