package com.llamalad7.mixinextras.sugar;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface BreakLoop {
    /**
     * The depth of the loop, starting at 0 and increases by one for each nested loop.<br>
     * Defaults to -1 which picks the smallest loop that surrounds the injection point.<br>
     * Example:
     * <pre>{@code
     * for (...) { // Cannot be targeted.
     * }
     *
     * while (...) { // depth = 0
     *     for (...) { // depth = 1, this is the default loop that is broken.
     *         injectedHandler();
     *
     *         do { // Cannot be targeted.
     *         } while (...)
     *     }
     * }
     * }</pre>
     */
    int depth() default -1;
}
