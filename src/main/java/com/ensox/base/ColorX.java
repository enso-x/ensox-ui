package com.ensox.base;

import net.minecraft.util.Mth;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.DyeColor;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ColorX — an ergonomic immutable color utility with unified conversions and modifier support.
 *
 * Features:
 *  - Unified internal RGBA float [0..1] representation.
 *  - Convenient overloaded factory methods: of(...).
 *  - Type-safe conversion via ColorType enum: to(ColorType.INT_ARGB) etc.
 *  - Chainable operations: withAlpha(), lighten(), darken(), shiftHSV().
 *  - Backward-compatible modifier syntax ("+h15", "=r128", "-v20", etc.).
 *  - Alpha stored in the highest 8 bits (bit 24) for packed ints.
 */
public final class ColorX implements Animatable<ColorX> {

    // ------------------------------ Unified color type enum ------------------------------
    public enum ColorType {
        INT_ARGB(Integer.class, c -> c.toIntARGB()),
        INT_RGB(Integer.class, c -> c.toIntRGB()),
        HEX_ARGB(String.class, c -> c.toHexARGB()),
        HEX_RGB(String.class, c -> c.toHexRGB()),
        TEXT_COLOR(TextColor.class, c -> c.toTextColor()),
        HSV(float[].class, c -> c.toHSV());

        private final Class<?> clazz;
        private final Function<ColorX, ?> fn;

        ColorType(Class<?> clazz, Function<ColorX, ?> fn) {
            this.clazz = clazz;
            this.fn = fn;
        }

        @SuppressWarnings("unchecked")
        public <T> T convert(ColorX c) { return (T) fn.apply(c); }

        @SuppressWarnings("unchecked")
        public <T> Class<T> type() { return (Class<T>) clazz; }
    }

    public enum PackedFormat { ARGB, RGB }
    public enum Model { HSV }

    // ------------------------------ Internal storage ------------------------------
    private final float r, g, b, a;

    private ColorX(float r, float g, float b, float a) {
        this.r = clamp01(r);
        this.g = clamp01(g);
        this.b = clamp01(b);
        this.a = clamp01(a);
    }

    // ------------------------------ Factory methods (of...) ------------------------------
    public static ColorX of(float r, float g, float b, float a) { return new ColorX(r, g, b, a); }
    public static ColorX of(float r, float g, float b) { return new ColorX(r, g, b, 1f); }

    public static ColorX of(int r, int g, int b, int a) { return new ColorX(r / 255f, g / 255f, b / 255f, a / 255f); }
    public static ColorX of(int r, int g, int b) { return of(r, g, b, 255); }

    public static ColorX of(int packedARGB) { return ofPacked(packedARGB, PackedFormat.ARGB); }

    public static ColorX ofPacked(int packed, PackedFormat fmt) {
        if (fmt == PackedFormat.ARGB) {
            int a = (packed >>> 24) & 0xFF;
            int r = (packed >>> 16) & 0xFF;
            int g = (packed >>> 8) & 0xFF;
            int b = packed & 0xFF;
            return of(r, g, b, a);
        } else {
            return of(0xFF000000 | (packed & 0xFFFFFF));
        }
    }

    private static Pattern getHexPattern(List<Integer> lengths) {
        String body = lengths.stream()
            .filter(n -> n > 0)
            .distinct()
            .sorted()
            .map(n -> "[0-9a-fA-F]{" + n + "}")
            .collect(Collectors.joining("|"));

        if (body.isEmpty()) {
            throw new IllegalArgumentException("No valid hex lengths");
        }

        return Pattern.compile("^#?(" + body + ")$");
    }

    public enum HexPattern {
        FULL(getHexPattern(List.of(3, 4, 6, 8))),
        LONG(getHexPattern(List.of(6, 8))),
        STANDARD(getHexPattern(List.of(6)));

        private final Pattern pattern;
        HexPattern(Pattern pattern) {
            this.pattern = pattern;
        }

        Pattern get() {
            return this.pattern;
        }
    }

    public static boolean validateHEXColor(String hexColor, HexPattern pattern) {
        return hexColor != null && pattern.get().matcher(hexColor).matches();
    }

    public static ColorX of(String hex) {
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        if (s.length() == 3 || s.length() == 4) {
            StringBuilder sb = new StringBuilder();
            for (char c : s.toCharArray()) sb.append(c).append(c);
            s = sb.toString();
        }
        if (s.length() == 6) s = "FF" + s;
        if (s.length() != 8) throw new IllegalArgumentException("Bad hex length: " + hex);
        int v = (int) Long.parseLong(s, 16);
        return of((v >>> 16) & 0xFF, (v >>> 8) & 0xFF, v & 0xFF, (v >>> 24) & 0xFF);
    }

