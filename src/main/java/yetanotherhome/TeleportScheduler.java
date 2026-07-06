package yetanotherhome;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Delays player-initiated teleports by {@link ModConfigs#TELEPORT_WARMUP_SECONDS}
 * so they can't be used to escape combat. Taking damage during the warmup
 * cancels the teleport (the hurt lockout is re-checked when the warmup ends).
 * A player has at most one pending teleport; a new one replaces it.
 */
public final class TeleportScheduler {
   private record Pending(long executeAt, Consumer<ServerPlayer> completion) {}

   private static final Map<UUID, Pending> pending = new HashMap<>();

   private TeleportScheduler() {}

   /** Runs {@code completion} after the warmup (immediately if warmup is 0). */
   public static void schedule(ServerPlayer player, Consumer<ServerPlayer> completion) {
      int warmup = ModConfigs.TELEPORT_WARMUP_SECONDS.get();
      if (warmup <= 0) {
         completion.accept(player);
         return;
      }
      pending.put(player.getUUID(), new Pending(System.currentTimeMillis() + warmup * 1000L, completion));
      player.sendSystemMessage(
         Component.literal("Teleporting in " + warmup + "s — don't take damage!").withStyle(ChatFormatting.GOLD));
   }

   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase != TickEvent.Phase.END || pending.isEmpty()) {
         return;
      }
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server == null) {
         return;
      }
      long now = System.currentTimeMillis();
      for (Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator(); it.hasNext(); ) {
         Map.Entry<UUID, Pending> entry = it.next();
         if (now < entry.getValue().executeAt()) {
            continue;
         }
         it.remove();
         ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
         if (player == null) {
            continue;
         }
         if (CombatTracker.lockoutRemainingSeconds(player.getUUID()) > 0) {
            player.sendSystemMessage(
               Component.literal("Teleport cancelled because you took damage.").withStyle(ChatFormatting.RED));
            continue;
         }
         entry.getValue().completion().accept(player);
      }
   }

   public static void onPlayerLogout(PlayerLoggedOutEvent event) {
      pending.remove(event.getEntity().getUUID());
   }

   public static void onServerStopping(ServerStoppingEvent event) {
      pending.clear();
   }
}
