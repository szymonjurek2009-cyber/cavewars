package pl.cavewars;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import pl.cavewars.arenachat.CaveWarsArenaChat;
import pl.cavewars.autopickup.CaveWarsAutoPickupFix;
import pl.cavewars.bridge.CaveWarsWorldBridge;
import pl.cavewars.countdown.CaveWarsLobbyCountdown;
import pl.cavewars.endteleport.CaveWarsEndTeleport;
import pl.cavewars.grajfix.GrajNpcFix;
import pl.cavewars.lossstats.CaveWarsLossStats;
import pl.cavewars.rules.CaveWarsRulesPatch;
import pl.cavewars.worlds.ArenaWorldManager;
import pl.cavewars.worlds.CaveWorldCommand;
import pl.cavewars.worlds.api.CaveWorldsApi;
import pl.topki.Topki;

public final class CaveWarsPlugin extends Topki implements CaveWorldsApi {
   private CaveConfig caveConfig;
   private ArenaData arena;
   private ArenaGenerator generator;
   private GameManager game;

   private File worldsDataFolder;
   private FileConfiguration worldsConfig;
   private ArenaWorldManager worldManager;
   private AutoArenaManager autoArenaManager;

   private CaveWarsLossStats lossStats;
   private CaveWarsRulesPatch rulesPatch;
   private CaveWarsArenaChat arenaChat;
   private CaveWarsAutoPickupFix autoPickup;
   private CaveWarsEndTeleport endTeleport;
   private CaveWarsLobbyCountdown lobbyCountdown;
   private CaveWarsWorldBridge worldBridge;
   private GrajNpcFix npcFix;

   @Override
   public void onEnable() {
      try {
         // Holder statystyk musi istnieć zanim Topki/HoloStats zaczną odczytywać przegrane.
         this.lossStats = new CaveWarsLossStats(this);

         super.onEnable();

         // RulesPatch ustawia wartości w config.properties przed wczytaniem GameManagera.
         this.rulesPatch = new CaveWarsRulesPatch(this);
         this.rulesPatch.enable();

         Path dataPath = this.getDataFolder().toPath();
         this.caveConfig = new CaveConfig(dataPath);
         this.caveConfig.load();
         this.arena = new ArenaData(dataPath);
         this.arena.load();
         this.generator = new ArenaGenerator(this.arena);
         this.game = new GameManager(this, this.caveConfig, this.arena, this.generator);

         CaveWarsCommand caveWarsCommand =
            new CaveWarsCommand(this, this.game, this.caveConfig, this.arena, this.generator);
         PluginCommand cw = this.getCommand("cavewars");
         if (cw == null) throw new IllegalStateException("Brak komendy cavewars w plugin.yml");
         cw.setExecutor(caveWarsCommand);
         cw.setTabCompleter(caveWarsCommand);
         this.getServer().getPluginManager().registerEvents(
            (Listener)new CaveWarsListener(this.game, this.arena), this);
         Bukkit.removeRecipe(org.bukkit.NamespacedKey.minecraft("shield"));
         this.getServer().getPluginManager().registerEvents(new ShieldCraftBlocker(), this);

         this.loadWorldsConfig();
         this.worldManager = new ArenaWorldManager(this);
         this.worldManager.initialize();
         this.autoArenaManager = new AutoArenaManager(this, this.caveConfig, this.arena, this.worldManager);
         this.autoArenaManager.enable();

         CaveWorldCommand caveWorldCommand = new CaveWorldCommand(this, this.worldManager);
         PluginCommand cww = this.getCommand("caveworlds");
         if (cww == null) throw new IllegalStateException("Brak komendy caveworlds w plugin.yml");
         cww.setExecutor(caveWorldCommand);
         cww.setTabCompleter(caveWorldCommand);
         Bukkit.getServicesManager().register(CaveWorldsApi.class, this, this, ServicePriority.Normal);

         this.lossStats.enable();

         this.arenaChat = new CaveWarsArenaChat(this);
         this.arenaChat.enable();
         this.autoPickup = new CaveWarsAutoPickupFix(this);
         this.autoPickup.enable();
         this.endTeleport = new CaveWarsEndTeleport(this);
         this.endTeleport.enable();
         this.lobbyCountdown = new CaveWarsLobbyCountdown(this);
         this.lobbyCountdown.enable();
         this.worldBridge = new CaveWarsWorldBridge(this);
         this.worldBridge.enable();
         this.npcFix = new GrajNpcFix(this);
         this.npcFix.enable();

         this.getLogger().info(
            "CaveWars Suite enabled: core + multi-autoarenas + worlds + stats + rules + chat + pickup + teleport + countdown + NPC + no-shield-craft.");

         if (!this.arena.hasArena()) {
            this.getLogger().warning("Arena nie jest ustawiona. Uzyj /cw setarena 20 18");
         }
         if (!this.arena.hasLobby()) {
            this.getLogger().warning("Lobby nie jest ustawione. Uzyj /cw setlobby");
         }
      } catch (Throwable t) {
         this.getLogger().severe("Nie udalo sie wlaczyc CaveWars Suite: " + t.getMessage());
         t.printStackTrace();
         this.getServer().getPluginManager().disablePlugin(this);
      }
   }

