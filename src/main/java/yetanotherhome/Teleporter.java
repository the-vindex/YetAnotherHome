package yetanotherhome;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityTeleportEvent;

public final class Teleporter {

   private Teleporter() {}

   /**
    * Command-style teleport: fires the cancellable {@link EntityTeleportEvent.TeleportCommand}
    * so protection mods can veto, and uses the RelativeMovement overload, which requests a
    * POST_TELEPORT chunk ticket at the destination.
    *
    * @return false if an event handler blocked the teleport
    */
   public static boolean teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yRot, float xRot) {
      EntityTeleportEvent.TeleportCommand event = ForgeEventFactory.onEntityTeleportCommand(player, x, y, z);
      if (event.isCanceled()) {
         return false;
      }
      // Remember the origin so /back can return here.
      BackData.get(player.serverLevel().getServer())
         .recordTeleportOrigin(player.getUUID(), HomeLocation.of(player));
      player.teleportTo(level, event.getTargetX(), event.getTargetY(), event.getTargetZ(), Set.of(), yRot, xRot);
      return true;
   }

   public static boolean teleportToPlayer(ServerPlayer player, ServerPlayer destination) {
      return teleport(player, destination.serverLevel(), destination.getX(), destination.getY(), destination.getZ(),
         destination.getYRot(), destination.getXRot());
   }
}
