package io.github.bradley09roberts.hardcorefriends.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.block.Blocks;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModEntities;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * Shared set-up for server game tests. Tests that use it must give each test method its own environment
 * ({@code hardcorefriends-test:solo_NN}) so they run one at a time: the camp is world-wide.
 *
 * <p>The {@value #PLOT} structure is 32×12×32: dirt at relative y=0, grass at y=1, air above. Friends stand at y=2.
 */
public final class TestSupport {
	public static final String PLOT = "hardcorefriends-test:plot";
	public static final int GROUND_Y = 1;
	public static final int STAND_Y = 2;

	private TestSupport() {
	}

	public static BlockPos centre() {
		return new BlockPos(16, STAND_Y, 16);
	}

	/** Clears all camp memory and removes friends left over from earlier tests. Optionally sets the camp here. */
	public static CampData resetCamp(GameTestHelper helper, boolean withCamp) {
		ServerLevel level = helper.getLevel();
		for (CompanionEntity c : Companions.all()) {
			c.discard();
		}
		Companions.clear();
		CampNeeds.clear();
		NightWatch.clear();
		CampData data = Camp.data(level.getServer());
		data.resetForTests();
		if (withCamp) {
			data.setCamp(helper.absolutePos(centre()), Camp.dimensionId(level));
		}
		setTime(helper, 1000);
		clearWeather(helper);
		FriendsConfig.get().campRadius = 24; // a test that shrank the camp may have ended early
		return data;
	}

	/** A thunderstorm darkens the sky and sends friends home, so tests pin clear weather. */
	public static void clearWeather(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WeatherData weather = level.getWeatherData();
		weather.setClearWeatherTime(1_000_000);
		weather.setRaining(false);
		weather.setRainTime(0);
		weather.setThundering(false);
		weather.setThunderTime(0);
		level.setRainLevel(0.0F);
		level.setThunderLevel(0.0F);
	}

	public static void setTime(GameTestHelper helper, long dayTime) {
		ServerLevel level = helper.getLevel();
		level.registryAccess().get(WorldClocks.OVERWORLD).ifPresent(h -> level.getServer().clockManager().setTotalTicks(h, dayTime));
	}

	/** Spawns a friend the same way {@code /friends recruit} does, working around the plot centre. */
	public static CompanionEntity spawnFriend(GameTestHelper helper, FriendId id, BlockPos relative) {
		ServerLevel level = helper.getLevel();
		CompanionEntity c = ModEntities.COMPANION.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (c == null) {
			throw new IllegalStateException("could not create companion");
		}
		BlockPos abs = helper.absolutePos(relative);
		c.setFriendId(id);
		c.snapTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
		c.setHomePos(helper.absolutePos(centre()));
		c.setHealth(c.getMaxHealth());
		c.backpack().setCapacity(Unity.backpackSlots(level.getServer()));
		c.setMode(CompanionMode.WORK, null);
		level.addFreshEntity(c);
		Companions.track(c);
		CampData data = Camp.data(level.getServer());
		CampData.Ledger ledger = data.ledger(id);
		ledger.state = CampData.LifeState.ALIVE;
		ledger.entityId = c.getUUID();
		data.touchLedger();
		return c;
	}

	/** Places a chest, fills it and links it as the supply chest. */
	public static Container placeChest(GameTestHelper helper, BlockPos relative, ItemStack... items) {
		helper.setBlock(relative, Blocks.CHEST);
		BlockPos abs = helper.absolutePos(relative);
		Container chest = SupplyChest.at(helper.getLevel(), abs).orElseThrow();
		for (ItemStack stack : items) {
			SupplyChest.insert(chest, stack);
		}
		Camp.data(helper.getLevel().getServer()).setChestPos(abs);
		return chest;
	}

	public static void give(CompanionEntity c, ItemStack... items) {
		for (ItemStack s : items) {
			c.backpack().insert(s);
		}
	}

	/** Places a small natural oak tree (trunk height 4, non-persistent leaves) at a relative ground position. */
	public static void growOak(GameTestHelper helper, BlockPos relBase) {
		for (int y = 0; y < 4; y++) {
			helper.setBlock(relBase.above(y), Blocks.OAK_LOG);
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = 2; dy <= 4; dy++) {
					BlockPos p = relBase.offset(dx, dy, dz);
					if ((dx != 0 || dz != 0 || dy == 4) && Math.abs(dx) + Math.abs(dz) <= 3) {
						helper.setBlock(p, Blocks.OAK_LEAVES.defaultBlockState());
					}
				}
			}
		}
	}
}
