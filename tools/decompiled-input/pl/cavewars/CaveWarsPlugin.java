package pl.cavewars;

import java.io.IOException;
import java.nio.file.Path;
import org.bukkit.command.PluginCommand;
import pl.topki.Topki;

public final class CaveWarsPlugin extends Topki {
   private CaveConfig caveConfig;
   private ArenaData arena;
   private ArenaGenerator generator;
   private GameManager game;

   @Override
   public void onEnable() {
      super.onEnable();

      try {
         Path var1 = this.getDataFolder().toPath();
         this.caveConfig = new CaveConfig(var1);
         this.caveConfig.load();
         this.arena = new ArenaData(var1);
         this.arena.load();
         this.generator = new ArenaGenerator(this.arena);
         this.game = new GameManager(this, this.caveConfig, this.arena, this.generator);
         CaveWarsCommand var2 = new CaveWarsCommand(this, this.game, this.caveConfig, this.arena, this.generator);
         PluginCommand var3 = this.getCommand("cavewars");
         if (var3 == null) {
            throw new IllegalStateException("Brak komendy cavewars w plugin.yml");
         }

         var3.setExecutor(var2);
         var3.setTabCompleter(var2);
         this.getServer().getPluginManager().registerEvents(new CaveWarsListener(this.game, this.arena), this);
         this.getLogger().info("CaveWars + Topki 1.0.8 wlaczony. Paper 1.21.10 / Java 21.");
         if (!this.arena.hasArena()) {
            this.getLogger().warning("Arena nie jest ustawiona. Uzyj /cw setarena 20 18");
         }

         if (!this.arena.hasLobby()) {
            this.getLogger().warning("Lobby nie jest ustawione. Uzyj /cw setlobby");
         }
      } catch (Exception var4) {
         this.getLogger().severe("Nie udalo sie wlaczyc CaveWars: " + var4.getMessage());
         var4.printStackTrace();
         this.getServer().getPluginManager().disablePlugin(this);
      }
   }

   @Override
   public void onDisable() {
      if (this.game != null) {
         this.game.shutdown();
      }

      super.onDisable();
   }

   void reloadLocalConfig() throws IOException {
      this.caveConfig.load();
      this.arena.load();
   }
}
