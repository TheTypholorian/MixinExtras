package com.llamalad7.mixinextras.sugar.impl;

import com.llamalad7.mixinextras.injector.StackExtension;
import com.llamalad7.mixinextras.sugar.impl.ref.LocalRefClassGenerator;
import com.llamalad7.mixinextras.sugar.impl.ref.LocalRefUtils;
import com.llamalad7.mixinextras.utils.Decorations;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes.InjectionNode;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.util.Annotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class JumpStackSugarApplicator extends SugarApplicator {
    private final int stackIndex, jumpIndex;
    private final Type targetStackType = LocalRefUtils.getTargetType(this.paramType, this.paramGeneric);
    protected AbstractJumpSugarApplicator jump;

    JumpStackSugarApplicator(SugarInjector parent, SugarParameter parameter) {
        super(parent, parameter);
        this.jumpIndex = Annotations.getValue(sugar, "jump", 0);
        this.stackIndex = Annotations.getValue(sugar, "index", 0);
    }

    @Override
    void init() {
        List<AbstractJumpSugarApplicator> jumps = parent.getApplicators(AbstractJumpSugarApplicator.class);

        if (jumpIndex < 0 || jumpIndex >= jumps.size()) {
            throw new SugarApplicationException("Out of bounds jump index " + jumpIndex + ", must be in range [0, " + jumps.size() + ")");
        }

        jump = jumps.get(jumpIndex);
    }

    @Override
    void validate(Target target, InjectionNode node) {
        if (!LocalRefUtils.isStackRef(paramType)) {
            throw new SugarApplicationException("Jump stacks must be a StackRef type");
        }
    }

    @Override
    void prepare(Target target, InjectionNode node) {
    }

    @Override
    void inject(Target target, InjectionNode node, InjectionNode oldNode, StackExtension stack) {
        AbstractJumpSugarApplicator.PerTarget perTarget = jump.getPerTarget(target, oldNode);
        perTarget.ensureFramesInitialized(target, node);

        if (stackIndex < 0 || stackIndex >= perTarget.targetFrame.getStackSize()) {
            throw new SugarApplicationException("Out of bounds stack index " + jumpIndex + ", must be in range [0, " + perTarget.targetFrame.getStackSize() + ")");
        }

        BasicValue targetValue = perTarget.targetFrame.getStack(stackIndex);

        if (targetValue == BasicValue.UNINITIALIZED_VALUE) {
            throw new AssertionError();
        }

        if (!targetValue.getType().equals(targetStackType)) {
            throw new SugarApplicationException("Wrong jump stack type, expected " + targetValue.getType().getClassName() + " but got " + targetStackType.getClassName());
        }

        String refImpl = LocalRefClassGenerator.getForJumpType(targetStackType);
        int localRefIndex = createJumpRef(target, node, refImpl, stack);
        stack.extra(1);
        target.insns.insertBefore(node.getCurrentTarget(), new VarInsnNode(Opcodes.ALOAD, localRefIndex));

        InsnList disposal = new InsnList();
        disposal.add(new VarInsnNode(Opcodes.ALOAD, localRefIndex));
        LocalRefUtils.generateDisposal(disposal, targetStackType, refImpl);
        perTarget.stack.put(stackIndex, disposal);
    }

    private int createJumpRef(Target target, InjectionNode node, String refImpl, StackExtension stack) {
        // We always create a ref, never cache.
        int localRefIndex = target.allocateLocal();
        target.addLocalVariable(localRefIndex, "jumpStackRef" + localRefIndex, 'L' + refImpl + ';');
        // Make and store the reference object, currently uninitialized.
        InsnList construction = new InsnList();
        LocalRefUtils.generateNew(construction, refImpl);
        construction.add(new VarInsnNode(Opcodes.ASTORE, localRefIndex));
        target.insertBefore(node, construction);

        // This covers the init and dispose calls, as well as at least the 2 stack entries for the initialization.
        stack.extra(targetStackType.getSize() + 1);
        return localRefIndex;
    }
}
