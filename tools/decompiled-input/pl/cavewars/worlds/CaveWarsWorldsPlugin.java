package pl.cavewars.worlds;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import pl.cavewars.worlds.api.CaveWorldsApi;

public final class CaveWarsWorldsPlugin extends JavaPlugin implements CaveWorldsApi {
   private ArenaWorldManager worldManager;

   public void onEnable() {
      this.saveDefaultConfig();
      this.worldManager = new ArenaWorldManager(this);

      try {
         this.worldManager.initialize();
      } catch (Exception var3) {
         this.getLogger().severe("Nie udało się zainicjalizować pluginu: " + var3.getMessage());
         this.getServer().getPluginManager().disablePlugin(this);
         return;
      }

      CaveWorldCommand var1 = new CaveWorldCommand(this, this.worldManager);
      PluginCommand var2 = this.getCommand("caveworlds");
      if (var2 == null) {
         this.getLogger().severe("Brak komendy caveworlds w plugin.yml");
         this.getServer().getPluginManager().disablePlugin(this);
      } else {
         var2.setExecutor(var1);
         var2.setTabCompleter(var1);
         Bukkit.getServicesManager().register(CaveWorldsApi.class, this, this, ServicePriority.Normal);
         this.getLogger().info("CaveWarsWorlds włączony.");
      }
   }

   public void onDisable() {
      Bukkit.getServicesManager().unregisterAll(this);
      if (this.worldManager != null) {
         this.worldManager.shutdownAndDeleteAll();
      }
   }

   public ArenaWorldManager getWorldManager() {
      return this.worldManager;
   }

   @Override
   public CompletableFuture<World> createArena(String var1, String var2) {
      return this.worldManager.createArena(var1, var2);
   }

   @Override
   public CompletableFuture<Void> deleteArena(String var1) {
      return this.worldManager.deleteArena(var1);
   }

   @Override
   public Optional<World> getArenaWorld(String var1) {
      return this.worldManager.getArenaWorld(var1);
   }

   @Override
   public Optional<Location> getLobby(String var1) {
      return this.worldManager.getLobby(var1);
   }

   @Override
   public Collection<String> getActiveArenaIds() {
      return this.worldManager.getActiveArenaIds();
   }
}
