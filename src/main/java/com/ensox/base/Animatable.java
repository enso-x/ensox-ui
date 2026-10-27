package com.ensox.base;

public interface Animatable<T extends Animatable<T>> {
    T interpolate(T next, float delta);
}
