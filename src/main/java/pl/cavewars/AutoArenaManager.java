package pl.cavewars;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.TabCompleteEvent;
import pl.cavewars.worlds.ArenaWorldManager;

final class AutoArenaManager implements Listener {
   private static final String PREFIX = "§8[§6CaveWars§8] §f";
   private static final String TEMPLATE = "solo";
   private final CaveWarsPlugin plugin;
   private final CaveConfig config;
   private final ArenaData baseArena;
   private final ArenaWorldManager worlds;
   private final Map<String, AutoInstance> instances = new LinkedHashMap<>();
   private final Map<UUID, AutoInstance> playerArena = new HashMap<>();
   private final Set<UUID> pending = new LinkedHashSet<>();
   private boolean creating;
   private int nextIndex = 1;

   AutoArenaManager(CaveWarsPlugin plugin, CaveConfig config, ArenaData baseArena, ArenaWorldManager worlds) {
      this.plugin = plugin;
      this.config = config;
      this.baseArena = baseArena;
      this.worlds = worlds;
   }

   void enable() {
      this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
      this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tick, 20L, 20L);
      this.plugin.getLogger().info("AutoArena enabled: /cw autojoin, template=" + TEMPLATE + ", max=" + this.config.maxPlayers());
   }

   void shutdown() {
      for (AutoInstance instance : this.instances.values()) {
         try {
            instance.game.shutdown();
         } catch (Throwable ignored) {
         }
      }
      this.instances.clear();
      this.playerArena.clear();
      this.pending.clear();
   }
   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
   public void onCommand(PlayerCommandPreprocessEvent event) {
      String command = event.getMessage().trim().toLowerCase();
      Player player = event.getPlayer();

      if (command.equals("/cw help") || command.equals("/cavewars help")) {
         event.setCancelled(true);
         this.sendHelp(player);
         return;
      }

      if (command.equals("/cw autojoin") || command.equals("/cavewars autojoin")
         || command.equals("/cw join") || command.equals("/cavewars join")) {
         event.setCancelled(true);
         this.autoJoin(player);
         return;
      }

      if ((command.equals("/cw leave") || command.equals("/cavewars leave"))
         && this.playerArena.containsKey(player.getUniqueId())) {
         event.setCancelled(true);
         this.leave(player);
         return;
      }

      if ((command.equals("/cw status") || command.equals("/cavewars status"))
         && this.playerArena.containsKey(player.getUniqueId())) {
         event.setCancelled(true);
         AutoInstance instance = this.playerArena.get(player.getUniqueId());
         player.sendMessage(PREFIX + "§7Arena: §f" + instance.id + " §8| " + instance.game.statusLine());
      }
   }

   @EventHandler
   public void onTabComplete(TabCompleteEvent event) {
      String buffer = event.getBuffer().toLowerCase();
      if (buffer.equals("/cw ") || buffer.equals("/cavewars ")
         || buffer.startsWith("/cw a") || buffer.startsWith("/cavewars a")) {
         if (!event.getCompletions().contains("autojoin")) {
            event.getCompletions().add("autojoin");
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      UUID id = event.getPlayer().getUniqueId();
      AutoInstance instance = this.playerArena.remove(id);
      this.pending.remove(id);
      if (instance != null) {
         instance.members.remove(id);
      }
   }
   private void sendHelp(Player player) {
      player.sendMessage("§6§lCaveWars §7(Paper 1.21.10 / Java 21)");
      player.sendMessage("§e/cw join §7- dolacz do wolnej areny");
      player.sendMessage("§e/cw autojoin §7- automatyczne dolaczenie / tworzenie kolejnej areny");
      player.sendMessage("§e/cw leave §7- wyjdz");
      player.sendMessage("§e/cw status §7- stan gry");
      if (player.hasPermission("cavewars.admin")) {
         player.sendMessage("§c/cw setlobby");
         player.sendMessage("§c/cw setarena [promien] [wysokosc]");
         player.sendMessage("§c/cw rebuild | start | stop | reload");
      }
   }

   private void autoJoin(Player player) {
      UUID id = player.getUniqueId();
      AutoInstance current = this.playerArena.get(id);
      if (current != null) {
         player.sendMessage(PREFIX + "§eJuz jestes na arenie §f" + current.id + "§e.");
         return;
      }
      if (this.pending.contains(id)) {
         player.sendMessage(PREFIX + "§eArena jest przygotowywana. Poczekaj chwile.");
         return;
      }

      AutoInstance available = this.findAvailable();
      if (available != null) {
         this.joinTo(available, player);
         return;
      }

      this.pending.add(id);
      player.sendMessage(PREFIX + "§eBrak wolnej areny. Tworze nowa...");
      this.ensureArena();
   }

   private void leave(Player player) {
      UUID id = player.getUniqueId();
      AutoInstance instance = this.playerArena.get(id);
      if (instance == null) {
         player.sendMessage(PREFIX + "§eNie jestes w CaveWars.");
         return;
      }
      instance.game.leave(player);
      this.playerArena.remove(id);
      instance.members.remove(id);
   }

   private AutoInstance findAvailable() {
      for (AutoInstance instance : this.instances.values()) {
         GameState state = instance.game.state();
         if (state != GameState.PLAYING && state != GameState.ENDING
            && instance.game.participantsCount() < this.config.maxPlayers()) {
            return instance;
         }
      }
      return null;
   }
   private void joinTo(AutoInstance instance, Player player) {
      UUID id = player.getUniqueId();
      this.playerArena.put(id, instance);
      instance.members.add(id);
      instance.game.join(player);
      if (!instance.game.isParticipant(id)) {
         this.playerArena.remove(id);
         instance.members.remove(id);
         return;
      }
      player.sendMessage(PREFIX + "§7Arena: §f" + instance.id + " §8| §7"
         + instance.game.participantsCount() + "/" + this.config.maxPlayers());
   }

   private void ensureArena() {
      if (this.creating || this.pending.isEmpty()) {
         return;
      }
      if (!this.worlds.getTemplateNames().contains(TEMPLATE)) {
         this.failPending("§cBrak template §f" + TEMPLATE + "§c. Utworz go przez /cww template create " + TEMPLATE + ".");
         return;
      }

      String id = this.nextArenaId();
      this.creating = true;
      this.plugin.getLogger().info("AutoArena: creating " + id + " from template " + TEMPLATE);
      this.worlds.createArena(id, TEMPLATE).whenComplete((world, error) ->
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.creating = false;
            if (error != null || world == null) {
               String message = error == null ? "nieznany blad" : rootMessage(error);
               this.plugin.getLogger().warning("AutoArena create failed: " + message);
               this.failPending("§cNie udalo sie utworzyc areny: §f" + message);
               return;
            }
            try {
               this.registerInstance(id, world);
               this.drainPending();
            } catch (Exception ex) {
               this.plugin.getLogger().severe("AutoArena setup failed: " + ex.getMessage());
               this.failPending("§cNie udalo sie skonfigurowac nowej areny.");
            }
         })
      );
   }
   private String nextArenaId() {
      Set<String> active = new LinkedHashSet<>(this.worlds.getActiveArenaIds());
      while (true) {
         String id = "solo_" + this.nextIndex++;
         if (!active.contains(id) && !this.instances.containsKey(id)) {
            return id;
         }
      }
   }

   private void registerInstance(String id, World world) throws Exception {
      Path folder = this.plugin.getDataFolder().toPath().resolve("auto-arenas").resolve(id);
      Files.createDirectories(folder);

      ArenaData arena = new ArenaData(folder);
      arena.setArena(
         new Location(world, this.baseArena.centerX(), this.baseArena.centerY(), this.baseArena.centerZ()),
         this.baseArena.radius(),
         this.baseArena.height()
      );
      arena.setLobby(this.templateLobby(world));
      arena.save();

      ArenaGenerator generator = new ArenaGenerator(arena);
      GameManager game = new GameManager(this.plugin, this.config, arena, generator);
      AutoInstance instance = new AutoInstance(id, world.getName(), arena, game);
      this.instances.put(id, instance);
      this.plugin.getServer().getPluginManager().registerEvents(new CaveWarsListener(game, arena), this.plugin);
      this.resetBorder(instance);
      this.plugin.getLogger().info("AutoArena ready: " + id + " -> " + world.getName());
   }

   private void drainPending() {
      Iterator<UUID> iterator = this.pending.iterator();
      while (iterator.hasNext()) {
         UUID id = iterator.next();
         Player player = Bukkit.getPlayer(id);
         if (player == null || !player.isOnline()) {
            iterator.remove();
            continue;
         }
         AutoInstance available = this.findAvailable();
         if (available == null) {
            break;
         }
         iterator.remove();
         this.joinTo(available, player);
      }
      if (!this.pending.isEmpty()) {
         this.ensureArena();
      }
   }

   private void failPending(String message) {
      for (UUID id : new LinkedHashSet<>(this.pending)) {
         Player player = Bukkit.getPlayer(id);
         if (player != null) {
            player.sendMessage(PREFIX + message);
         }
      }
      this.pending.clear();
   }

   private void tick() {
      for (AutoInstance instance : this.instances.values()) {
         GameState state = instance.game.state();

         if (state == GameState.STARTING) {
            this.updateLobbyCountdown(instance);
         } else {
            instance.lastCountdown = -1;
         }

         if (state == GameState.PLAYING && instance.lastState != GameState.PLAYING) {
            this.startBorder(instance);
         }

         if (state == GameState.PLAYING && instance.borderEnabled && !instance.shrinkStarted) {
            long elapsed = Math.max(0L, this.config.gameDurationSeconds() - instance.game.gameSecondsRemaining());
            if (elapsed >= instance.borderStartSeconds) {
               this.shrinkBorder(instance);
            }
         }

         if (state == GameState.IDLE && instance.lastState != GameState.IDLE) {
            this.finishInstance(instance);
            this.resetBorder(instance);
         }

         instance.lastState = state;
      }
   }
   private void updateLobbyCountdown(AutoInstance instance) {
      int remaining = (int)instance.game.startSecondsRemaining();
      if (remaining <= 0 || remaining == instance.lastCountdown) {
         return;
      }
      instance.lastCountdown = remaining;
      String bar = "§6§lCaveWars §8| §eStart za §f§l" + remaining + "s";
      if (remaining <= 10 || remaining == 15 || remaining == 30) {
         this.plugin.getLogger().info("AutoArena countdown " + instance.id + ": " + remaining + "s");
      }
      for (UUID id : new LinkedHashSet<>(instance.members)) {
         Player player = Bukkit.getPlayer(id);
         if (player != null && player.isOnline()) {
            player.sendActionBar(bar);
            if (remaining <= 10 || remaining == 15 || remaining == 30) {
               player.playSound(player.getLocation(), "minecraft:block.note_block.pling", 1.0F, 1.2F);
            }
         }
      }
   }

   private void finishInstance(AutoInstance instance) {
      Location spawn = this.mainSpawn();
      for (UUID id : new LinkedHashSet<>(instance.members)) {
         this.playerArena.remove(id);
         Player player = Bukkit.getPlayer(id);
         if (player == null || !player.isOnline()) {
            continue;
         }
         player.getInventory().clear();
         player.setGameMode(GameMode.ADVENTURE);
         if (!player.isDead()) {
            player.setHealth(20.0);
            player.setFoodLevel(20);
            if (spawn != null) {
               player.teleport(spawn);
            }
         }
      }
      instance.members.clear();
   }

   private void startBorder(AutoInstance instance) {
      Properties props = this.readConfig();
      instance.borderEnabled = Boolean.parseBoolean(props.getProperty("border-enabled", "true"));
      instance.borderStartSeconds = parseInt(props, "border-start-seconds", 600);
      instance.borderShrinkSeconds = parseInt(props, "border-shrink-seconds", 180);
      instance.borderFinalSize = parseDouble(props, "border-final-size", 6.0);
      instance.borderDamage = parseDouble(props, "border-damage", 2.0);
      instance.shrinkStarted = false;
      this.resetBorder(instance);
   }

   private void shrinkBorder(AutoInstance instance) {
      World world = Bukkit.getWorld(instance.worldName);
      if (world == null) {
         return;
      }
      WorldBorder border = world.getWorldBorder();
      border.setSize(instance.borderFinalSize, instance.borderShrinkSeconds);
      instance.shrinkStarted = true;
      this.plugin.getLogger().info("AutoArena border shrinking: " + instance.id);
   }
   private void resetBorder(AutoInstance instance) {
      World world = Bukkit.getWorld(instance.worldName);
      if (world == null) {
         return;
      }
      WorldBorder border = world.getWorldBorder();
      border.setCenter(this.baseArena.centerX(), this.baseArena.centerZ());
      if (instance.borderEnabled) {
         border.setDamageAmount(instance.borderDamage);
         try {
            border.setDamageBuffer(0.0);
         } catch (Throwable ignored) {
         }
         border.setSize(Math.max(8.0, this.baseArena.radius() * 2.0));
      } else {
         border.setSize(5.9999968E7);
      }
   }

   private Location templateLobby(World world) {
      try {
         Properties props = new Properties();
         File file = new File(this.plugin.getDataFolder(), "arena.properties");
         try (FileInputStream input = new FileInputStream(file)) {
            props.load(input);
         }
         double x = Double.parseDouble(props.getProperty("lobby.x", "0.5"));
         double y = Double.parseDouble(props.getProperty("lobby.y", "101"));
         double z = Double.parseDouble(props.getProperty("lobby.z", "0.5"));
         float yaw = Float.parseFloat(props.getProperty("lobby.yaw", "0"));
         float pitch = Float.parseFloat(props.getProperty("lobby.pitch", "0"));
         return new Location(world, x, y, z, yaw, pitch);
      } catch (Exception ex) {
         return world.getSpawnLocation();
      }
   }

   private Location mainSpawn() {
      try {
         Properties props = new Properties();
         File file = new File(this.plugin.getDataFolder(), "exit.properties");
         if (file.isFile()) {
            try (FileInputStream input = new FileInputStream(file)) {
               props.load(input);
            }
         }
         World world = Bukkit.getWorld(props.getProperty("spawn.world", "world"));
         if (world == null) {
            return null;
         }
         double x = Double.parseDouble(props.getProperty("spawn.x", "0"));
         double y = Double.parseDouble(props.getProperty("spawn.y", "80"));
         double z = Double.parseDouble(props.getProperty("spawn.z", "0"));
         float yaw = Float.parseFloat(props.getProperty("spawn.yaw", "0"));
         float pitch = Float.parseFloat(props.getProperty("spawn.pitch", "0"));
         return new Location(world, x, y, z, yaw, pitch);
      } catch (Exception ex) {
         World world = Bukkit.getWorld("world");
         return world == null ? null : world.getSpawnLocation();
      }
   }
   private Properties readConfig() {
      Properties props = new Properties();
      File file = new File(this.plugin.getDataFolder(), "config.properties");
      try (FileInputStream input = new FileInputStream(file)) {
         props.load(input);
      } catch (Exception ignored) {
      }
      return props;
   }

   private static int parseInt(Properties props, String key, int fallback) {
      try {
         return Integer.parseInt(props.getProperty(key, Integer.toString(fallback)).trim());
      } catch (Exception ignored) {
         return fallback;
      }
   }

   private static double parseDouble(Properties props, String key, double fallback) {
      try {
         return Double.parseDouble(props.getProperty(key, Double.toString(fallback)).trim());
      } catch (Exception ignored) {
         return fallback;
      }
   }

   private static String rootMessage(Throwable error) {
      Throwable current = error;
      while (current.getCause() != null) {
         current = current.getCause();
      }
      return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
   }

   private static final class AutoInstance {
      final String id;
      final String worldName;
      final ArenaData arena;
      final GameManager game;
      final Set<UUID> members = new LinkedHashSet<>();
      GameState lastState = GameState.IDLE;
      boolean borderEnabled = true;
      boolean shrinkStarted;
      int borderStartSeconds = 600;
      int borderShrinkSeconds = 180;
      double borderFinalSize = 6.0;
      double borderDamage = 2.0;
      int lastCountdown = -1;

      AutoInstance(String id, String worldName, ArenaData arena, GameManager game) {
         this.id = id;
         this.worldName = worldName;
         this.arena = arena;
         this.game = game;
      }
   }
}
