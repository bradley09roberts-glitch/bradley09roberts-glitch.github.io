package com.grandline.combat;

/**
 * One step of a melee combo. Timing is in ticks: wind-up before the hit lands, recovery before the next input.
 * {@code arc} is the horizontal sweep angle in degrees.
 */
public record Strike(float damage, double range, double arc, float knockback, int windup, int recovery, float stamina,
                     float poise, Element element, String anim, double lunge) {

    public static Strike of(float damage, double range, double arc, float knockback, int windup, int recovery, String anim) {
        return new Strike(damage, range, arc, knockback, windup, recovery, 4 + damage * 2, 10 + damage * 6, Element.PHYSICAL, anim, 0.0);
    }

    public Strike element(Element e) {
        return new Strike(damage, range, arc, knockback, windup, recovery, stamina, poise, e, anim, lunge);
    }

    public Strike lunge(double l) {
        return new Strike(damage, range, arc, knockback, windup, recovery, stamina, poise, element, anim, l);
    }

    public Strike stamina(float s) {
        return new Strike(damage, range, arc, knockback, windup, recovery, s, poise, element, anim, lunge);
    }

    public Strike poise(float p) {
        return new Strike(damage, range, arc, knockback, windup, recovery, stamina, p, element, anim, lunge);
    }
}
