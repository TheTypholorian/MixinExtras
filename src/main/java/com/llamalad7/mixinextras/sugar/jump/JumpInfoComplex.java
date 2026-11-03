package com.llamalad7.mixinextras.sugar.jump;

import com.llamalad7.mixinextras.sugar.Jump;

/**
 * A more complex version of {@link JumpInfo}, used in the case where stack/local values need manual adjustment.
 * See {@link Jump} for usage instructions.
 */
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
