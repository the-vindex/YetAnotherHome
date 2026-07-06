package yetanotherhome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** A saved position: dimension, coordinates, and view rotation. */
public record HomeLocation(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot) {

   public static final Codec<HomeLocation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(HomeLocation::dimension),
      Codec.DOUBLE.fieldOf("x").forGetter(HomeLocation::x),
      Codec.DOUBLE.fieldOf("y").forGetter(HomeLocation::y),
      Codec.DOUBLE.fieldOf("z").forGetter(HomeLocation::z),
      Codec.FLOAT.fieldOf("yaw").forGetter(HomeLocation::yRot),
      Codec.FLOAT.fieldOf("pitch").forGetter(HomeLocation::xRot)
   ).apply(instance, HomeLocation::new));

   public static HomeLocation of(ServerPlayer player) {
      return new HomeLocation(player.level().dimension(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
   }

   /** @return the level this home is in, or null if the dimension no longer exists */
   @Nullable
   public ServerLevel resolveLevel(MinecraftServer server) {
      return server.getLevel(dimension);
   }

   public boolean teleport(ServerPlayer player, ServerLevel level) {
      return Teleporter.teleport(player, level, x, y, z, yRot, xRot);
   }
}
