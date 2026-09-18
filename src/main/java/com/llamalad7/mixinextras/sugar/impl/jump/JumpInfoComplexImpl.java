package com.llamalad7.mixinextras.sugar.impl.jump;

import com.llamalad7.mixinextras.sugar.jump.JumpInfoComplex;

@SuppressWarnings("unused")
public class JumpInfoComplexImpl implements JumpInfoComplex {
    private boolean jumped = false;
    private final Object[] locals, stack;

    public JumpInfoComplexImpl(int locals, int stack) {
        this.locals = new Object[locals];
        this.stack = new Object[stack];
    }

    @Override
    public int getNumberOfLocals() {
        return locals.length;
    }

    @Override
    public int getSizeOfStack() {
        return stack.length;
    }

    @Override
    public void setLocal(int index, Object value) {
        locals[index] = value;
    }

    @Override
    public void setStack(int index, Object value) {
        stack[index] = value;
    }

    @Override
    public void jump() {
        jumped = true;
    }

    @Override
    public boolean hasJumped() {
        return jumped;
    }

    public Object localObject(int index) {
        return locals[index];
    }

    public int localInt(int index) {
        return (int) localObject(index);
    }

    public long localLong(int index) {
        return (long) localObject(index);
    }

    public float localFloat(int index) {
        return (float) localObject(index);
    }

    public double localDouble(int index) {
        return (double) localObject(index);
    }

    public Object stackObject(int index) {
        return stack[index];
    }

    public int stackInt(int index) {
        return (int) stackObject(index);
    }

    public long stackLong(int index) {
        return (long) stackObject(index);
    }

    public float stackFloat(int index) {
        return (float) stackObject(index);
    }

    public double stackDouble(int index) {
        return (double) stackObject(index);
    }
}
