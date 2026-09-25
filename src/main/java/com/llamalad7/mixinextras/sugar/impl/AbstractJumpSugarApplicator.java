package com.llamalad7.mixinextras.sugar.impl;

import com.llamalad7.mixinextras.injector.StackExtension;
import com.llamalad7.mixinextras.sugar.impl.jump.JumpInfoComplexImpl;
import com.llamalad7.mixinextras.sugar.impl.jump.JumpInfoImpl;
import com.llamalad7.mixinextras.sugar.jump.JumpInfo;
import com.llamalad7.mixinextras.sugar.jump.JumpInfoComplex;
import com.llamalad7.mixinextras.utils.CompatibilityHelper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.util.asm.ASM;
import org.spongepowered.asm.util.asm.MixinVerifier;

import java.util.*;

abstract class AbstractJumpSugarApplicator extends SugarApplicator {
    public static final Type JUMP_INFO_TYPE = Type.getType(JumpInfo.class);
    public static final Type JUMP_INFO_IMPL_TYPE = Type.getType(JumpInfoImpl.class);
    public static final Type JUMP_INFO_COMPLEX_TYPE = Type.getType(JumpInfoComplex.class);
    public static final Type JUMP_INFO_COMPLEX_IMPL_TYPE = Type.getType(JumpInfoComplexImpl.class);

    protected final static class Local {
        public final Type type;
        public final LocalVariableDiscriminator discriminator;

        protected Local(Type type, LocalVariableDiscriminator discriminator) {
            this.type = type;
            this.discriminator = discriminator;
        }
    }

    protected final static class LocalContextKey {
        public final Type type;
        public final boolean argsOnly;

