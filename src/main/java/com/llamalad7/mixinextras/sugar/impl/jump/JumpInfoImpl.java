package com.llamalad7.mixinextras.sugar.impl.jump;

import com.llamalad7.mixinextras.sugar.jump.JumpInfo;

public class JumpInfoImpl implements JumpInfo {
    private boolean jumped = false;

    @Override
    public void jump() {
        jumped = true;
    }

    @Override
    public boolean hasJumped() {
        return jumped;
    }
}