    public static ColorX ofHSV(float hDeg, float s, float v) {
        float[] rgb = hsvToRgb(hDeg, s, v);
        return of(rgb[0], rgb[1], rgb[2], 1f);
    }

    public static ColorX ofHSV(float hDeg, float s, float v, float a) {
        float[] rgb = hsvToRgb(hDeg, s, v);
        return of(rgb[0], rgb[1], rgb[2], a);
    }

    public static ColorX of(float x, float y, float z, Model model) {
        if (model == Model.HSV) return ofHSV(x, y, z);
        throw new IllegalArgumentException("Unknown model tag");
    }

    public static ColorX of(TextColor mc) { return ofPacked(mc.getValue(), PackedFormat.RGB); }

    public static ColorX of(DyeColor dyeColor) { return of(dyeColor.getTextureDiffuseColor()); }

    public static ColorX of(ChatFormatting formatting) {
        var colorValue = formatting.getColor();
        return ofPacked(colorValue == null ? 0 : colorValue, PackedFormat.RGB);
    }

    // ------------------------------ Accessors ------------------------------
    public float r() { return r; }
    public float g() { return g; }
    public float b() { return b; }
    public float a() { return a; }

    public int ri() { return Math.round(r * 255f); }
    public int gi() { return Math.round(g * 255f); }
    public int bi() { return Math.round(b * 255f); }
    public int ai() { return Math.round(a * 255f); }

    // ------------------------------ Conversion methods ------------------------------
    public int toIntARGB() { return (ai() << 24) | (ri() << 16) | (gi() << 8) | bi(); }
    public int toIntRGB()  { return (ri() << 16) | (gi() << 8) | bi(); }
    public String toHexARGB() { return String.format("#%08X", toIntARGB()); }
    public String toHexRGB()  { return String.format("#%06X", toIntRGB()); }
    public TextColor toTextColor() { return TextColor.fromRgb(toIntRGB()); }
    public float[] toHSV() { return rgbToHsv(r, g, b, a); }

    /** Unified type-safe conversion. */
    public <T> T to(ColorType type) { return type.convert(this); }

    // ------------------------------ Mutations (return new instance) ------------------------------
    public ColorX withAlpha(float newA) { return new ColorX(r, g, b, newA); }
    public ColorX lighten(float v) { return new ColorX(r + v, g + v, b + v, a); }
    public ColorX darken(float v) { return new ColorX(r - v, g - v, b - v, a); }

    /** HSV shift: dH in degrees, dS/dV in [-1..1]. */
    public ColorX shiftHSV(float dHdeg, float dS, float dV) {
        float[] hsv = toHSV();
        float h = wrap360(hsv[0] + dHdeg);
        float s = clamp01(hsv[1] + dS);
        float v = clamp01(hsv[2] + dV);
        float[] rgb = hsvToRgb(h, s, v);
        return new ColorX(rgb[0], rgb[1], rgb[2], a);
    }

    // ------------------------------ Modifiers (compatible with old syntax) ------------------------------
    public ColorX applyModifiers(List<String> modifiers) {
        float r = this.r, g = this.g, b = this.b, a = this.a;
        float[] hsv = null;

        Map<Character, BiFunction<Integer, Integer, Integer>> op = Map.of(
            '+', Integer::sum,
            '-', (v, d) -> v - d,
            '=', (v, d) -> d
        );

        for (String m : modifiers) {
            if (m == null || m.length() < 3) continue;
            char sig = m.charAt(0);
            BiFunction<Integer, Integer, Integer> f = op.get(sig);
            char chan = Character.toLowerCase(m.charAt(1));
            int amount;
            try { amount = Integer.parseInt(m.substring(2)); } catch (Exception e) { continue; }
            if (f == null) continue;

            switch (chan) {
                case 'h':
                case 's':
                case 'v': {
                    if (hsv == null) hsv = toHSV();
                    int H = Math.round(hsv[0]);
                    int S = Math.round(hsv[1] * 255f);
                    int V = Math.round(hsv[2] * 255f);
                    switch (chan) {
                        case 'h': H = wrap360i(f.apply(H, amount)); break;
                        case 's': S = clampi(f.apply(S, amount), 0, 255); break;
                        case 'v': V = clampi(f.apply(V, amount), 0, 255); break;
                    }
                    float[] rgb = hsvToRgb(H, S / 255f, V / 255f);
                    r = rgb[0]; g = rgb[1]; b = rgb[2];
                    hsv = new float[]{H, S / 255f, V / 255f};
                    break;
                }
                case 'r': { int R = clampi(f.apply(Math.round(r * 255f), amount), 0, 255); r = R / 255f; break; }
                case 'g': { int G = clampi(f.apply(Math.round(g * 255f), amount), 0, 255); g = G / 255f; break; }
                case 'b': { int B = clampi(f.apply(Math.round(b * 255f), amount), 0, 255); b = B / 255f; break; }
                case 'a': { int A = clampi(f.apply(Math.round(a * 255f), amount), 0, 255); a = A / 255f; break; }
            }
        }
        return new ColorX(r, g, b, a);
    }