   @Override
   public void onDisable() {
      try {
         if (this.lossStats != null) this.lossStats.disable();
      } catch (Throwable t) {
         this.getLogger().warning("LossStats shutdown: " + t.getMessage());
      }

      Bukkit.getServicesManager().unregisterAll(this);

      if (this.autoArenaManager != null) {
         this.autoArenaManager.shutdown();
      }

      if (this.game != null) {
         this.game.shutdown();
      }

      if (this.worldManager != null) {
         try {
            this.worldManager.shutdownAndDeleteAll();
         } catch (Throwable t) {
            this.getLogger().warning("CWW shutdown: " + t.getMessage());
         }
      }

      super.onDisable();
   }

   void reloadLocalConfig() throws IOException {
      this.caveConfig.load();
      this.arena.load();
   }

   private void loadWorldsConfig() throws IOException {
      this.worldsDataFolder = new File(this.getDataFolder().getParentFile(), "CaveWarsWorlds");
      if (!this.worldsDataFolder.exists() && !this.worldsDataFolder.mkdirs()) {
         throw new IOException("Nie mozna utworzyc katalogu CaveWarsWorlds");
      }

      File configFile = new File(this.worldsDataFolder, "config.yml");
      if (!configFile.exists()) {
         YamlConfiguration cfg = new YamlConfiguration();
         cfg.set("fallback-world", "world");
         cfg.set("world.autosave", false);
         cfg.set("world.disable-mob-spawning", true);
         cfg.set("world.freeze-time", true);
         cfg.set("world.clear-weather", true);
         cfg.set("template-editor.platform", true);
         cfg.set("template-editor.platform-y", 100);
         cfg.set("template-editor.platform-radius", 2);
         cfg.save(configFile);
      }
      this.worldsConfig = YamlConfiguration.loadConfiguration(configFile);
   }

   public File getWorldsDataFolder() {
      return this.worldsDataFolder;
   }

   public FileConfiguration getWorldsConfig() {
      return this.worldsConfig;
   }

   public ArenaWorldManager getWorldManager() {
      return this.worldManager;
   }

   @Override
   public CompletableFuture<World> createArena(String id, String template) {
      return this.worldManager.createArena(id, template);
   }

   @Override
   public CompletableFuture<Void> deleteArena(String id) {
      return this.worldManager.deleteArena(id);
   }

   @Override
   public Optional<World> getArenaWorld(String id) {
      return this.worldManager.getArenaWorld(id);
   }

   @Override
   public Optional<Location> getLobby(String id) {
      return this.worldManager.getLobby(id);
   }

   @Override
   public Collection<String> getActiveArenaIds() {
      return this.worldManager.getActiveArenaIds();
   }
}
