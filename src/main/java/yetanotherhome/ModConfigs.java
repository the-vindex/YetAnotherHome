package yetanotherhome;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Mod config, in config/yetanotherhome.toml. COMMON (not SERVER)
 * on purpose: this is a server-side mod, so per-world settings and client
 * sync — the two things SERVER configs buy — don't apply, and a file in
 * config/ is easier for admins to find and edit.
 */
public final class ModConfigs {
   public static final ForgeConfigSpec SPEC;
   public static final ForgeConfigSpec.IntValue MAX_HOMES;
   public static final ForgeConfigSpec.IntValue TPA_EXPIRY_SECONDS;
   public static final ForgeConfigSpec.IntValue DEATH_BACK_EXPIRY_SECONDS;
   public static final ForgeConfigSpec.IntValue BACK_EXPIRY_SECONDS;
   public static final ForgeConfigSpec.IntValue HURT_LOCKOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue TELEPORT_WARMUP_SECONDS;
    public static final ForgeConfigSpec.BooleanValue OP_UNLIMITED_HOMES;

    static {
       ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
       MAX_HOMES = builder
          .comment("Maximum number of homes per player")
           .defineInRange("maxHomes", 1, 1, 1000);
       OP_UNLIMITED_HOMES = builder
          .comment("If true, operators can create unlimited homes. Demotion prevents new homes beyond the limit; existing homes and overwrites still work.")
          .define("opUnlimitedHomes", true);
      TPA_EXPIRY_SECONDS = builder
         .comment("Seconds before a pending teleport request expires")
         .defineInRange("tpaExpirySeconds", 60, 1, 3600);
      DEATH_BACK_EXPIRY_SECONDS = builder
         .comment("Seconds after death during which /back prioritizes the death location over the regular back position")
         .defineInRange("deathBackExpirySeconds", 600, 1, 86400);
      BACK_EXPIRY_SECONDS = builder
         .comment("Seconds a teleport origin stays usable via /back; keeps /back from becoming a permanent waypoint into places a player teleported away from")
         .defineInRange("backExpirySeconds", 300, 1, 86400);
      HURT_LOCKOUT_SECONDS = builder
         .comment("Seconds after taking damage during which a player cannot start or complete a teleport (0 to disable)")
         .defineInRange("hurtLockoutSeconds", 15, 0, 3600);
      TELEPORT_WARMUP_SECONDS = builder
         .comment("Seconds a player-initiated teleport (/home, /back, accepted TPA) takes before the player is moved (0 for instant)")
         .defineInRange("teleportWarmupSeconds", 5, 0, 300);
      SPEC = builder.build();
   }

   private ModConfigs() {}
}
