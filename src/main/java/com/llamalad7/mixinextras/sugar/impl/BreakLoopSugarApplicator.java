package com.llamalad7.mixinextras.sugar.impl;

import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.util.Annotations;

import java.util.ArrayList;
import java.util.List;

class BreakLoopSugarApplicator extends AbstractJumpSugarApplicator {
    private final static class Loop {
        public final LabelNode startLabel;
        public LabelNode endLabel;

        private Loop(LabelNode startLabel) {
            this.startLabel = startLabel;
        }
    }

    BreakLoopSugarApplicator(SugarInjector parent, SugarParameter parameter) {
        super(parent, parameter);
    }

    @Override
    void validate(Target target, InjectionNodes.InjectionNode node) {
        super.validate(target, node);

        // done in validate since jump locals/stack values need jumpTarget for their validation
        List<LabelNode> passedLabels = new ArrayList<>();
        List<Loop> loops = new ArrayList<>();

        target.insns.iterator().forEachRemaining(insn -> {
            if (insn instanceof LabelNode) {
                LabelNode label = (LabelNode) insn;
                passedLabels.add(label);

                for (Loop loop : loops) {
                    if (loop.endLabel == null) {
                        loop.endLabel = label;
                    }
                }
            } else if (insn instanceof JumpInsnNode) {
                JumpInsnNode jump = (JumpInsnNode) insn;

                if (passedLabels.contains(jump.label)) {
                    for (Loop loop : loops) {
                        if (loop.startLabel.equals(jump.label)) {
                            loop.endLabel = null;
                            return;
                        }
                    }

                    loops.add(new Loop(jump.label));
                }
            }
        });

        int depth = Annotations.getValue(sugar, "depth", 0);
        int targetIndex = target.insns.indexOf(node.getCurrentTarget());
        List<Loop> loopStack = new ArrayList<>();

        for (Loop loop : loops) {
            if (target.insns.indexOf(loop.startLabel) <= targetIndex && target.insns.indexOf(loop.endLabel) >= targetIndex) {
                loopStack.add(loop);
            }
        }

        Loop loop = loopStack.get(loopStack.size() - 1 - depth);
        getPerTarget(target, node).setJumpTarget(loop.endLabel, target);
    }
}
