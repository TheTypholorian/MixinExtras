package com.llamalad7.mixinextras.sugar;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * TODO docs
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface Stack {
    /**
     * The index in the stack where the value should be set.
     */
    int index();

    /**
     * The 0-based index of jump to target. Indices are based on order in the injector parameter list.
     *
     * @see Jump
     */
    int jump();
}
