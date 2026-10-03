package com.terracraft.registry;

import com.terracraft.TerraCraft;
import com.terracraft.menu.AccessoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

/** Container menu types. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, TerraCraft.MODID);

    public static final RegistryObject<MenuType<AccessoryMenu>> ACCESSORIES = MENUS.register("accessories",
        () -> IMenuTypeExtension.create(AccessoryMenu::new));

    private ModMenus() {}
}
