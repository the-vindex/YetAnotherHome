package yetanotherhome;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** /sethome, /home, /delhome, /listhomes — without a name argument, the name defaults to "home". */
public final class HomeCommands {
   private static final String DEFAULT_HOME_NAME = "home";

   private static final SuggestionProvider<CommandSourceStack> HOME_SUGGESTIONS = (context, builder) -> {
      ServerPlayer player = context.getSource().getPlayer();
      if (player == null) {
         return builder.buildFuture();
      }
      return SharedSuggestionProvider.suggest(homes(context).homeNames(player.getUUID()), builder);
   };

   private HomeCommands() {}

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("sethome")
         .executes(context -> setHome(context, DEFAULT_HOME_NAME))
         .then(Commands.argument("name", StringArgumentType.word())
            .executes(context -> setHome(context, homeName(context)))));

      dispatcher.register(Commands.literal("home")
         .executes(context -> goHome(context, DEFAULT_HOME_NAME))
         .then(Commands.argument("name", StringArgumentType.word())
            .suggests(HOME_SUGGESTIONS)
            .executes(context -> goHome(context, homeName(context)))));

      dispatcher.register(Commands.literal("delhome")
         .executes(context -> deleteHome(context, DEFAULT_HOME_NAME))
         .then(Commands.argument("name", StringArgumentType.word())
            .suggests(HOME_SUGGESTIONS)
            .executes(context -> deleteHome(context, homeName(context)))));

      dispatcher.register(Commands.literal("listhomes")
         .executes(HomeCommands::listHomes));
   }

    private static int setHome(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
       ServerPlayer player = context.getSource().getPlayerOrException();
       boolean isOp = context.getSource().hasPermission(Commands.LEVEL_GAMEMASTERS);
       if (!homes(context).setHome(player.getUUID(), name, HomeLocation.of(player), isOp)) {
          context.getSource().sendFailure(
             Component.literal("You have reached the maximum of " + ModConfigs.MAX_HOMES.get() + " homes."));
          return 0;
       }
      context.getSource().sendSuccess(
         () -> Component.literal("Home '" + name + "' set.").withStyle(ChatFormatting.GREEN), false);
      return 1;
   }

   private static int goHome(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      HomeLocation home = homes(context).getHome(player.getUUID(), name);
      if (home == null) {
         context.getSource().sendFailure(Component.literal("Home " + name + " not set."));
         return 0;
      }
      ServerLevel level = home.resolveLevel(context.getSource().getServer());
      if (level == null) {
         context.getSource().sendFailure(
            Component.literal("Home " + name + " is in a dimension that no longer exists."));
         return 0;
      }
      if (CombatTracker.denyIfLockedOut(context.getSource(), player)) {
         return 0;
      }
      TeleportScheduler.schedule(player, p -> {
         if (!home.teleport(p, level)) {
            p.sendSystemMessage(Component.literal("Teleport was blocked.").withStyle(ChatFormatting.RED));
            return;
         }
         p.sendSystemMessage(Component.literal("Teleported to home: " + name).withStyle(ChatFormatting.GREEN));
      });
      return 1;
   }

   private static int deleteHome(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      if (!homes(context).deleteHome(player.getUUID(), name)) {
         context.getSource().sendFailure(Component.literal("Home " + name + " not found."));
         return 0;
      }
      context.getSource().sendSuccess(
         () -> Component.literal("Home " + name + " deleted.").withStyle(ChatFormatting.GREEN), false);
      return 1;
   }

   private static int listHomes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      Set<String> names = homes(context).homeNames(player.getUUID());
      if (names.isEmpty()) {
         context.getSource().sendFailure(Component.literal("You have no homes set."));
         return 0;
      }
      context.getSource().sendSuccess(
         () -> Component.literal("Your homes: " + String.join(", ", names)).withStyle(ChatFormatting.GREEN), false);
      return 1;
   }

   private static HomesData homes(CommandContext<CommandSourceStack> context) {
      return HomesData.get(context.getSource().getServer());
   }

   private static String homeName(CommandContext<CommandSourceStack> context) {
      return StringArgumentType.getString(context, "name").toLowerCase(Locale.ROOT);
   }
}
