package yetanotherhome;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** /tpa, /tpahere, /tpaccept, /tpdeny, /tpcancel, /tphere */
public final class TpaCommands {

   private TpaCommands() {}

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("tpa")
         .then(Commands.argument("player", EntityArgument.player())
            .executes(context -> request(context, false))));

      dispatcher.register(Commands.literal("tpahere")
         .then(Commands.argument("player", EntityArgument.player())
            .executes(context -> request(context, true))));

      dispatcher.register(Commands.literal("tpaccept")
         .executes(TpaCommands::accept));

      dispatcher.register(Commands.literal("tpdeny")
         .executes(TpaCommands::deny));

      dispatcher.register(Commands.literal("tpcancel")
         .executes(TpaCommands::cancel));

      dispatcher.register(Commands.literal("tphere")
         .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
         .then(Commands.argument("player", EntityArgument.player())
            .executes(TpaCommands::teleportHere)));
   }

   private static int request(CommandContext<CommandSourceStack> context, boolean here) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      ServerPlayer target = EntityArgument.getPlayer(context, "player");
      if (target.getUUID().equals(player.getUUID())) {
         context.getSource().sendFailure(Component.literal("Player not found or you can't teleport to yourself."));
         return 0;
      }
      if (!TpaManager.addRequest(player, target, here)) {
         context.getSource().sendFailure(
            Component.literal(target.getName().getString() + " already has a pending teleport request."));
         return 0;
      }
      String action = here ? " wants you to teleport to them." : " wants to teleport to you.";
      target.sendSystemMessage(
         Component.literal(player.getName().getString() + action
               + " Type /tpaccept to accept or /tpdeny to deny. (Expires in " + ModConfigs.TPA_EXPIRY_SECONDS.get() + "s)")
            .withStyle(ChatFormatting.GREEN)
            .append(" ")
            .append(button("[Accept]", "/tpaccept", ChatFormatting.GREEN))
            .append(" ")
            .append(button("[Deny]", "/tpdeny", ChatFormatting.RED)));
      context.getSource().sendSuccess(
         () -> Component.literal("Teleport request sent to " + target.getName().getString() + ". Use /tpcancel to cancel.")
            .withStyle(ChatFormatting.GREEN), false);
      return 1;
   }

   private static int accept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer target = context.getSource().getPlayerOrException();
      TpaManager.Request request = TpaManager.getRequest(target.getUUID());
      if (request == null || request.expired()) {
         context.getSource().sendFailure(Component.literal("No pending teleport requests."));
         return 0;
      }
      ServerPlayer requester = onlinePlayer(context, request.requester());
      if (requester == null) {
         context.getSource().sendFailure(Component.literal("Requester is no longer online."));
         return 0;
      }
      ServerPlayer moved = request.here() ? target : requester;
      UUID destinationId = request.here() ? requester.getUUID() : target.getUUID();
      long lockout = CombatTracker.lockoutRemainingSeconds(moved.getUUID());
      if (lockout > 0) {
         context.getSource().sendFailure(Component.literal(
            moved.getName().getString() + " can't teleport for another " + lockout + "s after taking damage."));
         if (moved != target) {
            moved.sendSystemMessage(Component.literal(
               "You can't teleport for another " + lockout + "s because you took damage.").withStyle(ChatFormatting.RED));
         }
         return 0; // request stays pending; retry after the lockout
      }
      TpaManager.remove(request);
      requester.sendSystemMessage(Component.literal("Teleport request accepted.").withStyle(ChatFormatting.GREEN));
      context.getSource().sendSuccess(
         () -> Component.literal("You accepted the teleport request.").withStyle(ChatFormatting.GREEN), false);
      TeleportScheduler.schedule(moved, p -> {
         ServerPlayer destination = p.serverLevel().getServer().getPlayerList().getPlayer(destinationId);
         if (destination == null) {
            p.sendSystemMessage(Component.literal("The other player is no longer online.").withStyle(ChatFormatting.RED));
            return;
         }
         if (!Teleporter.teleportToPlayer(p, destination)) {
            p.sendSystemMessage(Component.literal("Teleport was blocked.").withStyle(ChatFormatting.RED));
         }
      });
      return 1;
   }

   private static int deny(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer target = context.getSource().getPlayerOrException();
      TpaManager.Request request = TpaManager.getRequest(target.getUUID());
      if (request == null) {
         context.getSource().sendFailure(Component.literal("No pending teleport requests."));
         return 0;
      }
      ServerPlayer requester = onlinePlayer(context, request.requester());
      if (requester == null) {
         context.getSource().sendFailure(Component.literal("Requester is no longer online."));
         return 0;
      }
      requester.sendSystemMessage(Component.literal("Teleport request denied.").withStyle(ChatFormatting.RED));
      context.getSource().sendFailure(Component.literal("You denied the teleport request."));
      TpaManager.remove(request);
      return 1;
   }

   private static int cancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      TpaManager.Request request = TpaManager.cancelByRequester(player.getUUID());
      if (request == null) {
         context.getSource().sendFailure(Component.literal("No pending teleport request to cancel."));
         return 0;
      }
      ServerPlayer target = onlinePlayer(context, request.target());
      if (target != null) {
         target.sendSystemMessage(
            Component.literal(player.getName().getString() + " cancelled their teleport request.")
               .withStyle(ChatFormatting.GOLD));
      }
      context.getSource().sendSuccess(
         () -> Component.literal("Teleport request cancelled.").withStyle(ChatFormatting.GREEN), false);
      return 1;
   }

   private static int teleportHere(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer admin = context.getSource().getPlayerOrException();
      ServerPlayer target = EntityArgument.getPlayer(context, "player");
      if (!Teleporter.teleportToPlayer(target, admin)) {
         context.getSource().sendFailure(Component.literal("Teleport was blocked."));
         return 0;
      }
      target.sendSystemMessage(
         Component.literal("You have been teleported to " + admin.getName().getString())
            .withStyle(ChatFormatting.GOLD));
      context.getSource().sendSuccess(
         () -> Component.literal("Teleported " + target.getName().getString() + " to your location.")
            .withStyle(ChatFormatting.GREEN), false);
      return 1;
   }

   private static MutableComponent button(String label, String command, ChatFormatting color) {
      return Component.literal(label).withStyle(style -> style
         .withColor(color)
         .withBold(true)
         // SUGGEST_COMMAND so headless MCC clients don't auto-run /tpaccept from chat buttons.
         .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
         .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to run " + command))));
   }

   @Nullable
   private static ServerPlayer onlinePlayer(CommandContext<CommandSourceStack> context, UUID id) {
      return context.getSource().getServer().getPlayerList().getPlayer(id);
   }
}
