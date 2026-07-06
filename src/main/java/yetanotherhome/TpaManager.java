package yetanotherhome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Pending teleport requests, in memory only. Indexed both by target (for
 * /tpaccept, /tpdeny) and by requester (for /tpcancel and replacement).
 * Expired requests are swept once per second, notifying both parties.
 */
public final class TpaManager {

   /** @param here if true the target is asked to teleport to the requester (/tpahere) */
   public record Request(UUID requester, String requesterName, UUID target, String targetName, boolean here, long expiresAt) {
      public boolean expired() {
         return System.currentTimeMillis() >= expiresAt;
      }
   }

   private static final Map<UUID, Request> byTarget = new HashMap<>();
   private static final Map<UUID, Request> byRequester = new HashMap<>();
   private static final int SWEEP_INTERVAL_TICKS = 20;
   private static int tickCounter;

   private TpaManager() {}

   /**
    * Registers a request, replacing any earlier request by the same requester.
    *
    * @return false if the target already has a pending request from someone else
    */
   public static boolean addRequest(ServerPlayer requester, ServerPlayer target, boolean here) {
      Request existing = byTarget.get(target.getUUID());
      if (existing != null && !existing.expired() && !existing.requester().equals(requester.getUUID())) {
         return false;
      }
      cancelByRequester(requester.getUUID());
      long expiresAt = System.currentTimeMillis() + ModConfigs.TPA_EXPIRY_SECONDS.get() * 1000L;
      Request request = new Request(requester.getUUID(), requester.getName().getString(),
         target.getUUID(), target.getName().getString(), here, expiresAt);
      byTarget.put(request.target(), request);
      byRequester.put(request.requester(), request);
      return true;
   }

   /** @return the pending request addressed to {@code target}, or null; does not check expiry */
   @Nullable
   public static Request getRequest(UUID target) {
      return byTarget.get(target);
   }

   public static void remove(Request request) {
      byTarget.remove(request.target(), request);
      byRequester.remove(request.requester(), request);
   }

   /** Cancels the request {@code requester} sent. @return the cancelled request, or null */
   @Nullable
   public static Request cancelByRequester(UUID requester) {
      Request request = byRequester.remove(requester);
      if (request != null) {
         byTarget.remove(request.target(), request);
      }
      return request;
   }

   private static void removeAllFor(UUID player) {
      cancelByRequester(player);
      Request incoming = byTarget.remove(player);
      if (incoming != null) {
         byRequester.remove(incoming.requester(), incoming);
      }
   }

   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase != TickEvent.Phase.END || ++tickCounter % SWEEP_INTERVAL_TICKS != 0) {
         return;
      }
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server == null) {
         return;
      }
      List<Request> expired = new ArrayList<>();
      for (Request request : byTarget.values()) {
         if (request.expired()) {
            expired.add(request);
         }
      }
      for (Request request : expired) {
         remove(request);
         notify(server, request.requester(), "Your teleport request to " + request.targetName() + " has expired.");
         notify(server, request.target(), "The teleport request from " + request.requesterName() + " has expired.");
      }
   }

   public static void onPlayerLogout(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         removeAllFor(player.getUUID());
      }
   }

   public static void onServerStopping(ServerStoppingEvent event) {
      byTarget.clear();
      byRequester.clear();
   }

   private static void notify(MinecraftServer server, UUID playerId, String message) {
      ServerPlayer player = server.getPlayerList().getPlayer(playerId);
      if (player != null) {
         player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GOLD));
      }
   }
}
