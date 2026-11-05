package com.llamalad7.mixinextras.sugar.impl;

import com.llamalad7.mixinextras.utils.CompatibilityHelper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.util.Annotations;

import java.util.ArrayList;
import java.util.List;

class JumpSugarApplicator extends AbstractJumpSugarApplicator {
    JumpSugarApplicator(SugarInjector parent, SugarParameter parameter) {
        super(parent, parameter);
    }

    @Override
    void validate(Target target, InjectionNodes.InjectionNode node) {
        super.validate(target, node);

        // done in validate since jump locals/stack values need jumpTarget for their validation
        InjectionPoint injectionPoint = InjectionPoint.parse(CompatibilityHelper.getMixin(info), target.method, sugar, Annotations.<AnnotationNode>getValue(sugar, "value"));
        List<AbstractInsnNode> targets = new ArrayList<>();
        injectionPoint.find(target.method.desc, target.method.instructions, targets);

        if (targets.size() != 1) {
            throw new IllegalStateException("@Jump must specify exactly one target, got " + targets.size());
        }

        AbstractInsnNode targetNode = targets.get(0);

        if (Annotations.getValue(sugar, "shiftBeforeStack", Boolean.FALSE)) {
            Frame<BasicValue>[] frames = analyze(target);
            int index = target.method.instructions.indexOf(targetNode);

            while (frames[index].getStackSize() > 0) {
                index--;
                targetNode = targetNode.getPrevious();
            }
        }

        getPerTarget(target, node).setJumpTarget(targetNode, target);
    }
}
