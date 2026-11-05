package com.llamalad7.mixinextras.sugar.ref.jump;

/**
 * Represents a set-only reference to a local variable at a jump target.
 * Specialised variants are provided in the same package for all primitive types.
 * Do not store {@link StackRef} instances - their lifecycle is an implementation detail.
 * They will be valid throughout the handler method they are passed to, but no other guarantees are made.
 * Do not create custom {@link StackRef} implementations - methods may be added in the future.
 * @param <T> the type of the local variable - MUST be concrete
 */
public interface StackRef<T> {
    /**
     * Sets the value of the variable.
     * Will only apply if the jump is executed.
     * @param value a new value for the variable
     */
    void set(T value);
}
