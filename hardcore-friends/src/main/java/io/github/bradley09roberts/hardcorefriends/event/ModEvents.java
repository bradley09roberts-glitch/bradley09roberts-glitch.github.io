package io.github.bradley09roberts.hardcorefriends.event;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.CampProgress;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/** Server event wiring. Nothing here touches player death, respawn, game mode or difficulty. */
public final class ModEvents {
	private static final Set<Mob> HUNTERS = Collections.newSetFromMap(new WeakHashMap<>());

	private ModEvents() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof CompanionEntity companion) {
				Companions.track(companion);
			} else if (entity instanceof Mob mob && FriendsConfig.get().monstersTargetCompanions && huntsCompanions(mob)
				&& HUNTERS.add(mob)) {
				// Same priority vanilla zombies use for villagers; they must actually see a friend to start the hunt.
				mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, CompanionEntity.class, true));
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof CompanionEntity companion) {
				Companions.untrack(companion);
			}
		});
		// A block a player breaks is no longer the friends' own, whatever the player puts there next.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel) {
				Camp.data(serverLevel.getServer()).forgetPlaced(serverLevel, pos);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Unity.tick(server);
			CampNeeds.tick(server);
			CampProgress.tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Companions.clear();
			CampNeeds.clear();
		});
	}

	private static boolean huntsCompanions(Mob mob) {
		if (mob instanceof ZombifiedPiglin) {
			return false; // neutral unless provoked, like with villagers
		}
		return mob instanceof Zombie || mob instanceof AbstractSkeleton || mob instanceof Spider
			|| mob instanceof AbstractIllager || mob instanceof Witch;
	}
}
