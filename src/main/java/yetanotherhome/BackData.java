package yetanotherhome;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Per-player /back state, persisted with the world in data/yetanotherhome_back.dat.
 *
 * Two independent, individually expiring slots per player:
 * - back: origin of the player's most recent mod teleport; /back swaps with it
 *   (toggle). Expires after {@link ModConfigs#BACK_EXPIRY_SECONDS} so teleporting
 *   out of a place doesn't grant a permanent waypoint back into it.
 * - death: where the player last died. Takes priority over the back slot so a
 *   corpse run survives intermediate teleports like "/home for equipment", but
 *   only for {@link ModConfigs#DEATH_BACK_EXPIRY_SECONDS}; cleared once used.
 */
public class BackData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String DATA_NAME = "yetanotherhome_back";

   private record StampedLocation(HomeLocation location, long time) {
      static final Codec<StampedLocation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
         HomeLocation.CODEC.fieldOf("location").forGetter(StampedLocation::location),
         Codec.LONG.fieldOf("time").forGetter(StampedLocation::time)
      ).apply(instance, StampedLocation::new));

      boolean olderThanSeconds(int seconds) {
         return System.currentTimeMillis() - time > seconds * 1000L;
      }

      static StampedLocation now(HomeLocation location) {
         return new StampedLocation(location, System.currentTimeMillis());
      }
   }

   private record Entry(Optional<StampedLocation> back, Optional<StampedLocation> death) {
      static final Entry EMPTY = new Entry(Optional.empty(), Optional.empty());
      static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
         StampedLocation.CODEC.optionalFieldOf("back").forGetter(Entry::back),
         StampedLocation.CODEC.optionalFieldOf("death").forGetter(Entry::death)
      ).apply(instance, Entry::new));
   }

   private static final Codec<Map<UUID, Entry>> ENTRIES_CODEC = Codec.unboundedMap(
      Codec.STRING.xmap(UUID::fromString, UUID::toString), Entry.CODEC);

   private final Map<UUID, Entry> entries = new HashMap<>();

   public static BackData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(BackData::load, BackData::new, DATA_NAME);
   }

   public void recordTeleportOrigin(UUID player, HomeLocation origin) {
      Entry old = entries.getOrDefault(player, Entry.EMPTY);
      entries.put(player, new Entry(Optional.of(StampedLocation.now(origin)), old.death()));
      setDirty();
   }

   /** @return the unexpired back position, or null; expired entries are cleared */
   @Nullable
   public HomeLocation getBack(UUID player) {
      StampedLocation back = entries.getOrDefault(player, Entry.EMPTY).back().orElse(null);
      if (back == null) {
         return null;
      }
      if (back.olderThanSeconds(ModConfigs.BACK_EXPIRY_SECONDS.get())) {
         Entry old = entries.get(player);
         entries.put(player, new Entry(Optional.empty(), old.death()));
         setDirty();
         return null;
      }
      return back.location();
   }

   public void recordDeath(UUID player, HomeLocation where) {
      Entry old = entries.getOrDefault(player, Entry.EMPTY);
      entries.put(player, new Entry(old.back(), Optional.of(StampedLocation.now(where))));
      setDirty();
   }

   /** @return the unexpired death location, or null; expired entries are cleared */
   @Nullable
   public HomeLocation getDeathLocation(UUID player) {
      StampedLocation death = entries.getOrDefault(player, Entry.EMPTY).death().orElse(null);
      if (death == null) {
         return null;
      }
      if (death.olderThanSeconds(ModConfigs.DEATH_BACK_EXPIRY_SECONDS.get())) {
         clearDeath(player);
         return null;
      }
      return death.location();
   }

   public void clearDeath(UUID player) {
      Entry old = entries.get(player);
      if (old != null && old.death().isPresent()) {
         entries.put(player, new Entry(old.back(), Optional.empty()));
         setDirty();
      }
   }

   private static BackData load(CompoundTag tag) {
      BackData data = new BackData();
      if (tag.contains("players")) {
         ENTRIES_CODEC.parse(NbtOps.INSTANCE, tag.get("players"))
            .resultOrPartial(error -> LOGGER.error("Failed to load /back data: {}", error))
            .ifPresent(data.entries::putAll);
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      ENTRIES_CODEC.encodeStart(NbtOps.INSTANCE, entries)
         .resultOrPartial(error -> LOGGER.error("Failed to save /back data: {}", error))
         .ifPresent(encoded -> tag.put("players", encoded));
      return tag;
   }
}
