package com.llamalad7.mixinextras.sugar.impl;

import com.llamalad7.mixinextras.injector.StackExtension;
import com.llamalad7.mixinextras.sugar.impl.jump.JumpInfoImpl;
import com.llamalad7.mixinextras.sugar.jump.JumpInfo;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.util.asm.ASM;
import org.spongepowered.asm.util.asm.MixinVerifier;

import java.util.*;

abstract class AbstractJumpSugarApplicator extends SugarApplicator {
    public static final Type JUMP_INFO_TYPE = Type.getType(JumpInfo.class);
    public static final Type JUMP_INFO_IMPL_TYPE = Type.getType(JumpInfoImpl.class);

    // TODO this needs to be per-target
    protected final List<LocalSugarApplicator> locals = new ArrayList<>();
    protected final Map<Integer, InsnList> stack = new HashMap<>();
    protected InjectionNodes.InjectionNode jumpTarget;
    protected Frame<BasicValue> sourceFrame, targetFrame;

    AbstractJumpSugarApplicator(SugarInjector parent, SugarParameter parameter) {
        super(parent, parameter);
    }

    @Override
    int postProcessingPriority() {
        // We want to be after Locals so mutable refs are disposed before jumps
        return 10000;
    }

    protected Frame<BasicValue>[] analyze(Target target) {
        List<Type> interfaces = null;
        Type superType = null;

        if (target.classNode.interfaces != null) {
            interfaces = new ArrayList<>();
            for (String iface : target.classNode.interfaces) {
                interfaces.add(Type.getObjectType(iface));
            }
        }

        if (target.classNode.superName != null) {
            superType = Type.getObjectType(target.classNode.superName);
        }

        int maxStack = target.method.maxStack;
        target.method.maxStack = -1;

        try {
            return new Analyzer<>(new MixinVerifier(
                    ASM.API_VERSION,
                    Type.getObjectType(target.classNode.name),
                    superType,
                    interfaces,
                    (target.classNode.access & Opcodes.ACC_INTERFACE) != 0
            )).analyze(target.classNode.name, target.method);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        } finally {
            target.method.maxStack = maxStack;
        }
    }

    protected void setJumpTarget(AbstractInsnNode insn, Target target) {
        if (!(insn instanceof LabelNode)) {
            LabelNode label = new LabelNode();
            target.method.instructions.insertBefore(insn, label);
            insn = label;
        }

        jumpTarget = target.addInjectionNode(insn);
    }

    @Override
    void inject(Target target, InjectionNodes.InjectionNode node, StackExtension stack) {
        Frame<BasicValue>[] frames = analyze(target);

        targetFrame = frames[target.insns.indexOf(jumpTarget.getCurrentTarget())];
        sourceFrame = frames[target.insns.indexOf(node.getCurrentTarget()) + 1];

        for (int i = targetFrame.getStackSize() - 1; i >= 0; i--) {
            BasicValue value = targetFrame.getStack(i);

            if (value.equals(BasicValue.UNINITIALIZED_VALUE)) {
                throw new SugarApplicationException("Cannot jump to a target with uninitialized objects on the stack (ex. constructors). Set shiftBeforeStack to true in the Jump sugar to fix this.");
            }
        }

        LabelNode labelJumpTarget = (LabelNode) this.jumpTarget.getCurrentTarget();
        int jumpInfoVar = target.allocateLocal();
        target.addLocalVariable(jumpInfoVar, "jumpInfo" + jumpInfoVar, JUMP_INFO_IMPL_TYPE.getDescriptor());

        stack.extra(2);
        InsnList before = new InsnList();
        before.add(new TypeInsnNode(Opcodes.NEW, JUMP_INFO_IMPL_TYPE.getInternalName()));
        before.add(new InsnNode(Opcodes.DUP));
        before.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                JUMP_INFO_IMPL_TYPE.getInternalName(),
                "<init>",
                "()V",
                false
        ));
        before.add(new VarInsnNode(Opcodes.ASTORE, jumpInfoVar));
        before.add(new VarInsnNode(Opcodes.ALOAD, jumpInfoVar));
        target.insertBefore(node, before);

        SugarPostProcessingExtension.enqueuePostProcessing(this, () -> {
            InsnList after = new InsnList();
            LabelNode notJumped = new LabelNode();

            stack.extra(1);
            after.add(new VarInsnNode(Opcodes.ALOAD, jumpInfoVar));
            after.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    JUMP_INFO_IMPL_TYPE.getInternalName(),
                    "willJump",
                    "()Z",
                    false
            ));
            after.add(new JumpInsnNode(Opcodes.IFEQ, notJumped));

            for (int i = sourceFrame.getStackSize() - 1; i >= 0; i--) {
                BasicValue value = sourceFrame.getStack(i);

                switch (value.getType().getSize()) {
                    case 1:
                        after.add(new InsnNode(Opcodes.POP));
                        break;
                    case 2:
                        after.add(new InsnNode(Opcodes.POP2));
                        break;
                    default:
                        throw new AssertionError();
                }
            }

            for (int i = targetFrame.getStackSize() - 1; i >= 0; i--) {
                BasicValue value = targetFrame.getStack(i);
                InsnList handler = this.stack.get(i);

                if (handler != null) {
                    after.add(handler);
                } else {
                    switch (value.getType().getSort()) {
                        case Type.BOOLEAN:
                        case Type.CHAR:
                        case Type.BYTE:
                        case Type.SHORT:
                        case Type.INT:
                            after.add(new InsnNode(Opcodes.ICONST_0));
                            break;

                        case Type.LONG:
                            after.add(new InsnNode(Opcodes.LCONST_0));
                            break;

                        case Type.FLOAT:
                            after.add(new InsnNode(Opcodes.FCONST_0));
                            break;

                        case Type.DOUBLE:
                            after.add(new InsnNode(Opcodes.DCONST_0));
                            break;

                        default:
                            after.add(new InsnNode(Opcodes.ACONST_NULL));
                            break;
                    }
                }
            }

            for (LocalSugarApplicator local : locals) {
                after.add(local.createDisposal());
            }

            after.add(new JumpInsnNode(Opcodes.GOTO, labelJumpTarget));
            after.add(notJumped);
            target.insns.insert(node.getCurrentTarget(), after);
        });
    }
}
