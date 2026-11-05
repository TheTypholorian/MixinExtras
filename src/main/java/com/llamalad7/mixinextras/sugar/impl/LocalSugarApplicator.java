package com.llamalad7.mixinextras.sugar.impl;

import com.llamalad7.mixinextras.injector.StackExtension;
import com.llamalad7.mixinextras.sugar.impl.ref.LocalRefClassGenerator;
import com.llamalad7.mixinextras.sugar.impl.ref.LocalRefUtils;
import com.llamalad7.mixinextras.utils.Decorations;
import com.llamalad7.mixinextras.utils.InjectorUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.injection.modify.InvalidImplicitDiscriminatorException;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator.Context;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes.InjectionNode;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.util.Annotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LocalSugarApplicator extends SugarApplicator {
    private final boolean isArgsOnly;
    private final int jumpIndex;
    private final Type targetLocalType = LocalRefUtils.getTargetType(this.paramType, this.paramGeneric);
    private final boolean isMutable = targetLocalType != paramType;
    protected AbstractJumpSugarApplicator jump;

    LocalSugarApplicator(SugarInjector parent, SugarParameter parameter) {
        super(parent, parameter);
        this.isArgsOnly = Annotations.getValue(sugar, "argsOnly", (Boolean) false);
        this.jumpIndex = Annotations.getValue(sugar, "jump", -1);
    }

    @Override
    void init() {
        if (jumpIndex != -1) {
            List<AbstractJumpSugarApplicator> jumps = parent.getApplicators(AbstractJumpSugarApplicator.class);

            if (jumpIndex < 0 || jumpIndex >= jumps.size()) {
                throw new SugarApplicationException("Out of bounds jump index " + jumpIndex + ", must be in range [0, " + jumps.size() + ")");
            }

            jump = jumps.get(jumpIndex);
        }
    }

    @Override
    void validate(Target target, InjectionNode node) {
        if (jump != null) {
            if (!LocalRefUtils.isStackRef(paramType)) {
                throw new SugarApplicationException("Jump locals must be a StackRef type");
            }

            node = jump.getPerTarget(target, node).jumpTarget;
        }

        LocalVariableDiscriminator discriminator = LocalVariableDiscriminator.parse(sugar);
        Context context = InjectorUtils.getOrCreateLocalContext(target, node, info, targetLocalType, isArgsOnly);
        if (discriminator.printLVT()) {
            InjectorUtils.printLocals(target, node.getCurrentTarget(), context, discriminator, targetLocalType, isArgsOnly);
            info.addCallbackInvocation(info.getMethod());
            throw new SugarApplicationException("Application aborted because locals are being printed instead.");
        }
        try {
            if (discriminator.findLocal(context) < 0) {
                throw new SugarApplicationException("Unable to find matching local!");
            }
        } catch (InvalidImplicitDiscriminatorException e) {
            throw new SugarApplicationException("Invalid implicit variable discriminator: ", e);
        }
    }

    @Override
    void prepare(Target target, InjectionNode node) {
        if (jump != null) {
            node = jump.getPerTarget(target, node).jumpTarget;
        }

        InjectorUtils.getOrCreateLocalContext(target, node, info, targetLocalType, isArgsOnly);
    }

    @Override
    void inject(Target target, InjectionNode node, InjectionNode oldNode, StackExtension stack) {
        if (jump == null) {
            LocalVariableDiscriminator discriminator = LocalVariableDiscriminator.parse(sugar);
            Context context = InjectorUtils.getOrCreateLocalContext(target, node, info, targetLocalType, isArgsOnly);
            int localIndex = discriminator.findLocal(context);
            if (localIndex < 0) {
                throw new SugarApplicationException("Failed to match a local, this should have been caught during validation.");
            }

            if (isMutable) {
                String refName = LocalRefClassGenerator.getForType(targetLocalType);
                int localRefIndex = getOrCreateRef(target, node, refName, localIndex, stack);
                stack.extra(1);
                target.insns.insertBefore(node.getCurrentTarget(), new VarInsnNode(Opcodes.ALOAD, localRefIndex));
            } else {
                stack.extra(targetLocalType.getSize());
                target.insns.insertBefore(node.getCurrentTarget(), new VarInsnNode(targetLocalType.getOpcode(Opcodes.ILOAD), localIndex));
            }
        } else {
            AbstractJumpSugarApplicator.PerTarget perTarget = jump.getPerTarget(target, oldNode);
            LocalVariableDiscriminator discriminator = LocalVariableDiscriminator.parse(sugar);
            Context context = InjectorUtils.getOrCreateLocalContext(target, perTarget.jumpTarget, info, targetLocalType, isArgsOnly);
            int localIndex = discriminator.findLocal(context);
            if (localIndex < 0) {
                throw new SugarApplicationException("Failed to match a local, this should have been caught during validation.");
            }

            String refImpl = LocalRefClassGenerator.getForJumpType(targetLocalType);
            int localRefIndex = createJumpRef(target, node, refImpl, stack);
            stack.extra(1);
            target.insns.insertBefore(node.getCurrentTarget(), new VarInsnNode(Opcodes.ALOAD, localRefIndex));

            InsnList disposal = new InsnList();
            disposal.add(new VarInsnNode(Opcodes.ALOAD, localRefIndex));
            LocalRefUtils.generateDisposal(disposal, targetLocalType, refImpl);
            disposal.add(new VarInsnNode(targetLocalType.getOpcode(Opcodes.ISTORE), localIndex));
            perTarget.locals.add(disposal);
        }
    }

    @Override
    int postProcessingPriority() {
        // Late, we need to be tight around the handler calls to ensure proper initialization and disposal.
        return 1000;
    }

    private int getOrCreateRef(Target target, InjectionNode node, String refImpl, int localIndex, StackExtension stack) {
        Map<Integer, Integer> refIndices = node.getDecoration(Decorations.LOCAL_REF_MAP);
        if (refIndices == null) {
            refIndices = new HashMap<>();
            node.decorate(Decorations.LOCAL_REF_MAP, refIndices);
        }
        if (refIndices.containsKey(localIndex)) {
            // Another handler has already created an applicable reference, so we should share it.
            return refIndices.get(localIndex);
        }
        // We have to create the reference ourselves as we are the first to need it.
        int localRefIndex = target.allocateLocal();
        target.addLocalVariable(localRefIndex, "localRef" + localRefIndex, 'L' + refImpl + ';');
        // Make and store the reference object, currently uninitialized.
        InsnList construction = new InsnList();
        LocalRefUtils.generateNew(construction, refImpl);
        construction.add(new VarInsnNode(Opcodes.ASTORE, localRefIndex));
        target.insertBefore(node, construction);

        SugarPostProcessingExtension.enqueuePostProcessing(this, () -> {
            // When all injectors have finished applying, we need to initialize the refs before the handler call,
            // and write them back to the target method after. It's important to do this late so they're as tight
            // as possible around the call and we don't have any issues with stale values being used.
            InsnList initialization = new InsnList();
            initialization.add(new VarInsnNode(Opcodes.ALOAD, localRefIndex));
            initialization.add(new VarInsnNode(targetLocalType.getOpcode(Opcodes.ILOAD), localIndex));
            LocalRefUtils.generateInitialization(initialization, targetLocalType, refImpl);
            target.insertBefore(node.getCurrentTarget(), initialization);

            InsnList disposal = new InsnList();
            disposal.add(new VarInsnNode(Opcodes.ALOAD, localRefIndex));
            LocalRefUtils.generateDisposal(disposal, targetLocalType, refImpl);
            disposal.add(new VarInsnNode(targetLocalType.getOpcode(Opcodes.ISTORE), localIndex));
            target.insns.insert(node.getCurrentTarget(), disposal);
        });

        // This covers the init and dispose calls, as well as at least the 2 stack entries for the initialization.
        stack.extra(targetLocalType.getSize() + 1);

        // Tell future injectors where to find the reference.
        refIndices.put(localIndex, localRefIndex);

        return localRefIndex;
    }

    private int createJumpRef(Target target, InjectionNode node, String refImpl, StackExtension stack) {
        // We always create a ref, never cache.
        int localRefIndex = target.allocateLocal();
        target.addLocalVariable(localRefIndex, "jumpLocalRef" + localRefIndex, 'L' + refImpl + ';');
        // Make and store the reference object, currently uninitialized.
        InsnList construction = new InsnList();
        LocalRefUtils.generateNew(construction, refImpl);
        construction.add(new VarInsnNode(Opcodes.ASTORE, localRefIndex));
        target.insertBefore(node, construction);

        // This covers the init and dispose calls, as well as at least the 2 stack entries for the initialization.
        stack.extra(targetLocalType.getSize() + 1);
        return localRefIndex;
    }
}
