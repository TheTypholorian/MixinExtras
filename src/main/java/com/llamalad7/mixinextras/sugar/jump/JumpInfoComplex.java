package com.llamalad7.mixinextras.sugar.jump;

import com.llamalad7.mixinextras.sugar.Jump;

@SuppressWarnings("unused")
public interface JumpInfoComplex extends JumpInfo {
    int getNumberOfLocals();

    int getSizeOfStack();

    /**
     * @see Jump#localsToModify()
     */
    void setLocal(int index, Object value);

    /**
     * @see Jump#shiftBeforeStack()
     */
    void setStack(int index, Object value);
}
