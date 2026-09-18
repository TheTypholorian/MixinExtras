package com.llamalad7.mixinextras.sugar;

import com.llamalad7.mixinextras.sugar.jump.JumpInfo;
import com.llamalad7.mixinextras.sugar.jump.JumpInfoComplex;
import org.spongepowered.asm.mixin.injection.At;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A mixin sugar annotation that allows for an arbitrary jump to any place in the target method.<br>
 * Arguments must be of type {@link JumpInfo} or {@link JumpInfoComplex} when locals/stack values need extra handling (se {@link Jump#localsToModify()} and {@link Jump#shiftBeforeStack()} for more info).<br>
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
 *         methodA();
 *     } else {
 *         JumpInfo.Impl jumpHandle0 = new JumpInfo.Impl(0, 0);
 *         mixinMethod(null, jumpHandle0);
 *
 *         if (jumpHandle0) {
 *             methodA();
 *         } else {
 *             methodB();
 *         }
 *     }
 * }
 * }</pre>
 * though that is not exact, and there will only be <bold>one</bold> {@code methodA} call in the bytecode.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface Jump {
    /**
     * The instruction to jump to.
     */
    At value();
    /**
     * Number of locals (highest first) to modify at the jump target.<br>
     * For example, take this code:
     * <pre>{@code
     * if (...) {
     *     int i = 10;
     *     float j = 5;
     *     System.out.println(i + j);
     * }
     *
     * someMethod();
     * }</pre>
     * If you jump from the `someMethod` call to the `println` call, the `i` and `j` local variables will be uninitialized, which is problematic.
     * There are two ways to deal with this.
     * Option 1 is to instead jump before the locals are initialized, and they will be set to their normal value (or whatever value is mixin'd in).<br>
     * However, if you want a local to be a different value, then use `localsToModify`.
     * Specify each local you want to modify, then use `JumpInfo$Complex.setLocal` to modify them (`setLocal` calls will be ignored unless the jump is invoked).
     * Note that the indices for `setLocal` are indices into the `localsToModify` array.<br>
     * Going back to the original example, your mixin would look like this for option 2:
     * <pre>{@code
     * @Inject(
     *     method = "...",
     *     at = @At(
     *         value = "INVOKE",
     *         target = "someMethod"
     *     )
     * )
     * private void example(
     *     CallbackInfo ci,
     *     @Jump(
     *         value = @At(
     *             value = "INVOKE",
     *             target = "Ljava/io/PrintStream;println(Ljava/lang/String;)V"
     *         ),
     *         shiftBeforeStack = true,
     *         localsToModify = {
     *             @Local(type = int.class),
     *             @Local(type = float.class)
     *         }
     *     ) JumpInfo.Complex jump // Note that this is a JumpInfo.Complex type, as it has the setLocal and setStack methods.
     * ) {
     *     if (shouldJump) {
     *         jump.jump();
     *         jump.setLocal(0, <value for i>);
     *         jump.setLocal(1, <value for j>);
     *     }
     * }
     * }</pre>
     * For compatibility, in the case that your code could have a local variable be the default or be a different value, it is good design to use two separate jumps (one with option 1 and another with option 2).
     */
    Local[] localsToModify() default {};
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
