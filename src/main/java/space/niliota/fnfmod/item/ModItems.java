package space.niliota.fnfmod.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public class ModItems {

    // 1. Create the item(s)
    public static final Item MIC = registerItem("mic", new MicItem(new Item.Properties().stacksTo(1)));

    // A helper method to register items using Mojang mappings (instead of Yarn)
    private static Item registerItem(String name, Item item) {
        return Registry.register(
                BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath("fnfmod", name),
                item
        );
    }

    public static void registerModItems() {
        // 2. Add the item to an existing Creative Mode tab (e.g., INGREDIENTS)
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            entries.accept(MIC);
        });
    }
}
