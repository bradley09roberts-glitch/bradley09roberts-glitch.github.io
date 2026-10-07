package io.github.bradley09roberts.hardcorefriends.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

public class SmokeGameTest {
	@GameTest
	public void smoke(GameTestHelper context) {
		context.succeed();
	}
}