        protected LocalContextKey(Type type, boolean argsOnly) {
            this.type = type;
            this.argsOnly = argsOnly;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            LocalContextKey that = (LocalContextKey) o;
            return argsOnly == that.argsOnly && Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, argsOnly);
        }
    }

    protected AbstractInsnNode jumpTarget;
    protected final List<Local> localsToModify = new ArrayList<>();
    protected Frame<BasicValue> sourceFrame, targetFrame;

    AbstractJumpSugarApplicator(InjectionInfo info, SugarParameter parameter) {
        super(info, parameter);
    }

    @Override
    int postProcessingPriority() {
        return 1000;
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
        }
    }

    protected void loadFrames(Frame<BasicValue>[] frames, Target target, InjectionNodes.InjectionNode node) {
        sourceFrame = frames[target.insns.indexOf(node.getCurrentTarget())];
        targetFrame = frames[target.insns.indexOf(jumpTarget)];
    }

    @Override
    void inject(Target target, InjectionNodes.InjectionNode node, StackExtension stack) {
        boolean complex = paramType.equals(JUMP_INFO_COMPLEX_TYPE);
        Type jumpInfoImplType = complex ? JUMP_INFO_COMPLEX_IMPL_TYPE : JUMP_INFO_IMPL_TYPE;

        if (!localsToModify.isEmpty() && !complex) {
            throw new IllegalStateException("Specified locals to modify in jump annotation but doesn't take a complex jump info");
        }

        if (targetFrame.getStackSize() != 0 && !complex) {
            throw new IllegalStateException("Jump target has non-empty stack but no stack handling is set. Add 'shiftBeforeStack = true' in your @Jump annotation, or change the sugar type to JumpHandleComplex and set stack values through it.");
        }

        if (!(jumpTarget instanceof LabelNode)) {
            LabelNode label = new LabelNode();
            target.method.instructions.insertBefore(jumpTarget, label);
            jumpTarget = label;
        }

        LabelNode jumpTarget = (LabelNode) this.jumpTarget;
        int jumpInfoVar = target.allocateLocal();
        target.addLocalVariable(jumpInfoVar, "jumpInfo" + jumpInfoVar, jumpInfoImplType.getDescriptor());

        stack.extra(2);
        InsnList before = new InsnList();
        before.add(new TypeInsnNode(Opcodes.NEW, jumpInfoImplType.getInternalName()));
        before.add(new InsnNode(Opcodes.DUP));

        if (complex) {
            before.add(new LdcInsnNode(localsToModify.size()));
            before.add(new LdcInsnNode(targetFrame.getStackSize()));
            before.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    jumpInfoImplType.getInternalName(),
                    "<init>",
                    "(II)V",
                    false
            ));
        } else {
            before.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    jumpInfoImplType.getInternalName(),
                    "<init>",
                    "()V",
                    false
            ));
        }

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
                    jumpInfoImplType.getInternalName(),
                    "hasJumped",
                    "()Z",
                    false
            ));
            after.add(new JumpInsnNode(Opcodes.IFEQ, notJumped));

            for (int i = sourceFrame.getStackSize() - 1; i >= 0; i--) {
                BasicValue value = sourceFrame.getStack(i);

                if (!value.equals(BasicValue.UNINITIALIZED_VALUE)) {
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
            }

            if (complex) {
                if (!localsToModify.isEmpty()) {
                    Map<LocalContextKey, LocalVariableDiscriminator.Context> contexts = new HashMap<>();
                    int id = 0;
                    Set<Integer> indices = new HashSet<>();

                    for (Local local : localsToModify) {
                        LocalVariableDiscriminator.Context context = contexts.computeIfAbsent(new LocalContextKey(local.type, local.discriminator.isArgsOnly()), key -> CompatibilityHelper.makeLvtContext(info, key.type, key.argsOnly, target, jumpTarget));
                        int localVar = local.discriminator.findLocal(context);

                        if (!indices.add(localVar)) {
                            throw new IllegalStateException("Specified the same local to modify more than once in jump");
                        }

                        stack.extra(1);
                        after.add(new VarInsnNode(Opcodes.ALOAD, jumpInfoVar));
                        after.add(new LdcInsnNode(id++));

                        switch (local.type.getSort()) {
                            case Type.BOOLEAN:
                            case Type.BYTE:
                            case Type.CHAR:
                            case Type.SHORT:
                            case Type.INT:
                                after.add(new MethodInsnNode(
                                        Opcodes.INVOKEVIRTUAL,
                                        jumpInfoImplType.getInternalName(),
                                        "localInt",
                                        "(I)I",
                                        false
                                ));
                                after.add(new VarInsnNode(Opcodes.ISTORE, localVar));
                                break;
                            case Type.LONG:
                                after.add(new MethodInsnNode(
                                        Opcodes.INVOKEVIRTUAL,
                                        jumpInfoImplType.getInternalName(),
                                        "localLong",
                                        "(I)J",
                                        false
                                ));
                                after.add(new VarInsnNode(Opcodes.LSTORE, localVar));
                                break;
                            case Type.FLOAT:
                                after.add(new MethodInsnNode(
                                        Opcodes.INVOKEVIRTUAL,
                                        jumpInfoImplType.getInternalName(),
                                        "localFloat",
                                        "(I)F",
                                        false
                                ));
                                after.add(new VarInsnNode(Opcodes.FSTORE, localVar));
                                break;
                            case Type.DOUBLE:
                                after.add(new MethodInsnNode(
                                        Opcodes.INVOKEVIRTUAL,
                                        jumpInfoImplType.getInternalName(),
                                        "localDouble",
                                        "(I)D",
                                        false
                                ));
                                after.add(new VarInsnNode(Opcodes.DSTORE, localVar));
                                break;
                            default:
                                after.add(new MethodInsnNode(
                                        Opcodes.INVOKEVIRTUAL,
                                        jumpInfoImplType.getInternalName(),
                                        "localObject",
                                        "(I)Ljava/lang/Object;",
                                        false
                                ));
                                after.add(new TypeInsnNode(Opcodes.CHECKCAST, local.type.getInternalName()));
                                after.add(new VarInsnNode(Opcodes.ASTORE, localVar));
                                break;
                        }
                    }
                }

                for (int i = 0; i < targetFrame.getStackSize(); i++) {
                    BasicValue expected = targetFrame.getStack(i);

                    stack.extra(1);
                    after.add(new VarInsnNode(Opcodes.ALOAD, jumpInfoVar));
                    after.add(new LdcInsnNode(i));

                    switch (expected.getType().getSort()) {
                        case Type.BOOLEAN:
                        case Type.BYTE:
                        case Type.CHAR:
                        case Type.SHORT:
                        case Type.INT:
                            after.add(new MethodInsnNode(
                                    Opcodes.INVOKEVIRTUAL,
                                    jumpInfoImplType.getInternalName(),
                                    "stackInt",
                                    "(I)I",
                                    false
                            ));
                            break;
                        case Type.LONG:
                            after.add(new MethodInsnNode(
                                    Opcodes.INVOKEVIRTUAL,
                                    jumpInfoImplType.getInternalName(),
                                    "stackLong",
                                    "(I)J",
                                    false
                            ));
                            break;
                        case Type.FLOAT:
                            after.add(new MethodInsnNode(
                                    Opcodes.INVOKEVIRTUAL,
                                    jumpInfoImplType.getInternalName(),
                                    "stackFloat",
                                    "(I)F",
                                    false
                            ));
                            break;
                        case Type.DOUBLE:
                            after.add(new MethodInsnNode(
                                    Opcodes.INVOKEVIRTUAL,
                                    jumpInfoImplType.getInternalName(),
                                    "stackDouble",
                                    "(I)D",
                                    false
                            ));
                            break;
                        default:
                            after.add(new MethodInsnNode(
                                    Opcodes.INVOKEVIRTUAL,
                                    jumpInfoImplType.getInternalName(),
                                    "stackObject",
                                    "(I)Ljava/lang/Object;",
                                    false
                            ));
                            after.add(new TypeInsnNode(Opcodes.CHECKCAST, expected.getType().getInternalName()));
                            break;
                    }
                }
            }

            after.add(new JumpInsnNode(Opcodes.GOTO, jumpTarget));
            after.add(notJumped);
            target.insns.insert(node.getCurrentTarget(), after);
        });
    }
}
