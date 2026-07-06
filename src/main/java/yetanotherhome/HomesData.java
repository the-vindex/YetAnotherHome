package yetanotherhome;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/** Per-player named homes, persisted with the world in data/yetanotherhome_homes.dat. */
public class HomesData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String DATA_NAME = "yetanotherhome_homes";
   private static final Codec<Map<UUID, Map<String, HomeLocation>>> HOMES_CODEC = Codec.unboundedMap(
      Codec.STRING.xmap(UUID::fromString, UUID::toString),
      Codec.unboundedMap(Codec.STRING, HomeLocation.CODEC));

   private final Map<UUID, Map<String, HomeLocation>> homes = new HashMap<>();

   public static HomesData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(HomesData::load, HomesData::new, DATA_NAME);
   }

    /**
     * Set or update a named home for a player.
     *
     * @param player  the player UUID
     * @param name    the home name
     * @param location where to set it
     * @param isOp    true if the player is currently an operator (checked at call time)
     * @return false if the player is at the home limit and {@code name} is not an existing home.
     *         Operators bypass the limit when {@link ModConfigs#OP_UNLIMITED_HOMES} is true.
     */
    public boolean setHome(UUID player, String name, HomeLocation location, boolean isOp) {
       Map<String, HomeLocation> playerHomes = homes.computeIfAbsent(player, k -> new HashMap<>());
       if (playerHomes.size() >= ModConfigs.MAX_HOMES.get() && !playerHomes.containsKey(name)) {
          if (isOp && ModConfigs.OP_UNLIMITED_HOMES.get()) {
             // op: skip the limit, let the put() below create the new home
          } else {
             return false;
          }
       }
       playerHomes.put(name, location);
       setDirty();
       return true;
    }

   @Nullable
   public HomeLocation getHome(UUID player, String name) {
      return homes.getOrDefault(player, Map.of()).get(name);
   }

   public boolean deleteHome(UUID player, String name) {
      Map<String, HomeLocation> playerHomes = homes.get(player);
      if (playerHomes == null || playerHomes.remove(name) == null) {
         return false;
      }
      setDirty();
      return true;
   }

   /** @return the player's home names, sorted alphabetically */
   public Set<String> homeNames(UUID player) {
      return new TreeSet<>(homes.getOrDefault(player, Map.of()).keySet());
   }

   private static HomesData load(CompoundTag tag) {
      HomesData data = new HomesData();
      if (tag.contains("homes")) {
         HOMES_CODEC.parse(NbtOps.INSTANCE, tag.get("homes"))
            .resultOrPartial(error -> LOGGER.error("Failed to load homes: {}", error))
            .ifPresent(loaded -> loaded.forEach((uuid, playerHomes) -> data.homes.put(uuid, new HashMap<>(playerHomes))));
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      HOMES_CODEC.encodeStart(NbtOps.INSTANCE, homes)
         .resultOrPartial(error -> LOGGER.error("Failed to save homes: {}", error))
         .ifPresent(encoded -> tag.put("homes", encoded));
      return tag;
   }
}
