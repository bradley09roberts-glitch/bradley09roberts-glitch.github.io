package com.grandline.boss;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.fruit.Zoan;
import com.grandline.fruit.impl.FruitKit;
import com.grandline.fruit.impl.PowerKit;
import com.grandline.net.GLNetwork;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

import static com.grandline.boss.BossMove.m;
import static com.grandline.combat.Element.*;

/**
 * The boss roster: the Four Emperors, the Fleet Admiral and the Three Compass Admirals, the Seven Warlords, the Five
 * Stewards of the Pale Throne and the final-arc villains (docs/STORY.md §7). Each has three signature attacks spread
 * over its three phases (the second phase swaps one of them for a stronger one, the third uses all three), the
 * existing fruit powers it calls on when a phase begins, an arena script and its rewards.
 */
public final class Bosses {
    private static final Map<String, BossDef> ALL = new LinkedHashMap<>();
    /** Arena events and phase moves per boss (not part of the signature move sets; run by the self-test too). */
    private static final Map<String, List<BossMove>> EXTRA = new LinkedHashMap<>();

    @Nullable
    public static BossDef get(String notableId) {
        return ALL.get(notableId);
    }

    public static Collection<BossDef> all() {
        return Collections.unmodifiableCollection(ALL.values());
    }

    public static List<BossMove> extraMoves(String boss) {
        return EXTRA.getOrDefault(boss, List.of());
    }

    private static BossDef def(String id) {
        return new BossDef(id);
    }

    private static void add(BossDef d) {
        if (ALL.put(d.id, d) != null) throw new IllegalStateException("duplicate boss " + d.id);
    }

    private static BossMove extra(BossMove mv) {
        EXTRA.computeIfAbsent(mv.boss, k -> new ArrayList<>()).add(mv);
        return mv;
    }

    // ------------------------------------------------------------------ arena building blocks

    /** Every {@code s2} seconds in phase 2 and {@code s3} in phase 3, do something. */
    private static BossDef.Arena every(int s2, int s3, BiConsumer<BossFight, Long> action) {
        return every(s2, s3, action, null);
    }

    private static BossDef.Arena every(int s2, int s3, BiConsumer<BossFight, Long> action, @Nullable BiConsumer<BossFight, Integer> onPhase) {
        return new BossDef.Arena() {
            @Override
            public void onPhase(BossFight f, int phase) {
                if (onPhase != null && phase >= 2) onPhase.accept(f, phase);
            }

            @Override
            public void tick(BossFight f, long now) {
                if (f.phase < 2) return;
                int s = f.phase >= 3 ? s3 : s2;
                long sec = (now - f.start) / 20;
                if (s > 0 && sec > 0 && sec % s == 0) action.accept(f, now);
            }
        };
    }

    /** An arena strike at a foe's feet (lightning, light pillars, falling stars, tentacles...). */
    private static BiConsumer<BossFight, Long> strikeFoes(BossMove mv, int perPhase3) {
        return (f, now) -> {
            int n = f.phase >= 3 ? perPhase3 : 1;
            for (int i = 0; i < n; i++) BossArena.event(f, mv, BossArena.nearFoe(f, i == 0 ? 0 : 3));
        };
    }

    private static void zoan(BossFight f, String form) {
        if (com.grandline.fruit.FruitRules.fruitOf(f.boss) != null && com.grandline.fruit.FruitRules.fruitOf(f.boss).type.isZoan()) Zoan.setForm(f.boss, form);
    }

    /** Conqueror's Haki infusion: black lightning on every blow (the third phase of the strongest). */
    private static void infuse(BossFight f) {
        CombatState.of(f.boss).buffs.put("infusion", 20 * 120);
        Vec3 p = f.boss.position();
        GLNetwork.fx(f.boss, "black_lightning", p.x, p.y + 1, p.z, 3, 0, 0);
    }

    private static void harden(BossFight f) {
        CombatState.of(f.boss).buffs.put("hardening", 20 * 30); // a 30 s window: advanced Armament (internal destruction) cuts through it
    }

    private static void pullTo(LivingEntity e, Vec3 to, double strength) {
        PowerKit.pull(e, to, strength, 0.15);
    }

