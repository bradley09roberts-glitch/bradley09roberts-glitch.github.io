package com.longnight.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;

import java.util.EnumSet;

final class CitizenGoals {
    private CitizenGoals() {}

    /** Runs the person's routine whenever they are not fighting or fleeing. */
    static final class BrainGoal extends Goal {
        private final CitizenEntity e;

        BrainGoal(CitizenEntity e) {
            this.e = e;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return e.isAlive() && !(e.isFighter() && e.getTarget() != null && e.getTarget().isAlive());
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            e.brain.tick(e);
        }
    }

    /** Non-fighters run from monsters and raiders. */
    static final class FleeGoal extends AvoidEntityGoal<LivingEntity> {
        private final CitizenEntity e;

        FleeGoal(CitizenEntity e) {
            super(e, LivingEntity.class, 8f, 0.7, 0.95, x -> threat(e, x));
            this.e = e;
        }

        static boolean threat(CitizenEntity self, LivingEntity x) {
            if (self.isFighter()) return false;
            if (x instanceof CitizenEntity c) return c.isHostile();
            return x instanceof Monster || x instanceof Creeper;
        }

        @Override
        public boolean canUse() {
            return !e.isFighter() && !e.isSleeping() && super.canUse();
        }
    }
}
