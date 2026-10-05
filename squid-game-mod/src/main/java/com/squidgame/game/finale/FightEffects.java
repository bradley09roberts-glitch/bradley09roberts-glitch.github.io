package com.squidgame.game.finale;

import com.squidgame.core.finale.ActionKind;
import com.squidgame.core.finale.CombatEvent;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.game.GameContext;
import com.squidgame.registry.ModSounds;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the fight shows and plays: arm swings, animations of the NPC bodies, impact sounds and dust, sparks
 * on a guard, the camera tilt and red edge of a player who is hit. Hits are meant to have weight (a heavy blow
 * thumps and kicks up dust, a shove sends a puff of sand), never to look gory: only sparks, dust and stars.
 */
final class FightEffects {
    /** Positional sounds are louder than usual so that the audience on the far gallery hears the fight. */
    private static final float LOUD = 2.2f;

    private final GameContext ctx;
    private final ServerLevel level;
    private final int[] punchSide = new int[2];

    FightEffects(GameContext ctx) {
        this.ctx = ctx;
        this.level = ctx.level;
    }

    /**
     * Plays one event. {@code bodies} are indexed by the fighter's slot in the duel. The Activity changes of NPC bodies
     * are what the headless replay of a ceremony needs; in a live duel {@code FightSession} also syncs them every tick
     * (to the same values).
     */
    void play(CombatEvent e, FightBody[] bodies) {
        FightBody actor = bodies[e.actor()];
        FightBody target = e.target() >= 0 ? bodies[e.target()] : null;
        if (actor == null) {
            return;
        }
        switch (e.type()) {
            case SWING -> swing(e, actor);
            case CHARGE_START -> {
                sound(actor, ModSounds.UI_SELECT, 0.8f, 0.6f);
                if (actor.entity() instanceof ContestantEntity npc) {
                    npc.setActivity(Activity.MARBLE_WINDUP);
                }
            }
            case GUARD_UP -> {
                sparks(actor, 3);
                if (actor.entity() instanceof ContestantEntity npc) {
                    npc.setActivity(Activity.BLOCK);
                }
            }
            case GUARD_DOWN -> {
                if (actor.entity() instanceof ContestantEntity npc && npc.getActivity() == Activity.BLOCK) {
                    npc.setActivity(Activity.FIGHT_STANCE);
                }
            }
            case DODGE -> dodge(e, actor);
            case HIT -> hit(e, actor, target);
            case BLOCKED -> {
                if (target != null) {
                    sound(target, SoundEvents.SHIELD_BLOCK, 1.0f, 0.95f + level.random.nextFloat() * 0.25f);
                    sparks(target, 9);
                }
            }
            case PARRIED -> {
                if (target != null) {
                    sound(target, SoundEvents.SHIELD_BLOCK, 1.1f, 1.7f);
                    sound(target, ModSounds.UI_CONFIRM, 0.8f, 1.5f);
                    particles(ParticleTypes.END_ROD, chest(target.entity()), 10, 0.3, 0.02);
                    particles(ParticleTypes.ELECTRIC_SPARK, chest(target.entity()), 10, 0.3, 0.3);
                }
                trigger(actor, "stumble");
                hurtAnimation(actor);
            }
            case GUARD_BREAK -> {
                if (target != null) {
                    sound(target, SoundEvents.SHIELD_BREAK, 1.1f, 1.0f);
                    sound(target, ModSounds.DANGER_STING, 0.5f, 1.3f);
                    particles(ParticleTypes.CRIT, chest(target.entity()), 18, 0.35, 0.4);
                    particles(ParticleTypes.POOF, feet(target.entity()), 8, 0.3, 0.03);
                    trigger(target, "stumble");
                    hurtAnimation(target);
                    danger(target, 0.55f, 16);
                }
            }
            case DODGED -> {
                if (target != null) {
                    sound(target, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.9f);
                }
            }
            case WHIFF -> sound(actor, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, e.kind() == ActionKind.HEAVY ? 0.7f : 1.1f);
            case DENIED -> {
                if (actor instanceof PlayerBody pb) {
                    pb.player().playNotifySound(ModSounds.UI_DENY, SoundSource.MASTER, 0.5f, 1.2f);
                }
            }
            case EXHAUSTED -> {
                if (actor instanceof PlayerBody pb) {
                    pb.player().playNotifySound(ModSounds.UI_DENY, SoundSource.MASTER, 0.7f, 0.7f);
                } else if (actor.entity() instanceof ContestantEntity npc) {
                    particles(ParticleTypes.CLOUD, feet(npc), 4, 0.25, 0.01);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ pieces

    private void swing(CombatEvent e, FightBody actor) {
        LivingEntity a = actor.entity();
        Vec3 front = chest(a).add(forward(a).scale(0.9));
        switch (e.kind()) {
            case HEAVY -> {
                sound(actor, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.65f);
                particles(ParticleTypes.SWEEP_ATTACK, front, 1, 0, 0);
                trigger(actor, "punch_right");
                swingArm(a, InteractionHand.MAIN_HAND);
            }
            case SHOVE -> {
                sound(actor, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8f, 0.85f);
                trigger(actor, "shove");
                swingArm(a, InteractionHand.OFF_HAND);
            }
            default -> {
                sound(actor, SoundEvents.PLAYER_ATTACK_SWEEP, 0.55f, 1.2f + level.random.nextFloat() * 0.2f);
                int side = punchSide[e.actor()]++ & 1;
                trigger(actor, side == 0 ? "punch_left" : "punch_right");
                swingArm(a, InteractionHand.MAIN_HAND);
            }
        }
        if (actor.entity() instanceof ContestantEntity npc && npc.getActivity() == Activity.MARBLE_WINDUP) {
            npc.setActivity(Activity.FIGHT_STANCE);
        }
    }

    private void dodge(CombatEvent e, FightBody actor) {
        LivingEntity a = actor.entity();
        sound(actor, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.7f);
        particles(ParticleTypes.CLOUD, feet(a), 6, 0.25, 0.02);
        double yaw = Math.toRadians(a.getYRot());
        double right = e.ix() * -Math.cos(yaw) + e.iz() * -Math.sin(yaw);
        trigger(actor, right >= 0 ? "dodge_right" : "dodge_left");
    }

    private void hit(CombatEvent e, FightBody actor, FightBody target) {
        if (target == null) {
            return;
        }
        LivingEntity t = target.entity();
        Vec3 c = chest(t);
        switch (e.kind()) {
            case HEAVY -> {
                sound(target, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.3f, 0.8f);
                sound(target, ModSounds.ELIMINATION_BODY_FALL, 0.7f, 1.5f);
                particles(ParticleTypes.CRIT, c, 16, 0.35, 0.45);
                particles(ParticleTypes.SWEEP_ATTACK, c, 1, 0, 0);
                particles(ParticleTypes.POOF, feet(t), 7, 0.3, 0.04);
                danger(target, 0.5f, 16);
            }
            case SHOVE -> {
                sound(target, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.15f);
                sound(target, ModSounds.ELIMINATION_BODY_FALL, 0.8f, 1.2f);
                particles(ParticleTypes.POOF, feet(t), 10, 0.35, 0.06);
                particles(ParticleTypes.CLOUD, c, 4, 0.3, 0.05);
                danger(target, 0.3f, 10);
            }
            default -> {
                sound(target, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.95f + level.random.nextFloat() * 0.2f);
                particles(ParticleTypes.CRIT, c, 7, 0.3, 0.35);
                danger(target, 0.22f, 8);
            }
        }
        trigger(target, "knocked_back");
        hurtAnimation(target);
    }

    // ------------------------------------------------------------------ helpers

    private void trigger(FightBody body, String animation) {
        if (body.entity() instanceof ContestantEntity npc) {
            npc.triggerAction(animation);
        }
    }

    private void swingArm(LivingEntity entity, InteractionHand hand) {
        if (entity instanceof ServerPlayer p) {
            p.swing(hand, true);
        }
    }

    /** The vanilla camera tilt and red flash of the body that was hit. */
    private void hurtAnimation(FightBody body) {
        if (body instanceof PlayerBody pb) {
            pb.player().connection.send(new ClientboundHurtAnimationPacket(pb.player()));
        }
    }

    private void danger(FightBody body, float intensity, int ticks) {
        if (body instanceof PlayerBody pb) {
            ctx.danger(pb.player(), intensity, ticks, 0, 0xFFB01818);
        }
    }

    private void sparks(FightBody body, int count) {
        particles(ParticleTypes.ELECTRIC_SPARK, chest(body.entity()).add(forward(body.entity()).scale(0.5)), count, 0.3, 0.2);
    }

    private void sound(FightBody body, SoundEvent s, float volume, float pitch) {
        Vec3 p = body.entity().position();
        level.playSound(null, p.x, p.y + 1.0, p.z, s, SoundSource.PLAYERS, volume * LOUD, pitch);
    }

    private void particles(ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(type, at.x, at.y, at.z, count, spread, spread * 0.6, spread, speed);
    }

    private static Vec3 chest(LivingEntity e) {
        return new Vec3(e.getX(), e.getY() + 1.1, e.getZ());
    }

    private static Vec3 feet(LivingEntity e) {
        return new Vec3(e.getX(), e.getY() + 0.15, e.getZ());
    }

    private static Vec3 forward(LivingEntity e) {
        double yaw = Math.toRadians(e.getYRot());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }
}
