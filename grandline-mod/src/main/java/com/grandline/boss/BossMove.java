package com.grandline.boss;

import com.grandline.combat.Element;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A telegraphed boss attack: a ground marker of some {@link Shape} appears where the attack will land, the boss winds
 * up (animation + sound), then the hit resolves against whoever is still inside the marker. Signature attacks belong
 * to one boss and to some of its phases; arena attacks are fired by the arena script and do not stop the boss.
 * Names: {@code boss.grandline.move.<boss>.<name>}.
 */
public final class BossMove {
    /** Marker shapes. Sizes: CIRCLE/SELF/RAIN radius; RING outer + inner (safe) radius; CONE length + arc; LINE/CROSS length + half-width. */
    public enum Shape { CIRCLE, SELF, RING, CONE, LINE, RAIN, CROSS }

    /** Extra behaviour per victim that was hit (pulls, roots, marks). */
    @FunctionalInterface
    public interface OnHit { void apply(BossFight f, Telegraph t, LivingEntity victim); }

    /** Extra behaviour once the attack has resolved (lingering hazards, the boss relocating). */
    @FunctionalInterface
    public interface After { void apply(BossFight f, Telegraph t); }

    public record EffectSpec(Holder<MobEffect> effect, int ticks, int amplifier) {
        MobEffectInstance make() {
            return new MobEffectInstance(effect, ticks, amplifier);
        }
    }

    public final String boss, name;
    Shape shape = Shape.CIRCLE;
    double size = 4, size2 = 0;
    /** RAIN: extra random circles besides one per foe. */
    int extra = 0;
    int windup = 30, cooldown = 160;
    float power = 1.5f;
    Element element = Element.PHYSICAL;
    float knockback = 1.0f, lift = 0f;
    int stun = 0;
    boolean unblockable = true;
    /** Bit (phase - 1) set when the move belongs to that phase's move set. */
    int phases = 0b111;
    String windAnim = "focus", hitAnim = "ground_slam";
    /** Palette of the generic marker/hit effects (ClientFx "bosses" block). */
    String style = "blunt";
    /** An existing effect kind drawn at each centre on release, called with (radius, themeB, 0). */
    String theme = "";
    float themeB = 0;
    /** Inward pull per tick on foes near the marker during the wind-up (gravity wells, whirlpools). */
    double pull = 0;
    /** The boss travels along the LINE (or to the CIRCLE) as the hit lands. */
    boolean dash = false;
    /** Maximum distance to the target for the boss to choose this move. */
    double range = 24;
    /** An existing self-centred ability (e.g. "fruit/magu/magma_floor") executed at release, after the hit. */
    String reuse = "";
    final List<EffectSpec> effects = new ArrayList<>();
    @Nullable OnHit onHit;
    @Nullable After after;
    int ignite = 0;

    BossMove(String boss, String name) {
        this.boss = boss;
        this.name = name;
    }

    public String id() { return boss + "/" + name; }
    public String nameKey() { return "boss.grandline.move." + boss + "." + name; }
    public Shape shape() { return shape; }
    public double size() { return size; }
    public double size2() { return size2; }
    public int windup() { return windup; }
    public boolean inPhase(int phase) { return (phases & (1 << (phase - 1))) != 0; }
    public String reuse() { return reuse; }
    public float power() { return power; }

    // ------------------------------------------------------------------ builder

    public static BossMove m(String boss, String name) {
        return new BossMove(boss, name);
    }

    public BossMove circle(double r) { shape = Shape.CIRCLE; size = r; return this; }
    public BossMove self(double r) { shape = Shape.SELF; size = r; range = r + 3; return this; }
    public BossMove ring(double outer, double inner) { shape = Shape.RING; size = outer; size2 = inner; range = outer; return this; }
    public BossMove cone(double length, double arc) { shape = Shape.CONE; size = length; size2 = arc; range = length; return this; }
    public BossMove line(double length, double halfWidth) { shape = Shape.LINE; size = length; size2 = halfWidth; range = length; return this; }
    public BossMove rain(double r, int extra) { shape = Shape.RAIN; size = r; this.extra = extra; range = 28; return this; }
    public BossMove cross(double length, double halfWidth) { shape = Shape.CROSS; size = length; size2 = halfWidth; return this; }
    public BossMove windup(int t) { windup = t; return this; }
    public BossMove cooldown(int t) { cooldown = t; return this; }
    public BossMove power(float p) { power = p; return this; }
    public BossMove el(Element e) { element = e; return this; }
    public BossMove kb(float k) { knockback = k; return this; }
    public BossMove lift(float l) { lift = l; return this; }
    public BossMove stun(int t) { stun = t; return this; }
    public BossMove blockable() { unblockable = false; return this; }
    public BossMove phases(int... ps) {
        phases = 0;
        for (int p : ps) phases |= 1 << (p - 1);
        return this;
    }
    public BossMove anim(String wind, String hit) { windAnim = wind; hitAnim = hit; return this; }
    public BossMove style(String s) { style = s; return this; }
    public BossMove theme(String kind) { theme = kind; return this; }
    public BossMove theme(String kind, float b) { theme = kind; themeB = b; return this; }
    public BossMove pull(double p) { pull = p; return this; }
    public BossMove dash() { dash = true; return this; }
    public BossMove range(double r) { range = r; return this; }
    public BossMove reuse(String abilityId) { reuse = abilityId; return this; }
    public BossMove effect(Holder<MobEffect> e, int ticks, int amp) { effects.add(new EffectSpec(e, ticks, amp)); return this; }
    public BossMove ignite(int seconds) { ignite = seconds; return this; }
    public BossMove onHit(OnHit h) { onHit = h; return this; }
    public BossMove after(After a) { after = a; return this; }
}
