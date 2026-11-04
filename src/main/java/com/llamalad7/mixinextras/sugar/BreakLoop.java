package com.llamalad7.mixinextras.sugar;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A specialized type of {@link Jump} that breaks a loop wrapping the mixin target rather than jumping to a specified instruction.<br>
 * Example:
 * <pre>{@code
 * public static void targetMethod() {
 *     while (...) {
 *         for (...) {
 *             otherMethod();
 *         }
 *     }
 * }
 * }</pre>
 * <pre>{@code
 * @Inject(
 *     method = "targetMethod",
 *     at = @At(
 *         value = "INVOKE",
 *         target = "otherMethod"
 *     )
 * )
 * private static void injectedMethod(CallbackInfo ci, @BreakLoop JumpInfo loop) {
 *     if (condition) {
 *         loop.jump();
 *     }
 * }
 * }</pre>
 * That code will break the {@code for} loop if {@code condition} inside {@code injectedMethod} is true.
 * TODO will this ever need to be a complex type?
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface BreakLoop {
    /**
     * How many loops to break<br>
     * Example:
     * <pre>{@code
     * for (...) { // Cannot be targeted.
     * }
     *
     * while (...) { // depth = 1
     *     for (...) { // depth = 0, this is the default loop that is broken.
     *         injectedHandler();
     *
     *         do { // Cannot be targeted.
     *         } while (...)
     *     }
     * }
     * }</pre>
     */
    int depth() default 0;
}
