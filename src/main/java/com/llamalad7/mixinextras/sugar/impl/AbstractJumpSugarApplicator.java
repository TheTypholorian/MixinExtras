package com.llamalad7.mixinextras.sugar.impl;

import com.llamalad7.mixinextras.injector.StackExtension;
import com.llamalad7.mixinextras.sugar.impl.jump.JumpInfoImpl;
import com.llamalad7.mixinextras.sugar.jump.JumpInfo;
import org.apache.commons.lang3.tuple.Pair;
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

    static class PerTarget {
        final Map<Integer, InsnList> locals = new HashMap<>();
        final Map<Integer, InsnList> stack = new HashMap<>();
        InjectionNodes.InjectionNode jumpTarget;
        Frame<BasicValue> sourceFrame, targetFrame;

        protected void setJumpTarget(AbstractInsnNode insn, Target target) {
            if (!(insn instanceof LabelNode)) {
                LabelNode label = new LabelNode();
                target.method.instructions.insertBefore(insn, label);
                insn = label;
            }

            jumpTarget = target.addInjectionNode(insn);
        }

        protected void ensureFramesInitialized(Target target, InjectionNodes.InjectionNode node) {
            if (sourceFrame == null || targetFrame == null) {
                if (jumpTarget == null) {
                    throw new SugarApplicationException("Jump target has not been initialized, this is a bug.");
                }

                Frame<BasicValue>[] frames = analyze(target);

                targetFrame = frames[target.insns.indexOf(jumpTarget.getCurrentTarget())];
                sourceFrame = frames[target.insns.indexOf(node.getCurrentTarget()) + 1];

                for (int i = targetFrame.getStackSize() - 1; i >= 0; i--) {
                    BasicValue value = targetFrame.getStack(i);

                    if (value.equals(BasicValue.UNINITIALIZED_VALUE)) {
                        throw new SugarApplicationException("Cannot jump to a target with uninitialized objects on the stack (ex. constructors). Set shiftBeforeStack to true in the Jump sugar to fix this.");
                    }
                }
            }
        }
    }

    final Map<Target, List<Pair<InjectionNodes.InjectionNode, PerTarget>>> perTarget = new HashMap<>();

    AbstractJumpSugarApplicator(SugarInjector parent, SugarParameter parameter) {
        super(parent, parameter);
    }

    PerTarget getPerTarget(Target target, InjectionNodes.InjectionNode node) {
        List<Pair<InjectionNodes.InjectionNode, PerTarget>> list = perTarget.computeIfAbsent(target, key -> new ArrayList<>());

        for (Pair<InjectionNodes.InjectionNode, PerTarget> pair : list) {
            if (pair.getKey() == node) {
                return pair.getValue();
            }
        }

        PerTarget n = new PerTarget();
        list.add(Pair.of(node, n));
        return n;
    }

    // We want to inject before Locals so mutable refs are disposed before jumps
    @Override
    int postProcessingPriority() {
        return 0;
    }

    // Initialize per target values before locals so locals can use them
    @Override
    int initPriority() {
        return 0;
    }

    @Override
    int validatePriority() {
        return 0;
    }

    @Override
    int preparePriority() {
        return 0;
    }

    protected static Frame<BasicValue>[] analyze(Target target) {
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

    @Override
    void init() {
    }

    @Override
    void validate(Target target, InjectionNodes.InjectionNode node) {
        if (!paramType.equals(JUMP_INFO_TYPE)) {
            throw new IllegalStateException("@Jump sugar has the wrong type! Expected " + JUMP_INFO_TYPE.getClassName() + " but got " + paramType.getClassName());
        }
    }

    @Override
    void prepare(Target target, InjectionNodes.InjectionNode node) {
    }

    @Override
    void inject(Target target, InjectionNodes.InjectionNode node, InjectionNodes.InjectionNode oldNode, StackExtension stack) {
        PerTarget perTarget = getPerTarget(target, oldNode);
        perTarget.ensureFramesInitialized(target, node);

        LabelNode labelJumpTarget = (LabelNode) perTarget.jumpTarget.getCurrentTarget();
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

            for (int i = perTarget.sourceFrame.getStackSize() - 1; i >= 0; i--) {
                BasicValue value = perTarget.sourceFrame.getStack(i);

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

            for (int i = 0; i < perTarget.targetFrame.getLocals(); i++) {
                BasicValue value = perTarget.targetFrame.getLocal(i);
                InsnList handler = perTarget.locals.get(i);

                if (handler != null) {
                    after.add(handler);
                } else if (value.getType() != null && !Objects.equals(perTarget.sourceFrame.getLocal(i).getType(), value.getType())) {
                    switch (value.getType().getSort()) {
                        case Type.BOOLEAN:
                        case Type.CHAR:
                        case Type.BYTE:
                        case Type.SHORT:
                        case Type.INT:
                            after.add(new InsnNode(Opcodes.ICONST_0));
                            after.add(new VarInsnNode(Opcodes.ISTORE, i));
                            break;

                        case Type.LONG:
                            after.add(new InsnNode(Opcodes.LCONST_0));
                            after.add(new VarInsnNode(Opcodes.LSTORE, i));
                            break;

                        case Type.FLOAT:
                            after.add(new InsnNode(Opcodes.FCONST_0));
                            after.add(new VarInsnNode(Opcodes.FSTORE, i));
                            break;

                        case Type.DOUBLE:
                            after.add(new InsnNode(Opcodes.DCONST_0));
                            after.add(new VarInsnNode(Opcodes.DSTORE, i));
                            break;

                        default:
                            after.add(new InsnNode(Opcodes.ACONST_NULL));
                            after.add(new VarInsnNode(Opcodes.ASTORE, i));
                            break;
                    }
                }
            }

            for (int i = 0; i < perTarget.targetFrame.getStackSize(); i++) {
                BasicValue value = perTarget.targetFrame.getStack(i);
                InsnList handler = perTarget.stack.get(i);

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

            after.add(new JumpInsnNode(Opcodes.GOTO, labelJumpTarget));
            after.add(notJumped);
            target.insns.insert(node.getCurrentTarget(), after);
        });
    }
}