    static {
        // ======================================================================== the Four Emperors
        // Hrolf Kumobashi, "Cloudbridge": a giant of Elbaph, Hasshoken and Conqueror's Haki. Arena: shockwaves that
        // roll out from him (stand close to his feet or far away), and black lightning on every blow at the end.
        BossMove hrolfWave = extra(m("hrolf", "giant_shockwave").ring(24, 5).windup(30).power(0.9f).el(IMPACT).kb(1.4f).lift(0.5f).style("quake").theme("shockwave"));
        add(def("hrolf").relic("iron_kanabo", 7).scroll("hasshoken").haki("conqueror").bounty(1_500_000_000L)
                .move(m("hrolf", "cloudbridge_slam").self(7).windup(32).power(1.6f).el(BLUNT).kb(1.8f).lift(0.7f).anim("heavy_overhead", "ground_slam").style("quake").theme("air_crack"))
                .move(m("hrolf", "hearth_breaker").cone(14, 80).windup(26).power(1.4f).el(BLUNT).kb(2.4f).lift(0.3f).anim("heavy_swing_back", "heavy_swing").style("haki").theme("shockwave").phases(1, 3))
                .move(m("hrolf", "rampart_of_elbaph").ring(20, 6).windup(36).power(1.7f).el(IMPACT).kb(1.5f).lift(0.5f).anim("roar", "ground_slam").style("quake").theme("shockwave").phases(2, 3))
                .onPhase(3, Bosses::infuse)
                .arena(every(8, 5, (f, now) -> BossArena.event(f, hrolfWave, f.boss.position()))));

        // Kurogane Shiomaru, "the Kraken": the Kraken Zoan, a gambler's kanabo. Arena: ink pools and, near the sea,
        // tentacles that burst up under his foes. He takes his hybrid form in the second phase, the Kraken in the third.
        BossMove kuroTentacle = extra(m("kurogane", "rising_tentacle").circle(2.5).windup(24).power(0.9f).el(BLUNT).lift(0.9f).style("ink").theme("kraken_slam"));
        add(def("kurogane").relic("iron_kanabo", 6).scroll("ryuo").haki("armament").bounty(1_500_000_000L)
                .move(m("kurogane", "tentacle_barrage").rain(3, 2).windup(28).power(1.2f).el(BLUNT).kb(1.0f).lift(0.9f).anim("cast_ground", "ground_slam").style("ink").theme("kraken_slam"))
                .move(m("kurogane", "ink_maelstrom").cone(14, 60).windup(24).power(0.9f).el(WATER).kb(0.8f).anim("roar", "cast_forward").style("ink")
                        .effect(MobEffects.BLINDNESS, 60, 0).effect(MobEffects.MOVEMENT_SLOWDOWN, 60, 1).phases(1, 3))
                .move(m("kurogane", "abyssal_crush").circle(5).windup(34).power(1.8f).el(BLUNT).kb(0.3f).stun(20).anim("roar", "slam").style("ink").theme("kraken_crush", 3)
                        .effect(MobEffects.MOVEMENT_SLOWDOWN, 80, 2).phases(2, 3))
                .onPhase(2, f -> zoan(f, "hybrid")).onPhase(3, f -> zoan(f, "full")).onPhase(3, Bosses::harden)
                .arena(every(5, 3, (f, now) -> {
                    if (BossArena.nearWater(f)) BossArena.event(f, kuroTentacle, BossArena.nearFoe(f, 0));
                    if ((now - f.start) / 20 % 10 == 0) BossArena.hazard(f, BossArena.nearFoe(f, 2), 4, 12, "ink", 0.05f, WATER)
                            .effect(MobEffects.BLINDNESS, 40, 0).effect(MobEffects.MOVEMENT_SLOWDOWN, 30, 1);
                })));

        // Barnum Candlewick, "Waxwork": the Hobby-Hobby Fruit and a showman's hammer. Arena: the carnival comes alive,
        // toy bombs marching in at his foes; the third phase opens with Toy Kingdom (the fruit's awakening).
        BossMove barnumToys = extra(m("barnum", "toy_bomb").circle(2.5).windup(26).power(0.8f).el(IMPACT).kb(1f).lift(0.4f).style("toy").theme("toy_poof"));
        BossMove toyKingdom = extra(m("barnum", "toy_kingdom").self(12).windup(40).power(0f).reuse("fruit/hobi/toy_kingdom").anim("cast_up", "cast_up").style("toy").theme("confetti", 1));
        add(def("barnum").relic("war_hammer", 6).scroll("anchor_brawl").haki("armament").bounty(1_500_000_000L)
                .move(m("barnum", "hobby_touch").line(10, 1.5).windup(20).power(1.0f).el(PHYSICAL).dash().anim("stance", "palm_strike").style("toy").theme("toy_poof")
                        .effect(MobEffects.MOVEMENT_SLOWDOWN, 80, 2).effect(MobEffects.WEAKNESS, 80, 1))
                .move(m("barnum", "toy_parade").rain(3, 3).windup(30).power(1.1f).el(IMPACT).kb(1.2f).lift(0.4f).anim("cast_up", "cast_ground").style("toy").theme("confetti").phases(1, 3))
                .move(m("barnum", "big_top_finale").self(9).windup(38).power(1.6f).el(BLUNT).kb(2f).lift(0.6f).anim("heavy_overhead", "ground_slam").style("toy").theme("confetti", 1).phases(2, 3))
                .onPhase(3, f -> f.startMove(toyKingdom, f.boss.getTarget(), null, true))
                .arena(every(7, 4, strikeFoes(barnumToys, 2))));

        // Aurelia Hinode, "Daybreak": the Star-Star Fruit (Logia) and one sword. Arena: stars fall on her foes; her
        // last phase ends in a supernova that first drags everyone in.
        BossMove aureliaStar = extra(m("aurelia", "falling_star").circle(2.5).windup(26).power(0.8f).el(LIGHT).kb(0.6f).style("star").theme("starfall"));
        add(def("aurelia").relic("fine_katana", 6).scroll("ittoryu").haki("conqueror").bounty(1_500_000_000L)
                .move(m("aurelia", "daybreak_slash").line(18, 1.6).windup(22).power(1.4f).el(SLASH).kb(1f).dash().anim("iai_stance", "iai_slash").style("star"))
                .move(m("aurelia", "starfall").rain(3.5, 3).windup(32).power(1.2f).el(LIGHT).kb(0.8f).anim("cast_up", "cast_ground").style("star").theme("starfall").phases(1, 3))
                .move(m("aurelia", "supernova").self(11).windup(50).power(2.0f).el(LIGHT).kb(2f).lift(0.5f).pull(0.03).cooldown(360).anim("cast_up", "roar").style("star").theme("hoshi_supernova").phases(2, 3))
                .onPhase(3, Bosses::infuse)
                .arena(every(5, 3, strikeFoes(aureliaStar, 2))));

        // ======================================================================== the Marines
        // Fleet Admiral Constance Ironwake, "the Unbending Tide": the Whirl-Whirl Fruit and Rokushiki. Arena: whirlpools
        // that drag foes around and in (at sea they open in the water around the ship).
        add(def("constance").color(BossBarColor.BLUE).relic("fine_katana", 6).scroll("rokushiki").haki("observation").bounty(1_200_000_000L)
                .move(m("constance", "maelstrom").circle(6).windup(32).power(1.3f).el(WATER).pull(0.05).kb(0.5f).anim("cast_forward", "cast_ground").style("whirl").theme("whirlpool"))
                .move(m("constance", "tidal_spiral").line(20, 2).windup(24).power(1.3f).el(WATER).kb(1.6f).anim("stance", "thrust").style("whirl").phases(1, 3))
                .move(m("constance", "charybdis").ring(16, 4).windup(38).power(1.6f).el(WATER).kb(1.2f).lift(0.4f).anim("cast_up", "roar").style("whirl").theme("vortex").phases(2, 3))
                .arena(every(15, 12, (f, now) -> {
                    int n = f.phase >= 3 ? 3 : 2;
                    for (int i = 0; i < n; i++) BossArena.hazard(f, BossArena.spot(f, 6, 14), 3.5, 14, "whirl", 0.12f, WATER).swirl(0.18)
                            .effect(MobEffects.MOVEMENT_SLOWDOWN, 30, 1);
                })));

        // Admiral Zenith (Tenma Solberg): the Glint-Glint Fruit (Logia). Arena: pillars of light at his foes' feet.
        BossMove zenithPillar = extra(m("zenith", "light_pillar").circle(2).windup(22).power(0.8f).el(LIGHT).style("light").theme("light_pillar", 10));
        add(def("zenith").color(BossBarColor.BLUE).relic("rapier", 5).scroll("black_leg").haki("observation").bounty(800_000_000L)
                .move(m("zenith", "yasakani_rain").rain(2.5, 4).windup(24).power(1.0f).el(LIGHT).kb(0.6f).anim("cast_up", "cast_forward").style("light").theme("light_burst"))
                .move(m("zenith", "ama_no_murakumo").cone(9, 110).windup(20).power(1.3f).el(SLASH).kb(1f).anim("stance", "wide_slash").style("light").theme("light_sword").phases(1, 3))
                .move(m("zenith", "amaterasu").line(32, 1.4).windup(30).power(1.8f).el(LIGHT).kb(1.4f).range(32).anim("cast_forward", "cast_forward").style("light").phases(2, 3))
                .arena(every(4, 3, strikeFoes(zenithPillar, 2))));

        // Admiral Nadir (Ingrid Fukamizu): the Dark-Dark Fruit, which pulls everything in and lets nothing pass
        // through. Arena: darkness spreads from where she stands, blinding and dragging.
        add(def("nadir").color(BossBarColor.BLUE).relic("kanabo", 7).scroll("rokushiki").haki("armament").bounty(800_000_000L)
                .move(m("nadir", "black_hole").circle(6).windup(34).power(1.4f).el(DARKNESS).pull(0.06).kb(0.2f).anim("cast_ground", "ground_slam").style("dark").theme("black_hole")
                        .effect(MobEffects.DARKNESS, 80, 0))
                .move(m("nadir", "kurouzu").line(16, 1.6).windup(22).power(1.2f).el(DARKNESS).kb(0).anim("cast_forward", "grab").style("dark")
                        .onHit((f, t, e) -> pullTo(e, f.boss.position(), 1.4)).effect(MobEffects.MOVEMENT_SLOWDOWN, 40, 1).phases(1, 3))
                .move(m("nadir", "liberation").self(10).windup(36).power(1.7f).el(DARKNESS).kb(1.8f).lift(0.4f).anim("roar", "cast_up").style("dark").theme("darkness_burst").phases(2, 3))
                .arena(every(25, 25, (f, now) -> darkness(f), (f, phase) -> darkness(f))));

        // Admiral Meridian (Rocco Kirisame): the Magma-Magma Fruit (Logia). Arena: the ground (or the sea around his
        // ship) boils into a burning lava crust, and in the last phase volcanic bombs rain on his foes.
        BossMove meteor = extra(m("meridian", "volcanic_bomb").circle(3).windup(28).power(1.0f).el(MAGMA).ignite(4).style("magma").theme("magma_burst"));
        add(def("meridian").color(BossBarColor.BLUE).relic("brass_knuckles", 6).scroll("ryuo").haki("armament").bounty(800_000_000L)
                .move(m("meridian", "dai_funka").line(16, 2.2).windup(24).power(1.5f).el(MAGMA).kb(1.4f).ignite(5).anim("stance", "heavy_punch").style("magma").theme("magma_burst"))
                .move(m("meridian", "meigo").circle(2.5).windup(16).power(1.6f).el(MAGMA).ignite(4).dash().range(14).anim("stance", "thrust").style("magma").phases(1, 3))
                .move(m("meridian", "ryusei_kazan").rain(3, 4).windup(38).power(1.3f).el(MAGMA).kb(1f).lift(0.3f).ignite(4).anim("cast_up", "cast_up").style("magma").theme("eruption").phases(2, 3))
                .arena(every(0, 4, strikeFoes(meteor, 1), (f, phase) -> {
                    BossArena.crust(f, f.boss.position(), 9, Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState());
                    for (int i = 0; i < 2; i++) BossArena.hazard(f, BossArena.spot(f, 4, 10), 3, 20, "magma", 0.2f, MAGMA);
                })));

        // ======================================================================== the Seven Warlords
        // Sterling Okanemochi, "the Fence" (later the Fifth Emperor): the Scale-Scale Fruit and a golden flintlock.
        // Arena: the scales tilt, and whoever stands on the heavy side can barely move.
        add(def("sterling").color(BossBarColor.YELLOW).relic("flintlock", 3).scroll("gunslinger").haki("armament").bounty(1_000_000_000L)
                .move(m("sterling", "golden_volley").line(24, 1.2).windup(18).power(1.1f).el(PIERCE).kb(0.6f).range(24).anim("aim", "rapid_fire").style("gold"))
                .move(m("sterling", "weigh_down").circle(5).windup(26).power(0.9f).el(BLUNT).kb(0).anim("point", "cast_ground").style("gold").theme("tenbin_weight", 2)
                        .effect(MobEffects.MOVEMENT_SLOWDOWN, 100, 3).onHit((f, t, e) -> FruitKit.impulse(e, new Vec3(0, -0.8, 0), false)).phases(1, 3))
                .move(m("sterling", "final_judgement").circle(7).windup(36).power(1.8f).el(BLUNT).kb(0.4f).stun(16).anim("cast_up", "slam").style("gold").theme("tenbin_judgement", 1).phases(2, 3))
                .arena(every(12, 9, (f, now) -> {
                    for (int i = 0; i < (f.phase >= 3 ? 3 : 2); i++) BossArena.hazard(f, BossArena.nearFoe(f, 4), 4, 10, "gold", 0.06f, BLUNT)
                            .effect(MobEffects.MOVEMENT_SLOWDOWN, 30, 2);
                })));

        // Hisui Benitsubaki, "the Jade Camellia": the Yamata-no-Orochi Zoan and a war bow. Arena: venom pools; her
        // heads grow back when the last phase begins.
        add(def("hisui").color(BossBarColor.GREEN).relic("war_bow", 3).scroll("sniper").haki("conqueror").bounty(600_000_000L)
                .move(m("hisui", "eight_head_fury").cone(10, 130).windup(26).power(1.4f).el(PIERCE).kb(1.2f).anim("roar", "claw_flurry").style("venom").theme("venom")
                        .effect(MobEffects.POISON, 80, 1))
                .move(m("hisui", "venom_rain").rain(3, 3).windup(30).power(0.9f).el(PIERCE).anim("aim", "fire_two_hand").style("venom").theme("venom")
                        .effect(MobEffects.POISON, 100, 1).phases(1, 3))
                .move(m("hisui", "serpent_coil").ring(11, 3).windup(30).power(1.5f).el(BLUNT).kb(0.4f).anim("stance", "spin_kick").style("venom")
                        .effect(MobEffects.MOVEMENT_SLOWDOWN, 60, 2).phases(2, 3))
                .onPhase(2, f -> zoan(f, "hybrid")).onPhase(3, f -> zoan(f, "full")).power(3, "fruit/hebi_orochi/regrowth")
                .arena(every(9, 6, (f, now) -> BossArena.hazard(f, BossArena.nearFoe(f, 1), 3.5, 12, "venom", 0.06f, POISON)
                        .effect(MobEffects.POISON, 60, 1))));

        // Jinpachi Grimaldi, "the Last Blade": iaido with a black blade. Arena: his cuts linger in the air; at the end
        // the world itself is crossed by slashes.
        BossMove crosscut = extra(m("jinpachi", "lingering_cut").line(26, 1.2).windup(26).power(1.0f).el(SLASH).style("slash"));
        add(def("jinpachi").color(BossBarColor.WHITE).relic("nodachi", 8).scroll("iaido").haki("armament").bounty(600_000_000L)
                .move(m("jinpachi", "last_blade").line(30, 1.4).windup(30).power(1.9f).el(SLASH).kb(1f).range(30).anim("iai_stance", "iai_slash").style("slash")
                        .after((f, t) -> {
                            if (f.phase < 2) return;
                            for (double d = 3; d < t.move.size(); d += 4)
                                BossArena.hazard(f, t.origin().add(t.dir.scale(d)), 1.4, 6, "slash", 0.15f, SLASH);
                        }))
                .move(m("jinpachi", "thousand_cuts").self(6).windup(20).power(1.2f).el(SLASH).kb(0.8f).anim("stance", "spin_slash").style("slash").theme("blade_storm").phases(1, 3))
                .move(m("jinpachi", "cross_of_kuraigana").cross(20, 1.4).windup(32).power(1.6f).el(SLASH).kb(0.8f).anim("iai_stance", "dual_x_cut").style("slash").phases(2, 3))
                .onPhase(3, Bosses::harden)
                .arena(every(0, 6, (f, now) -> {
                    Vec3 through = BossArena.nearFoe(f, 0);
                    double a = f.boss.getRandom().nextDouble() * Math.PI * 2;
                    BossArena.beam(f, crosscut, through.add(Math.cos(a) * 13, 0, Math.sin(a) * 13), through);
                })));

        // Leonhart Raiju, "the Thunder-Mane": a Mink lion's Electro. Arena: a thunderstorm; under the full moon of
        // his last phase he takes his Sulong form (faster, stronger).
        BossMove bolt = extra(m("leonhart", "thunderstrike").circle(2.5).windup(20).power(0.9f).el(LIGHTNING).stun(6).style("lightning").theme("thunder"));
        add(def("leonhart").color(BossBarColor.YELLOW).relic("brass_knuckles", 5).scroll("electro").haki("observation").bounty(500_000_000L)
                .move(m("leonhart", "thunder_mane").self(7).windup(24).power(1.3f).el(LIGHTNING).kb(1.2f).stun(10).anim("roar", "ground_slam").style("lightning").theme("discharge"))
                .move(m("leonhart", "lightning_pounce").line(14, 1.8).windup(18).power(1.4f).el(LIGHTNING).kb(1.4f).dash().anim("stance", "pounce").style("lightning").phases(1, 3))
                .move(m("leonhart", "sulong_roar").ring(14, 3).windup(32).power(1.6f).el(SOUND).kb(1.6f).lift(0.4f).anim("roar", "howl").style("lightning").theme("thunder")
                        .effect(MobEffects.CONFUSION, 60, 0).phases(2, 3))
                .onPhase(3, f -> {
                    f.boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 120, 1));
                    f.boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 120, 0));
                    Vec3 p = f.boss.position();
                    GLNetwork.fx(f.boss, "sulong", p.x, p.y + 1, p.z, 0, 0, 0);
                })
                .arena(every(4, 3, strikeFoes(bolt, 2))));

        // Sir Barnaby Saltmarsh, "the Salt Baron": the Salt-Salt Fruit. Arena: the salt flats (the fruit's own power),
        // which parch whoever stands on them.
        BossMove saltFlats = extra(m("saltmarsh", "salt_flats").self(12).windup(30).power(0f).reuse("fruit/shio/salt_flats").anim("cast_ground", "cast_ground").style("salt").theme("salt_flats"));
        add(def("saltmarsh").color(BossBarColor.WHITE).relic("fine_cutlass", 4).scroll("ittoryu").haki("armament").bounty(150_000_000L)
                .move(m("saltmarsh", "crystal_spikes").line(14, 2).windup(22).power(1.3f).el(PIERCE).kb(0.6f).lift(0.6f).anim("cast_ground", "cast_ground").style("salt").theme("salt", 1))
                .move(m("saltmarsh", "desiccate").circle(5).windup(28).power(1.0f).el(PHYSICAL).anim("point", "cast_forward").style("salt").theme("salt_flats")
                        .effect(MobEffects.WEAKNESS, 100, 1).effect(MobEffects.HUNGER, 100, 1).effect(MobEffects.MOVEMENT_SLOWDOWN, 60, 1).phases(1, 3))
                .move(m("saltmarsh", "pillar_of_salt").circle(3).windup(34).power(1.6f).el(PHYSICAL).stun(30).anim("cast_up", "slam").style("salt").theme("salt_statue", 3)
                        .effect(MobEffects.MOVEMENT_SLOWDOWN, 80, 4).phases(2, 3))
                .onPhase(2, f -> f.startMove(saltFlats, f.boss.getTarget(), null, true))
                .arena(every(14, 10, (f, now) -> BossArena.hazard(f, BossArena.nearFoe(f, 2), 4, 10, "salt", 0.05f, PHYSICAL)
                        .effect(MobEffects.WEAKNESS, 40, 0).effect(MobEffects.MOVEMENT_SLOWDOWN, 30, 0))));

        // Countess Lucrezia Kagamiya, "the Mirror-Monger": the Mirror-Mirror Fruit. Arena: beams of reflected light
        // cross the arena through her foes.
        BossMove mirrorBeam = extra(m("lucrezia", "reflected_beam").line(26, 1.0).windup(24).power(0.9f).el(LIGHT).style("mirror"));
        add(def("lucrezia").relic("rapier", 4).scroll("storm_dance").haki("observation").bounty(150_000_000L)
                .move(m("lucrezia", "prism_beam").line(20, 1.2).windup(22).power(1.3f).el(LIGHT).range(20).anim("cast_forward", "cast_forward").style("mirror").theme("mirror_flash"))
                .move(m("lucrezia", "kaleidoscope").rain(2.5, 4).windup(26).power(1.0f).el(SLASH).anim("cast_up", "cast_ground").style("mirror").theme("mirror_shard").phases(1, 3))
                .move(m("lucrezia", "blinding_vanity").cone(12, 90).windup(24).power(0.9f).el(LIGHT).anim("stance", "cast_forward").style("mirror")
                        .effect(MobEffects.BLINDNESS, 80, 0).effect(MobEffects.MOVEMENT_SLOWDOWN, 40, 1).phases(2, 3))
                .arena(every(6, 4, (f, now) -> {
                    Vec3 through = BossArena.nearFoe(f, 0);
                    double a = f.boss.getRandom().nextDouble() * Math.PI * 2;
                    BossArena.beam(f, mirrorBeam, through.add(Math.cos(a) * 13, 0, Math.sin(a) * 13), through);
                })));

        // Pipistrello Minimaro, "the Drill Lord": the Spiral-Spiral Fruit, the smallest Warlord ever. Arena: drills
        // bore up from below his foes.
        BossMove drill = extra(m("pipistrello", "drill_from_below").circle(2).windup(22).power(0.9f).el(PIERCE).lift(0.7f).style("spiral").theme("toy_poof"));
        add(def("pipistrello").relic("steel_spear", 4).scroll("black_leg").haki("armament").bounty(150_000_000L)
                .move(m("pipistrello", "screw_lance").line(16, 1.2).windup(16).power(1.3f).el(PIERCE).kb(1f).dash().anim("stance", "spear_thrust").style("spiral"))
                .move(m("pipistrello", "helix_burst").self(6).windup(24).power(1.1f).el(IMPACT).lift(1.0f).anim("cast_up", "handstand_spin").style("spiral").theme("tornado").phases(1, 3))
                .move(m("pipistrello", "drill_ambush").circle(3).windup(26).power(1.5f).el(PIERCE).lift(0.8f).dash().anim("cast_ground", "uppercut").style("spiral").phases(2, 3))
                .arena(every(5, 3, strikeFoes(drill, 2))));

        // ======================================================================== the final arcs
        // Nox Tsukuyomi, "the Miasma": the Gas-Gas Fruit (Logia), CP0's informant waiting at Laugh Tale. Arena: clouds
        // of poison gas drift over his foes.
        add(def("nox").relic("knife", 5).scroll("rokushiki").haki("observation").bounty(800_000_000L)
                .move(m("nox", "gastanet").circle(5).windup(30).power(1.5f).el(FIRE).kb(1.4f).lift(0.4f).ignite(3).anim("point", "cast_forward").style("gas").theme("gas_explosion"))
                .move(m("nox", "oxygen_drain").cone(10, 70).windup(22).power(0.8f).el(POISON).anim("cast_forward", "cast_forward").style("gas")
                        .effect(MobEffects.MOVEMENT_SLOWDOWN, 80, 2).effect(MobEffects.DIG_SLOWDOWN, 80, 1).effect(MobEffects.WEAKNESS, 80, 1).phases(1, 3))
                .move(m("nox", "shinokuni").self(11).windup(44).power(1.3f).el(POISON).anim("cast_up", "roar").style("gas").theme("shinokuni")
                        .effect(MobEffects.POISON, 100, 2).effect(MobEffects.MOVEMENT_SLOWDOWN, 60, 2).phases(2, 3))
                .arena(every(8, 5, (f, now) -> BossArena.hazard(f, BossArena.nearFoe(f, 3), 3.5, 10, "gas", 0.06f, POISON)
                        .effect(MobEffects.POISON, 40, 1))));

        // Vespera Kagenui, "the Shadow-Stitcher": head of CP0, the String-String Fruit. Arena: in her last phase a cage
        // of strings closes around the fight; whoever touches it is cut and thrown back in.
        add(def("vespera").relic("whip", 5).scroll("rokushiki").haki("observation").bounty(800_000_000L)
                .move(m("vespera", "goshikito").circle(4).windup(28).power(1.5f).el(SLASH).kb(0.3f).anim("cast_up", "cast_ground").style("string"))
                .move(m("vespera", "tamaito_volley").line(22, 1.0).windup(18).power(1.1f).el(PIERCE).range(22).anim("finger_pistol", "finger_pistol").style("string").phases(1, 3))
                .move(m("vespera", "puppet_strings").circle(6).windup(26).power(0.6f).el(SLASH).anim("cast_forward", "grab").style("string")
                        .effect(MobEffects.MOVEMENT_SLOWDOWN, 80, 4).onHit((f, t, e) -> pullTo(e, f.boss.position(), 1.0)).phases(2, 3))
                .arena(every(0, 0, (f, now) -> {}, (f, phase) -> {
                    if (phase >= 3) BossArena.hazard(f, f.boss.position(), 22, 40, "string", 0.3f, SLASH).cage(7);
                })));

        // Sigrun Yoake, "the Dawnbringer": the Phoenix Zoan, Supreme Commander of the Revolutionary Army. Arena: blue
        // flames ring her foes; the phoenix's flames of rebirth mend her when the last phase begins.
        add(def("sigrun").color(BossBarColor.GREEN).relic("naginata", 5).scroll("life_return").haki("conqueror").bounty(800_000_000L)
                .move(m("sigrun", "phoenix_brand").line(14, 2).windup(20).power(1.4f).el(FIRE).kb(1.4f).dash().ignite(3).anim("stance", "kick_high").style("phoenix"))
                .move(m("sigrun", "flame_wings").cone(10, 140).windup(24).power(1.2f).el(FIRE).kb(1.6f).ignite(3).anim("cast_up", "wide_slash").style("phoenix").theme("phoenix_wings").phases(1, 3))
                .move(m("sigrun", "dawn_pyre").self(9).windup(36).power(1.5f).el(FIRE).kb(1.2f).lift(0.5f).ignite(5).anim("roar", "cast_up").style("phoenix").theme("blue_flame").phases(2, 3))
                .onPhase(2, f -> zoan(f, "hybrid")).onPhase(3, f -> zoan(f, "full")).power(3, "fruit/tori_phoenix/rebirth_flames")
                .arena(every(9, 6, (f, now) -> BossArena.hazard(f, BossArena.nearFoe(f, 1), 3, 10, "phoenix", 0.12f, FIRE))));

        // Shogun Hagane Oboro: the Azure Dragon Zoan and iaido; Wano's puppet ruler. Arena: storm clouds hurl
        // thunderbolts at his foes.
        BossMove hThunder = extra(m("hagane", "dragon_thunder").circle(2.5).windup(22).power(0.9f).el(LIGHTNING).style("lightning").theme("thunder"));
        add(def("hagane").relic("fine_katana", 5).scroll("iaido").haki("armament").bounty(400_000_000L)
                .move(m("hagane", "bolo_breath").cone(16, 40).windup(26).power(1.5f).el(FIRE).ignite(4).anim("roar", "cast_forward").style("fire"))
                .move(m("hagane", "azure_iai").line(18, 1.4).windup(22).power(1.4f).el(SLASH).dash().anim("iai_stance", "iai_slash").style("slash").phases(1, 3))
                .move(m("hagane", "dragon_twister").self(9).windup(34).power(1.3f).el(WIND).kb(1f).lift(1.0f).anim("cast_up", "spin_slash").style("wind").theme("tornado").phases(2, 3))
                .onPhase(2, f -> zoan(f, "hybrid")).onPhase(3, f -> zoan(f, "full"))
                .arena(every(5, 3, strikeFoes(hThunder, 2))));

        // ======================================================================== the Five Stewards of the Pale Throne
        // Saint Aldric Shirokane, Steward of Records: the Time-Time Fruit. Arena: pockets of slowed time.
        BossMove twentyYears = m("aldric", "twenty_years").self(12).windup(44).power(0f).reuse("fruit/toki/twenty_years").anim("cast_up", "cast_up").style("time").phases(2, 3);
        add(def("aldric").color(BossBarColor.WHITE).relic("fine_katana", 5).scroll("santoryu").haki("observation").bounty(800_000_000L)
                .move(m("aldric", "skipped_moment").line(12, 1.5).windup(12).power(1.2f).el(SLASH).dash().anim("iai_stance", "iai_dash").style("time"))
                .move(m("aldric", "hourglass").circle(5).windup(28).power(0.9f).el(PHYSICAL).anim("cast_forward", "cast_ground").style("time")
                        .effect(MobEffects.MOVEMENT_SLOWDOWN, 100, 3).effect(MobEffects.DIG_SLOWDOWN, 100, 2).phases(1, 3))
                .move(twentyYears)
                .arena(every(10, 7, (f, now) -> BossArena.hazard(f, BossArena.nearFoe(f, 2), 4, 12, "time", 0f, PHYSICAL)
                        .effect(MobEffects.MOVEMENT_SLOWDOWN, 30, 2))));

        // Saint Morwenna Hoshikage, Steward of Faith: the Soul-Soul Fruit (Zeus the thundercloud, Prometheus the sun).
        // Arena: Prometheus burns around her; Zeus strikes her foes.
        BossMove zeus = extra(m("morwenna", "zeus_strike").circle(3).windup(22).power(0.9f).el(LIGHTNING).style("lightning").theme("zeus_cloud"));
        add(def("morwenna").color(BossBarColor.WHITE).relic("bo_staff", 5).scroll("rokushiki").haki("conqueror").bounty(800_000_000L)
                .move(m("morwenna", "zeus_judgement").rain(3, 3).windup(28).power(1.1f).el(LIGHTNING).anim("cast_up", "cast_forward").style("lightning").theme("zeus_cloud"))
                .move(m("morwenna", "prometheus_sun").circle(6).windup(34).power(1.6f).el(FIRE).ignite(5).kb(1f).anim("cast_up", "throw").style("fire").theme("prometheus_sun").phases(1, 3))
                .move(m("morwenna", "soul_pocus").cone(10, 60).windup(24).power(1.0f).el(DARKNESS).anim("point", "cast_forward").style("soul")
                        .effect(MobEffects.MOVEMENT_SLOWDOWN, 80, 2).effect(MobEffects.WEAKNESS, 80, 1)
                        .onHit((f, t, e) -> f.boss.heal(f.boss.getMaxHealth() * 0.01f)).phases(2, 3))
                .arena(every(4, 3, strikeFoes(zeus, 2), (f, phase) -> BossArena.hazard(f, f.boss.position(), 4, 30, "fire", 0.15f, FIRE))));

        // Saint Cassius Tetsugaki, Steward of Arms: the Tremor-Tremor Fruit. Arena: tremors roll out across the
        // ground and the island tilts, throwing everyone one way.
        BossMove tremor = extra(m("cassius", "tremor").ring(24, 8).windup(30).power(0.9f).el(IMPACT).kb(1.4f).lift(0.4f).style("quake").theme("shockwave"));
        add(def("cassius").color(BossBarColor.WHITE).relic("war_hammer", 6).scroll("hasshoken").haki("armament").bounty(800_000_000L)
                .move(m("cassius", "quake_punch").cone(14, 50).windup(24).power(1.6f).el(IMPACT).kb(2.4f).lift(0.3f).anim("stance", "heavy_punch").style("quake").theme("air_crack"))
                .move(m("cassius", "air_crack").self(10).windup(38).power(1.4f).el(IMPACT).kb(1.6f).lift(0.6f).anim("cast_up", "double_palm").style("quake").theme("air_crack").phases(1, 3))
                .move(m("cassius", "sea_quake").ring(18, 5).windup(34).power(1.5f).el(IMPACT).kb(1.8f).lift(0.5f).anim("roar", "ground_slam").style("quake").theme("shockwave").phases(2, 3))
                .arena(every(7, 5, (f, now) -> {
                    BossArena.event(f, tremor, f.boss.position());
                    if (f.phase >= 3) tilt(f);
                })));

        // Saint Ottavia Kanemura, Steward of the Treasury: the Barrier-Barrier Fruit. Arena: barrier walls rise and
        // shove foes out of their zones; she shuts herself in a barrier ball as the last phase begins.
        add(def("ottavia").color(BossBarColor.WHITE).relic("tonfa", 5).scroll("ryuo").haki("armament").bounty(800_000_000L)
                .move(m("ottavia", "barrier_crash").line(16, 2.4).windup(22).power(1.4f).el(BLUNT).kb(2f).anim("stance", "double_palm").style("barrier"))
                .move(m("ottavia", "barrier_prison").circle(3).windup(26).power(0.9f).el(BLUNT).stun(30).anim("point", "cast_forward").style("barrier")
                        .effect(MobEffects.MOVEMENT_SLOWDOWN, 80, 5).phases(1, 3))
                .move(m("ottavia", "barrier_bulldozer").cone(12, 90).windup(28).power(1.3f).el(BLUNT).kb(2.6f).anim("double_palm", "double_palm").style("barrier").phases(2, 3))
                .power(3, "fruit/bari/barrier_ball")
                .arena(every(10, 7, (f, now) -> BossArena.hazard(f, BossArena.nearFoe(f, 1), 3, 6, "barrier", 0f, BLUNT).pull(-0.5))));

        // Saint Leopold Yamigiri, Steward of the Sea Lanes: the Swamp-Swamp Fruit (Logia). Arena: the swamp spreads
        // from where he stands and drags at every step.
        add(def("leopold").color(BossBarColor.WHITE).relic("war_trident", 5).scroll("tide_grapple").haki("armament").bounty(800_000_000L)
                .move(m("leopold", "swamp_hand").circle(4).windup(26).power(1.2f).el(PHYSICAL).anim("cast_ground", "grab").style("swamp").theme("mud_grab", 2)
                        .effect(MobEffects.MOVEMENT_SLOWDOWN, 80, 3).onHit((f, t, e) -> FruitKit.impulse(e, new Vec3(0, -0.6, 0), false)))
                .move(m("leopold", "swamp_spit").line(16, 1.5).windup(20).power(1.2f).el(POISON).anim("cast_forward", "cast_forward").style("swamp")
                        .effect(MobEffects.MOVEMENT_SLOWDOWN, 60, 1).phases(1, 3))
                .move(m("leopold", "bottomless_swamp").self(10).windup(40).power(1.4f).el(PHYSICAL).pull(0.04).anim("cast_up", "ground_slam").style("swamp")
                        .effect(MobEffects.MOVEMENT_SLOWDOWN, 100, 4).phases(2, 3))
                .arena(every(30, 30, (f, now) -> swamp(f), (f, phase) -> swamp(f))));
    }

    private static void darkness(BossFight f) {
        if (f.phase < 2) return;
        BossArena.hazard(f, f.boss.position(), f.phase >= 3 ? 13 : 9, 25, "dark", 0.08f, DARKNESS).pull(0.04)
                .effect(MobEffects.DARKNESS, 40, 0);
    }

    private static void swamp(BossFight f) {
        if (f.phase < 2) return;
        BossArena.hazard(f, f.boss.position(), f.phase >= 3 ? 12 : 8, 30, "swamp", 0.06f, PHYSICAL).effect(MobEffects.MOVEMENT_SLOWDOWN, 30, 2);
    }

    /** The island tilts: everyone in the fight is thrown the same way. */
    private static void tilt(BossFight f) {
        double a = f.boss.getRandom().nextDouble() * Math.PI * 2;
        Vec3 push = new Vec3(Math.cos(a) * 0.9, 0.25, Math.sin(a) * 0.9);
        for (LivingEntity e : BossArena.foes(f)) FruitKit.impulse(e, push, false);
        Vec3 p = f.boss.position();
        GLNetwork.fx(f.boss, "island_tilt", p.x, p.y, p.z, 12, 0, 0);
    }

    /** Force class loading (registry is static). */
    public static int count() {
        return ALL.size();
    }

    private Bosses() {}
}
