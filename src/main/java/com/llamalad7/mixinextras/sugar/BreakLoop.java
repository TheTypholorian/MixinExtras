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
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface BreakLoop {
    /**
     * The depth of the loop to break out of, starting at 0 and increasing by one for each nested loop.<br>
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
