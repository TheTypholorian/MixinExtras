package com.llamalad7.mixinextras.sugar;

import com.llamalad7.mixinextras.sugar.jump.JumpInfo;
import org.spongepowered.asm.mixin.injection.At;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A mixin sugar annotation that allows for an arbitrary jump to any place in the target method (the jump only occurs after the mixin handler has finished).<br>
 * Arguments must be of type {@link JumpInfo}<br>
 * Example for usage:
 * <pre>{@code
 * public static void targetMethod() {
 *     if (...) {
 *         methodA();
 *     } else {
 *         methodB();
 *     }
 * }
 *
 * @Inject(
 *     method = "targetMethod",
 *     at = @At(
 *         value = "INVOKE",
 *         target = "methodB"
 *     )
 * )
 * private static void mixinMethod(
 *     CallbackInfo ci,
 *     @Jump(
 *         value = @At(
 *             value = "INVOKE",
 *             target = "methodA"
 *         )
 *     ) JumpInfo jump
 * ) {
 *     if (shouldJump) {
 *         jump.jump();
 *     }
 * }
 * }</pre>
 * The mixin'd output will look like this:
 * <pre>{@code
 * public static void targetMethod() {
 *     if (...) {
 *     jumpTarget:
 *         methodA();
 *     } else {
 *         JumpInfo.Impl jumpHandle0 = new JumpInfo.Impl(0, 0);
 *         mixinMethod(null, jumpHandle0);
 *
 *         if (jumpHandle0) {
 *             goto jumpTarget;
 *         }
 *         methodB();
 *     }
 * }
 * }</pre>
 * There will only be <bold>one</bold> {@code methodA} call in the bytecode (regardless of what decompilers say).<br>
 * When locals or stack values at the jump target need adjustment, use a Local or Stack sugar with {@link Local#jump()}/{@link JumpStack#jump()} = [0-based index of this jump according to the injector argument order].
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface Jump {
    /**
     * The instruction to jump to.
     * Must match exactly one instruction, otherwise an error is thrown.
     */
    At value();
    /**
     * If true, shifts the jump target back until the stack is empty.<br>
     * For example, take this code:
     * <pre>{@code
     * System.out.println("abc");
     * }</pre>
     * Targeting the `println` would normally look like this:
     * <pre>{@code
     * PrintStream var0 = System.out;
     * String var1 = "abc";
     * // injection here
     * var0.println(var1);
     * }</pre>
     * However, if `shiftBeforeStack` is true, it then looks like this:
     * <pre>{@code
     * // injection here
     * System.out.println("abc");
     * }</pre>
     * If you had {@code shiftBeforeStack} set to false, then you would need to use a {@link JumpInfoComplex} and restate the stack values for {@code System.out} and {@code "abc"} in your mixin, like this:
     * <pre>{@code
     * complexHandle.jump();
     * complexHandle.setStack(0, System.out);
     * complexHandle.setStack(1, "abc");
     * }</pre>
     * which would ignore other mixins changing those values. The index parameter for `setStack` is lowest first.<br>
     * Note that the above code can be used to combine a jump and a {@link org.spongepowered.asm.mixin.injection.ModifyArgs}, which might be useful in some cases.
     */
    boolean shiftBeforeStack() default false;
}
