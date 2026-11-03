package com.llamalad7.mixinextras.sugar.jump;

/**
 * A handle for a jump/goto in a mixin.<br>
 * Note that the jump will only occur after the mixin handler has finished, like {@link org.spongepowered.asm.mixin.injection.callback.CallbackInfo}.<br>
 * See {@link com.llamalad7.mixinextras.sugar.Jump} for usage instructions.
 */
@SuppressWarnings("unused")
public interface JumpInfo {
    void jump();

    boolean willJump();
}
