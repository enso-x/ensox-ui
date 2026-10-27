package com.ensox.ui.anim;

/** Work ticked once per rendered frame by a {@link com.ensox.ui.core.UiRoot}. */
@FunctionalInterface
public interface FrameTask {
    /** @return {@code true} to keep running, {@code false} to be removed */
    boolean tick(float deltaSeconds);
}
