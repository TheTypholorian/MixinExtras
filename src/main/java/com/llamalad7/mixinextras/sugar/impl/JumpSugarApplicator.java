package com.llamalad7.mixinextras.sugar.impl;

import com.llamalad7.mixinextras.utils.CompatibilityHelper;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.util.Annotations;

import java.util.ArrayList;
import java.util.List;

class JumpSugarApplicator extends AbstractJumpSugarApplicator {
    JumpSugarApplicator(InjectionInfo info, SugarParameter parameter) {
        super(info, parameter);
    }

    @Override
    void validate(Target target, InjectionNodes.InjectionNode node) {
        if (!(paramType.equals(JUMP_INFO_TYPE) || paramType.equals(JUMP_INFO_COMPLEX_TYPE))) {
            throw new IllegalStateException("@Jump sugar has the wrong type! Expected " + JUMP_INFO_TYPE.getClassName() + " or " + JUMP_INFO_COMPLEX_TYPE.getClassName() + " but got " + paramType.getClassName());
        }
    }

    @Override
    void prepare(Target target, InjectionNodes.InjectionNode node) {
        InjectionPoint injectionPoint = InjectionPoint.parse(CompatibilityHelper.getMixin(info), target.method, sugar, Annotations.<AnnotationNode>getValue(sugar, "value"));
        List<AbstractInsnNode> targets = new ArrayList<>();
        injectionPoint.find(target.method.desc, target.method.instructions, targets);

        if (targets.size() != 1) {
            throw new IllegalStateException("@Jump must specify exactly one target, got " + targets.size());
        }

        AbstractInsnNode targetNode = targets.get(0);
        List<AnnotationNode> localsToModify = Annotations.getValue(sugar, "localsToModify", false);

        for (AnnotationNode local : localsToModify) {
            this.localsToModify.add(new Local(Annotations.getValue(local, "type", Type.VOID_TYPE), LocalVariableDiscriminator.parse(local)));
        }

        Frame<BasicValue>[] frames = analyze(target);

        if (Annotations.getValue(sugar, "shiftBeforeStack", Boolean.FALSE)) {
            int index = target.method.instructions.indexOf(targetNode);

            while (frames[index].getStackSize() > 0) {
                index--;
                targetNode = targetNode.getPrevious();
            }
        }

        jumpTarget = targetNode;
        loadFrames(frames, target, node);
    }
}
