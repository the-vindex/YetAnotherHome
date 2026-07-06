package yetanotherhome;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * Tracks when players last took damage. A recently hurt player cannot start
 * or complete a mod teleport for {@link ModConfigs#HURT_LOCKOUT_SECONDS}.
 * Death clears the mark: a respawned player is out of the fight and must be
 * able to /home or /back to their corpse immediately.
 */
public final class CombatTracker {
   private static final Map<UUID, Long> lastHurt = new HashMap<>();

   private CombatTracker() {}

   /** @return remaining lockout in whole seconds (rounded up), 0 if the player may teleport */
   public static long lockoutRemainingSeconds(UUID player) {
      Long hurtAt = lastHurt.get(player);
      if (hurtAt == null) {
         return 0;
      }
      long lockoutMs = ModConfigs.HURT_LOCKOUT_SECONDS.get() * 1000L;
      long elapsed = System.currentTimeMillis() - hurtAt;
      if (elapsed >= lockoutMs) {
         lastHurt.remove(player);
         return 0;
      }
      return (lockoutMs - elapsed + 999) / 1000;
   }

   /** Sends the standard lockout failure if the player is locked out. @return true if denied */
   public static boolean denyIfLockedOut(CommandSourceStack source, ServerPlayer player) {
      long remaining = lockoutRemainingSeconds(player.getUUID());
      if (remaining <= 0) {
         return false;
      }
      source.sendFailure(
         Component.literal("You can't teleport for another " + remaining + "s because you took damage."));
      return true;
   }

   public static void clear(UUID player) {
      lastHurt.remove(player);
   }

   public static void onLivingDamage(LivingDamageEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0.0F) {
         lastHurt.put(player.getUUID(), System.currentTimeMillis());
      }
   }

   public static void onPlayerLogout(PlayerLoggedOutEvent event) {
      lastHurt.remove(event.getEntity().getUUID());
   }

   public static void onServerStopping(ServerStoppingEvent event) {
      lastHurt.clear();
   }
}
