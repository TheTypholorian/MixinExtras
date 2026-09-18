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

    BreakLoopSugarApplicator(InjectionInfo info, SugarParameter parameter) {
        super(info, parameter);
    }

    @Override
    void validate(Target target, InjectionNodes.InjectionNode node) {
        if (!paramType.equals(JUMP_INFO_TYPE)) {
            throw new IllegalStateException("@BreakLoop sugar has the wrong type! Expected " + JUMP_INFO_TYPE.getClassName() + " but got " + paramType.getClassName());
        }
    }

    @Override
    void prepare(Target target, InjectionNodes.InjectionNode node) {
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

        int depth = Annotations.getValue(sugar, "depth", -1);
        int targetIndex = target.insns.indexOf(node.getCurrentTarget());
        List<Loop> loopStack = new ArrayList<>();

        for (Loop loop : loops) {
            if (target.insns.indexOf(loop.startLabel) <= targetIndex && target.insns.indexOf(loop.endLabel) >= targetIndex) {
                loopStack.add(loop);
            }
        }

        Loop loop = depth == -1 ? loopStack.getLast() : loopStack.get(depth);
        jumpTarget = loop.endLabel;
        loadFrames(analyze(target), target, node);
    }
}
