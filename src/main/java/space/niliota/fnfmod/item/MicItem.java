package space.niliota.fnfmod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class MicItem extends Item {
    public MicItem(Properties properties) {
        super(properties);
    }

    // Override the "use" method, which triggers on Right-Click
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);

        // ALWAYS check if you are on the server before doing logic like messages, giving items, or changing blocks
        // level.isClientSide() is true for the Client, false for the Server
        if (!level.isClientSide()) {

            // (opening the song menu happens on the client, see FNFmodClient)

            // 2. Add a cooldown so it can't be spammed (20 ticks = 1 second)
            player.getCooldowns().addCooldown(this, 20);

            // Optionally, damage the item (if it has durability)
            // itemStack.hurtAndBreak(1, player, Player.getSlotForHand(usedHand));

        }



        // 3. Play a sound. This runs on both the client (for the user to hear instantly) and server (for others to hear)
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);

        // Tell the game the action was successful
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }


    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("beep bop skee-bep!").withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
