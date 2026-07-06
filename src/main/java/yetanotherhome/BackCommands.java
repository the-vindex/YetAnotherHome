package yetanotherhome;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** /back — return to your death location (priority, expiring) or toggle with your last teleport origin. */
public final class BackCommands {

   private BackCommands() {}

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("back")
         .executes(BackCommands::back));
   }

   private static int back(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      BackData data = BackData.get(context.getSource().getServer());

      HomeLocation death = data.getDeathLocation(player.getUUID());
      HomeLocation destination = death != null ? death : data.getBack(player.getUUID());
      if (destination == null) {
         context.getSource().sendFailure(Component.literal("No previous location found."));
         return 0;
      }
      ServerLevel level = destination.resolveLevel(context.getSource().getServer());
      if (level == null) {
         context.getSource().sendFailure(
            Component.literal("Your previous location is in a dimension that no longer exists."));
         return 0;
      }
      if (CombatTracker.denyIfLockedOut(context.getSource(), player)) {
         return 0;
      }
      boolean toDeath = death != null;
      TeleportScheduler.schedule(player, p -> {
         if (!destination.teleport(p, level)) {
            p.sendSystemMessage(Component.literal("Teleport was blocked.").withStyle(ChatFormatting.RED));
            return;
         }
         if (toDeath) {
            BackData.get(p.serverLevel().getServer()).clearDeath(p.getUUID());
            p.sendSystemMessage(Component.literal("Teleported back to your death location.").withStyle(ChatFormatting.GOLD));
         } else {
            p.sendSystemMessage(Component.literal("Teleported back to your last location.").withStyle(ChatFormatting.GREEN));
         }
      });
      return 1;
   }
}
