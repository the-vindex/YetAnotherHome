package yetanotherhome;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(YetAnotherHome.MOD_ID)
public class YetAnotherHome {
   public static final String MOD_ID = "yetanotherhome";

   public YetAnotherHome() {
      ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModConfigs.SPEC, "yetanotherhome.toml");
      MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
      MinecraftForge.EVENT_BUS.addListener(YetAnotherHome::onLivingDeath);
      MinecraftForge.EVENT_BUS.addListener(CombatTracker::onLivingDamage);
      MinecraftForge.EVENT_BUS.addListener(CombatTracker::onPlayerLogout);
      MinecraftForge.EVENT_BUS.addListener(CombatTracker::onServerStopping);
      MinecraftForge.EVENT_BUS.addListener(TeleportScheduler::onServerTick);
      MinecraftForge.EVENT_BUS.addListener(TeleportScheduler::onPlayerLogout);
      MinecraftForge.EVENT_BUS.addListener(TeleportScheduler::onServerStopping);
      MinecraftForge.EVENT_BUS.addListener(TpaManager::onServerTick);
      MinecraftForge.EVENT_BUS.addListener(TpaManager::onPlayerLogout);
      MinecraftForge.EVENT_BUS.addListener(TpaManager::onServerStopping);
   }

   private void onRegisterCommands(RegisterCommandsEvent event) {
      HomeCommands.register(event.getDispatcher());
      TpaCommands.register(event.getDispatcher());
      BackCommands.register(event.getDispatcher());
   }

   private static void onLivingDeath(LivingDeathEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         BackData.get(player.serverLevel().getServer()).recordDeath(player.getUUID(), HomeLocation.of(player));
         CombatTracker.clear(player.getUUID());
      }
   }
}
