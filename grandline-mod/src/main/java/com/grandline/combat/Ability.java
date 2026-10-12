package com.grandline.combat;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/**
 * A usable technique: weapon art, fighting-style move, Haki technique or Devil Fruit power.
 * The executor runs on the server only and returns false if the ability could not be used (no stamina is spent).
 */
public record Ability(String id, Source source, String owner, int unlockMastery, int cooldown, float stamina, float haki,
                      String anim, Executor exec, boolean awakened) {

    public enum Source { WEAPON, STYLE, FRUIT, HAKI, RACE }

    @FunctionalInterface
    public interface Executor {
        boolean run(LivingEntity user, AbilityContext ctx);
    }

    public String translationKey() {
        return "ability.grandline." + id.replace('/', '.');
    }

    public Component name() {
        return Component.translatable(translationKey());
    }

    /** Mastery key this ability trains, e.g. "fruit/mera". */
    public String masteryKey() {
        return switch (source) {
            case WEAPON -> "weapon/" + owner;
            case STYLE -> "style/" + owner;
            case FRUIT -> "fruit/" + owner;
            case HAKI -> "haki/" + owner;
            case RACE -> "race/" + owner;
        };
    }

    public static Builder builder(String id, Source source, String owner) {
        return new Builder(id, source, owner);
    }

    public static final class Builder {
        private final String id, owner;
        private final Source source;
        private int unlock = 0, cooldown = 20;
        private float stamina = 10, haki = 0;
        private String anim = "";
        private boolean awakened = false;

        Builder(String id, Source source, String owner) {
            this.id = id;
            this.source = source;
            this.owner = owner;
        }

        public Builder unlock(int mastery) { this.unlock = mastery; return this; }
        public Builder cooldown(int ticks) { this.cooldown = ticks; return this; }
        public Builder stamina(float s) { this.stamina = s; return this; }
        public Builder haki(float h) { this.haki = h; return this; }
        public Builder anim(String a) { this.anim = a; return this; }
        public Builder awakened() { this.awakened = true; return this; }

        public Ability exec(Executor e) {
            Ability a = new Ability(id, source, owner, unlock, cooldown, stamina, haki, anim, e, awakened);
            Abilities.register(a);
            return a;
        }
    }
}