    // ------------------------------ Utilities ------------------------------
    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof ColorX color && this.toIntARGB() == color.toIntARGB();
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(this.toIntARGB());
    }

    @Override public String toString() { return "ColorX{" + toHexARGB() + "}"; }

    @Override
    public ColorX interpolate(ColorX next, float delta) {
        return ColorX.of(
            Mth.lerp(delta, this.r, next.r),
            Mth.lerp(delta, this.g, next.g),
            Mth.lerp(delta, this.b, next.b),
            Mth.lerp(delta, this.a, next.a)
        );
    }

    public static int packARGBi(int a, int r, int g, int b) { return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF); }
    public static int darkenInt(int argb, float value) { return ColorX.of(argb).darken(value).toIntARGB(); }
    public static String darkenHex(String hex, float value) { return ColorX.of(hex).darken(value).toHexARGB(); }

    public static ColorX TRANSPARENT = ColorX.of(0x00000000);
    public static ColorX BLACK = ColorX.ofPacked(0x000000, PackedFormat.RGB);
    public static ColorX WHITE = ColorX.ofPacked(0xFFFFFF, PackedFormat.RGB);

    private static float[] rgbToHsv(float r, float g, float b, float a) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0f) h = 0f;
        else if (max == r) h = 60f * (((g - b) / d) % 6f);
        else if (max == g) h = 60f * (((b - r) / d) + 2f);
        else h = 60f * (((r - g) / d) + 4f);
        if (h < 0f) h += 360f;
        float s = max == 0f ? 0f : d / max;
        float v = max;
        return new float[]{h, s, v, a};
    }

    private static float[] hsvToRgb(float hDeg, float s, float v) {
        float C = v * s;
        float X = C * (1 - Math.abs(((hDeg / 60f) % 2) - 1));
        float m = v - C;
        float r1, g1, b1;
        float h = wrap360(hDeg);
        if (h < 60) { r1 = C; g1 = X; b1 = 0; }
        else if (h < 120) { r1 = X; g1 = C; b1 = 0; }
        else if (h < 180) { r1 = 0; g1 = C; b1 = X; }
        else if (h < 240) { r1 = 0; g1 = X; b1 = C; }
        else if (h < 300) { r1 = X; g1 = 0; b1 = C; }
        else { r1 = C; g1 = 0; b1 = X; }
        return new float[]{ clamp01(r1 + m), clamp01(g1 + m), clamp01(b1 + m) };
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private static int clampi(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float wrap360(float h) { float x = h % 360f; return x < 0 ? x + 360f : x; }
    private static int wrap360i(int h) { int x = h % 360; return x < 0 ? x + 360 : x; }
}

// ----------------------------------- Examples -----------------------------------
// ColorX a = ColorX.of(0x80FFAA33);
// ColorX b = ColorX.of(0x3366FF, ColorX.PackedFormat.RGB);
// ColorX c = ColorX.of("#abc");
// ColorX d = ColorX.of(1f, 0.2f, 0.4f);
// ColorX e = ColorX.of(255, 50, 120, 200);
// ColorX h = ColorX.ofHSV(210f, 0.5f, 0.7f);
// ColorX h2 = ColorX.of(210f, 0.5f, 0.7f, ColorX.Model.HSV);
// Integer packed = a.to(ColorX.ColorType.INT_ARGB);
// String hex = a.to(ColorX.ColorType.HEX_ARGB);
// float[] hsv = a.to(ColorX.ColorType.HSV);
// TextColor mc = a.to(ColorX.ColorType.TEXT_COLOR);
// ColorX modded = a.applyModifiers(List.of("+h25", "-s20", "=a128"));
