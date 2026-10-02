package com.terracraft.registry;

import com.terracraft.TerraCraft;
import com.terracraft.menu.AccessoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Container menu types. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, TerraCraft.MODID);

    public static final RegistryObject<MenuType<AccessoryMenu>> ACCESSORIES = MENUS.register("accessories",
        () -> IForgeMenuType.create(AccessoryMenu::new));

    private ModMenus() {}
}
