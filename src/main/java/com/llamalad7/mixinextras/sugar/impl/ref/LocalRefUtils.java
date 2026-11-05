package com.llamalad7.mixinextras.sugar.impl.ref;

import com.llamalad7.mixinextras.service.MixinExtrasService;
import com.llamalad7.mixinextras.sugar.ref.*;
import com.llamalad7.mixinextras.sugar.ref.jump.*;
import com.llamalad7.mixinextras.utils.ASMUtils;
import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

public class LocalRefUtils {
    public static Class<?> getInterfaceFor(Type type) {
        if (!ASMUtils.isPrimitive(type)) {
            return LocalRef.class;
        }
        switch (type.getDescriptor().charAt(0)) {
            case 'Z': return LocalBooleanRef.class;
            case 'B': return LocalByteRef.class;
            case 'C': return LocalCharRef.class;
            case 'S': return LocalShortRef.class;
            case 'I': return LocalIntRef.class;
            case 'J': return LocalLongRef.class;
            case 'F': return LocalFloatRef.class;
            case 'D': return LocalDoubleRef.class;
        }
        throw new IllegalStateException("Illegal descriptor " + type.getDescriptor());
    }

    public static Class<?> getJumpInterfaceFor(Type type) {
        if (!ASMUtils.isPrimitive(type)) {
            return StackRef.class;
        }
        switch (type.getDescriptor().charAt(0)) {
            case 'Z': return BooleanStackRef.class;
            case 'B': return ByteStackRef.class;
            case 'C': return CharStackRef.class;
            case 'S': return ShortStackRef.class;
            case 'I': return IntStackRef.class;
            case 'J': return LongStackRef.class;
            case 'F': return FloatStackRef.class;
            case 'D': return DoubleStackRef.class;
        }
        throw new IllegalStateException("Illegal descriptor " + type.getDescriptor());
    }

    public static Type getTargetType(Type type, Type generic) {
        if (type.getSort() != Type.OBJECT || !MixinExtrasService.getInstance().isClassOwned(type.getClassName())) {
            return type;
        }
        switch (StringUtils.substringAfterLast(type.getInternalName(), "/")) {
            case "LocalBooleanRef":
            case "BooleanStackRef":
                return Type.BOOLEAN_TYPE;
            case "LocalByteRef":
            case "ByteStackRef":
                return Type.BYTE_TYPE;
            case "LocalCharRef":
            case "CharStackRef":
                return Type.CHAR_TYPE;
            case "LocalDoubleRef":
            case "DoubleStackRef":
                return Type.DOUBLE_TYPE;
            case "LocalFloatRef":
            case "FloatStackRef":
                return Type.FLOAT_TYPE;
            case "LocalIntRef":
            case "IntStackRef":
                return Type.INT_TYPE;
            case "LocalLongRef":
            case "LongStackRef":
                return Type.LONG_TYPE;
            case "LocalShortRef":
            case "ShortStackRef":
                return Type.SHORT_TYPE;
            case "LocalRef":
            case "StackRef":
                if (generic == null) {
                    throw new IllegalStateException("LocalRef must have a concrete type argument!");
                }
                return generic;
            default: return type;
        }
    }

    public static boolean isStackRef(Type type) {
        if (type.getSort() != Type.OBJECT || !MixinExtrasService.getInstance().isClassOwned(type.getClassName())) {
            return false;
        }

        return StringUtils.substringAfterLast(type.getInternalName(), "/").endsWith("StackRef");
    }

    public static void generateNew(InsnList insns, String refImpl) {
        insns.add(new TypeInsnNode(Opcodes.NEW, refImpl));
        insns.add(new InsnNode(Opcodes.DUP));
        insns.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                refImpl,
                "<init>",
                "()V",
                false
        ));
    }

    public static void generateInitialization(InsnList insns, Type innerType, String refImpl) {
        insns.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                refImpl,
                "init",
                Type.getMethodDescriptor(Type.VOID_TYPE, getErasedType(innerType)),
                false
        ));
    }

    public static void generateDisposal(InsnList insns, Type innerType, String refImpl) {
        insns.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                refImpl,
                "dispose",
                Type.getMethodDescriptor(getErasedType(innerType)),
                false
        ));
        if (!ASMUtils.isPrimitive(innerType)) {
            insns.add(new TypeInsnNode(Opcodes.CHECKCAST, innerType.getInternalName()));
        }
    }

    public static void generateUnwrapping(InsnList insns, Type innerType, Runnable load) {
        String refInterface = Type.getInternalName(getInterfaceFor(innerType));

        load.run();
        insns.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                refInterface,
                "get",
                Type.getMethodDescriptor(getErasedType(innerType)),
                true
        ));
        if (!ASMUtils.isPrimitive(innerType)) {
            insns.add(new TypeInsnNode(Opcodes.CHECKCAST, innerType.getInternalName()));
        }
    }

    private static Type getErasedType(Type actual) {
        return ASMUtils.isPrimitive(actual) ? actual : ASMUtils.OBJECT_TYPE;
    }
}
