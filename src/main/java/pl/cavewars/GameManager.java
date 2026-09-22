package pl.cavewars;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class GameManager {
   static final String PREFIX = "§8[§6CaveWars§8] §f";
   private final CaveWarsPlugin plugin;
   private final CaveConfig config;
   private final ArenaData arena;
   private final ArenaGenerator generator;
   private final Set<UUID> participants = new LinkedHashSet();
   private final Set<UUID> alive = new LinkedHashSet();
   private GameState state = GameState.IDLE;
   private long pvpEnableAtMillis = 0L;
   private long gameEndAtMillis = 0L;
   private int startGeneration = 0;
   private long startAtMillis = 0L;

   GameManager(CaveWarsPlugin var1, CaveConfig var2, ArenaData var3, ArenaGenerator var4) {
      this.plugin = var1;
      this.config = var2;
      this.arena = var3;
      this.generator = var4;
   }

   GameState state() {
      return this.state;
   }

   int participantsCount() {
      return this.participants.size();
   }

   int aliveCount() {
      return this.alive.size();
   }

   boolean isParticipant(UUID var1) {
      return this.participants.contains(var1);
   }

   boolean isAlive(UUID var1) {
      return this.alive.contains(var1);
   }

   boolean isPlaying() {
      return this.state == GameState.PLAYING;
   }

   boolean pvpEnabled() {
      return this.state == GameState.PLAYING && System.currentTimeMillis() >= this.pvpEnableAtMillis;
   }

   long pvpSecondsRemaining() {
      return Math.max(0L, (this.pvpEnableAtMillis - System.currentTimeMillis() + 999L) / 1000L);
   }

   long gameSecondsRemaining() {
      return Math.max(0L, (this.gameEndAtMillis - System.currentTimeMillis() + 999L) / 1000L);
   }

   long startSecondsRemaining() {
      if (this.state != GameState.STARTING || this.startAtMillis <= 0L) {
         return 0L;
      }
      return Math.max(0L, (this.startAtMillis - System.currentTimeMillis() + 999L) / 1000L);
   }

   void join(Player var1) {
      if (!this.arena.hasArena() || !this.arena.hasLobby()) {
         this.tell(var1, "§cArena lub lobby nie jest jeszcze skonfigurowane.");
      } else if (this.state != GameState.PLAYING && this.state != GameState.ENDING) {
         if (this.participants.contains(var1.getUniqueId())) {
            this.tell(var1, "§eJuz jestes w kolejce.");
         } else if (this.participants.size() >= this.config.maxPlayers()) {
            this.tell(var1, "§cKolejka jest pelna.");
         } else {
            this.participants.add(var1.getUniqueId());
            if (this.state == GameState.IDLE) {
               this.state = GameState.WAITING;
            }

            Location var2 = this.arena.lobby(this.plugin.getServer());
            if (var2 != null) {
               var1.teleport(var2);
            }

            var1.setGameMode(GameMode.ADVENTURE);
            var1.getInventory().clear();
            this.broadcast("§e" + var1.getName() + " §7dolaczyl. §f(" + this.participants.size() + "/" + this.config.maxPlayers() + ")");
            this.tell(var1, "§7Aby wyjsc z CaveWars na spawn wpisz §e/cw leave§7.");
            this.maybeScheduleAutoStart();
         }
      } else {
         this.tell(var1, "§cMecz juz trwa. Poczekaj na nastepna runde.");
      }
   }

   void leave(Player var1) {
      UUID var2 = var1.getUniqueId();
      if (!this.participants.remove(var2)) {
         this.tell(var1, "§eNie jestes w CaveWars.");
      } else {
         boolean var3 = this.alive.remove(var2);
         Location var4 = this.mainSpawn();
         if (var4 != null) {
            var1.teleport(var4);
         }

         var1.setGameMode(GameMode.ADVENTURE);
         var1.getInventory().clear();
         this.broadcast("§e" + var1.getName() + " §7opuscil CaveWars.");
         if (this.state == GameState.PLAYING && var3) {
            this.checkForWinner();
         }

         if (this.state == GameState.WAITING || this.state == GameState.STARTING) {
            if (this.participants.size() < this.config.minPlayers()) {
               this.startGeneration++;
               this.startAtMillis = 0L;
               this.state = this.participants.isEmpty() ? GameState.IDLE : GameState.WAITING;
               if (!this.participants.isEmpty()) {
                  this.broadcast("§cStart anulowany — za malo graczy.");
               }
            } else {
               this.maybeScheduleAutoStart();
            }
         }
      }
   }

   void handleQuit(Player var1) {
      UUID var2 = var1.getUniqueId();
      boolean var3 = this.participants.remove(var2);
      boolean var4 = this.alive.remove(var2);
      if (var3) {
         if (this.state == GameState.PLAYING && var4) {
            this.checkForWinner();
         }

         if (this.state == GameState.WAITING || this.state == GameState.STARTING) {
            if (this.participants.size() < this.config.minPlayers()) {
               this.startGeneration++;
               this.startAtMillis = 0L;
               this.state = this.participants.isEmpty() ? GameState.IDLE : GameState.WAITING;
               if (!this.participants.isEmpty()) {
                  this.broadcast("§cStart anulowany — za malo graczy.");
               }
            } else {
               this.maybeScheduleAutoStart();
            }
         }
      }
   }

   void forceStart(Player var1) {
      if (this.state != GameState.PLAYING && this.state != GameState.ENDING) {
         if (this.participants.size() < 2) {
            this.tell(var1, "§cDo startu potrzeba co najmniej 2 graczy.");
         } else {
            this.startGeneration++;
            this.startAtMillis = 0L;
            this.startGame();
         }
      } else {
         this.tell(var1, "§cMecz juz trwa.");
      }
   }

   void stop(String var1) {
      if (this.state != GameState.IDLE || !this.participants.isEmpty()) {
         this.startGeneration++;
         this.broadcast("§cGra zatrzymana: §f" + var1);
         this.finishRound(false);
      }
   }

   private void maybeScheduleAutoStart() {
      if ((this.state == GameState.WAITING || this.state == GameState.STARTING)
         && this.participants.size() >= this.config.minPlayers()) {
         int delaySeconds = this.participants.size() >= 3 ? 15 : 30;

         if (this.state == GameState.STARTING && this.startAtMillis > 0L
            && this.startSecondsRemaining() <= delaySeconds) {
            return;
         }

         this.state = GameState.STARTING;
         int generation = ++this.startGeneration;
         this.startAtMillis = System.currentTimeMillis() + delaySeconds * 1000L;
         this.broadcast("§aStart za §f" + delaySeconds + " s§a. Gracze: §f" + this.participants.size());

         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (generation == this.startGeneration && this.state == GameState.STARTING) {
               if (this.participants.size() < this.config.minPlayers()) {
                  this.startAtMillis = 0L;
                  this.state = this.participants.isEmpty() ? GameState.IDLE : GameState.WAITING;
                  this.broadcast("§cStart anulowany — za malo graczy.");
               } else {
                  this.startAtMillis = 0L;
                  this.startGame();
               }
            }
         }, delaySeconds * 20L);
      }
   }

   private void startGame() {
      this.startAtMillis = 0L;
      if (this.arena.hasArena() && this.arena.hasLobby()) {
         World var1 = this.arena.world(this.plugin.getServer());
         if (var1 == null) {
            this.state = GameState.WAITING;
            this.broadcast("§cSwiat areny nie jest zaladowany: " + this.arena.worldName());
         } else {
            ArrayList<Player> var2 = new ArrayList<>();

            for (UUID var4 : new ArrayList<UUID>(this.participants)) {
               Player var5 = this.plugin.getServer().getPlayer(var4);
               if (var5 == null) {
                  this.participants.remove(var4);
               } else {
                  var2.add(var5);
               }
            }

            if (var2.size() < 2) {
               this.state = var2.isEmpty() ? GameState.IDLE : GameState.WAITING;
               this.broadcast("§cStart anulowany — za malo graczy online.");
            } else {
               Collections.shuffle(var2);

               List<Location> var8;
               try {
                  var8 = this.generator.carveSpawnPockets(var1, var2.size());
               } catch (RuntimeException var7) {
                  this.state = GameState.WAITING;
                  this.broadcast("§cNie mozna przygotowac spawnów: " + var7.getMessage());
                  return;
               }

               this.alive.clear();

               for (int var9 = 0; var9 < var2.size(); var9++) {
                  Player var11 = (Player)var2.get(var9);
                  this.alive.add(var11.getUniqueId());
                  this.preparePlayerForMatch(var11, (Location)var8.get(var9));
               }

               this.state = GameState.PLAYING;
               long var10 = System.currentTimeMillis();
               this.pvpEnableAtMillis = var10 + this.config.pvpAfterSeconds() * 1000L;
               this.gameEndAtMillis = var10 + this.config.gameDurationSeconds() * 1000L;
               int var6 = this.startGeneration;
               this.broadcast(
                  "§a§lSTART! §7Gra trwa §f"
                     + formatTime(this.config.gameDurationSeconds())
                     + "§7. PvP wlaczy sie za §f"
                     + formatTime(this.config.pvpAfterSeconds())
                     + "§7."
               );
               if (this.config.pvpAfterSeconds() > 0) {
                  this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                     if (var6 == this.startGeneration && this.state == GameState.PLAYING) {
                        this.broadcast("§c§lPvP WLACZONE! §7Mozecie sie teraz atakowac.");
                     }
                  }, this.config.pvpAfterSeconds() * 20L);
               }

               this.scheduleGameTimerTick(var6);
               this.scheduleHudTick(var6);
            }
         }
      } else {
         this.state = GameState.WAITING;
         this.broadcast("§cBrak ustawionej areny lub lobby.");
      }
   }

   private void scheduleGameTimerTick(int var1) {
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         if (var1 == this.startGeneration && this.state == GameState.PLAYING) {
            long var2 = this.gameSecondsRemaining();
            if (var2 <= 0L) {
               this.endByTime();
            } else {
               if (this.shouldAnnounceTime(var2)) {
                  this.broadcast("§eCzas do konca: §f§l" + formatTime(var2));
               }

               this.scheduleGameTimerTick(var1);
            }
         }
      }, 20L);
   }

   private void scheduleHudTick(int generation) {
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         if (generation == this.startGeneration && this.state == GameState.PLAYING) {
            this.sendHudActionBar();
            this.scheduleHudTick(generation);
         }
      }, 10L);
   }

   private void sendHudActionBar() {
      long remaining = this.gameSecondsRemaining();
      String pvp = this.pvpEnabled() ? "§cON" : "§a" + formatTime(this.pvpSecondsRemaining());

      for (UUID id : new ArrayList<UUID>(this.participants)) {
         Player player = this.plugin.getServer().getPlayer(id);
         if (player == null) {
            continue;
         }

         String hud = "§6§lCaveWars §8| §eCzas: §f§l" + formatTime(remaining) + " §8| §7PvP: " + pvp;
         double borderDistance = this.borderDistance(player);
         if (!Double.isNaN(borderDistance)) {
            hud += " §8| §bGranica jest §f" + String.format(Locale.US, "%.2f", borderDistance) + "m §bod Ciebie";
         }
         player.sendActionBar(hud);
      }
   }

   private double borderDistance(Player player) {
      World arenaWorld = this.arena.world(this.plugin.getServer());
      if (arenaWorld == null || player.getWorld() != arenaWorld) {
         return Double.NaN;
      }

      WorldBorder border = arenaWorld.getWorldBorder();
      Location center = border.getCenter();
      Location pos = player.getLocation();
      double halfSize = border.getSize() / 2.0;
      double toXEdge = halfSize - Math.abs(pos.getX() - center.getX());
      double toZEdge = halfSize - Math.abs(pos.getZ() - center.getZ());
      return Math.max(0.0, Math.min(toXEdge, toZEdge));
   }

   private boolean shouldAnnounceTime(long var1) {
      return var1 == 300L || var1 == 180L || var1 == 120L || var1 == 60L || var1 == 30L || var1 == 20L || var1 == 10L || var1 <= 5L && var1 >= 1L;
   }

   private void endByTime() {
      if (this.state == GameState.PLAYING) {
         this.state = GameState.ENDING;
         this.startGeneration++;
         this.broadcast("§6§lKONIEC CZASU! §7Minelo §f" + formatTime(this.config.gameDurationSeconds()) + "§7.");
         if (this.alive.size() > 1) {
            this.broadcast("§eRunda zakonczona remisem. Przy zyciu zostalo: §f" + this.alive.size());
         } else if (this.alive.size() == 1) {
            Player var1 = this.plugin.getServer().getPlayer((UUID)this.alive.iterator().next());
            if (var1 != null) {
               this.plugin.awardWin(var1);
               this.broadcast("§6§lWYGRYWA: §e" + var1.getName() + "§6!");
            }
         }

         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.finishRound(true), 100L);
      }
   }

   private void preparePlayerForMatch(Player var1, Location var2) {
      var1.getInventory().clear();
      var1.setGameMode(GameMode.SURVIVAL);
      var1.setHealth(20.0);
      var1.setFoodLevel(20);
      var1.setSaturation(10.0F);
      var1.setLevel(0);
      var1.setExp(0.0F);
      var1.teleport(var2);
      var1.getInventory().addItem(new ItemStack[]{new ItemStack(Material.WOODEN_PICKAXE, 1)});
      if (this.config.starterBread() > 0) {
         var1.getInventory().addItem(new ItemStack[]{new ItemStack(Material.BREAD, this.config.starterBread())});
      }

      if (this.config.starterTorches() > 0) {
         var1.getInventory().addItem(new ItemStack[]{new ItemStack(Material.TORCH, this.config.starterTorches())});
      }
   }

   private Location mainSpawn() {
      try {
         Properties props = new Properties();
         File file = new File(this.plugin.getDataFolder(), "exit.properties");
         if (file.isFile()) {
            try (FileInputStream in = new FileInputStream(file)) {
               props.load(in);
            }
         }

         World world = this.plugin.getServer().getWorld(props.getProperty("spawn.world", "world"));
         if (world == null) {
            return null;
         }

         double x = Double.parseDouble(props.getProperty("spawn.x", "-15.0"));
         double y = Double.parseDouble(props.getProperty("spawn.y", "8.0"));
         double z = Double.parseDouble(props.getProperty("spawn.z", "5.0"));
         float yaw = Float.parseFloat(props.getProperty("spawn.yaw", "0.0"));
         float pitch = Float.parseFloat(props.getProperty("spawn.pitch", "0.0"));
         return new Location(world, x, y, z, yaw, pitch);
      } catch (Exception ex) {
         World world = this.plugin.getServer().getWorld("world");
         return world == null ? null : world.getSpawnLocation();
      }
   }

   void onDeath(Player var1) {
      UUID var2 = var1.getUniqueId();
      if (this.state == GameState.PLAYING && this.alive.remove(var2)) {
         this.broadcast("§c" + var1.getName() + " §7odpada. Zostalo: §f" + this.alive.size());
         this.checkForWinner();
      }
   }

   void onRespawn(Player var1) {
      UUID var2 = var1.getUniqueId();
      if (this.participants.contains(var2)) {
         boolean var4 = this.state == GameState.PLAYING && !this.alive.contains(var2);
         Location var3;
         if (var4) {
            var3 = this.arena.center(this.plugin.getServer());
         } else {
            var3 = this.arena.lobby(this.plugin.getServer());
         }

         if (var3 != null) {
            Location var5 = var3;
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
               var1.teleport(var5);
               var1.setGameMode(var4 ? GameMode.SPECTATOR : GameMode.ADVENTURE);
            }, 1L);
         }
      }
   }

   private void checkForWinner() {
      if (this.state == GameState.PLAYING) {
         if (this.alive.size() <= 1) {
            Player var1 = null;
            if (this.alive.size() == 1) {
               var1 = this.plugin.getServer().getPlayer((UUID)this.alive.iterator().next());
            }

            this.state = GameState.ENDING;
            this.startGeneration++;
            if (var1 != null) {
               this.plugin.awardWin(var1);
               this.broadcast("§6§lWYGRYWA: §e" + var1.getName() + "§6!");
            } else {
               this.broadcast("§eRunda zakonczona bez zwyciezcy.");
            }

            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.finishRound(true), 100L);
         }
      }
   }

   private void finishRound(boolean var1) {
      this.state = GameState.ENDING;
      World var2 = this.arena.world(this.plugin.getServer());
      Location var3 = var2 == null ? this.arena.lobby(this.plugin.getServer()) : new Location(var2, -16.0, 8.0, 6.0);

      for (UUID var5 : new ArrayList<UUID>(this.participants)) {
         Player var6 = this.plugin.getServer().getPlayer(var5);
         if (var6 != null) {
            var6.getInventory().clear();
            var6.sendActionBar("");
            var6.setGameMode(GameMode.ADVENTURE);
            var6.setHealth(20.0);
            var6.setFoodLevel(20);
            var6.setSaturation(5.0F);
            if (var3 != null) {
               var6.teleport(var3);
            }
         }
      }

      this.participants.clear();
      this.alive.clear();
      this.pvpEnableAtMillis = 0L;
      this.gameEndAtMillis = 0L;
      this.startAtMillis = 0L;
      this.state = GameState.IDLE;
      if (var1 && this.config.resetAfterGame() && this.arena.hasArena()) {
         World var8 = this.arena.world(this.plugin.getServer());
         if (var8 != null) {
            try {
               int var9 = this.generator.rebuild(var8);
               this.plugin.getLogger().info("Arena CaveWars zresetowana: " + var9 + " blokow.");
            } catch (RuntimeException var7) {
               this.plugin.getLogger().severe("Reset areny nie udal sie: " + var7.getMessage());
            }
         }
      }
   }

   void shutdown() {
      this.startGeneration++;
      this.startAtMillis = 0L;
      if (!this.participants.isEmpty()) {
         this.finishRound(false);
      }
   }

   boolean canModifyArena(Player var1) {
      if (!this.arena
         .contains(var1.getLocation().getWorld().getBlockAt(var1.getLocation().getBlockX(), var1.getLocation().getBlockY(), var1.getLocation().getBlockZ()))) {
         return true;
      } else {
         return this.state == GameState.PLAYING ? this.alive.contains(var1.getUniqueId()) : var1.hasPermission("cavewars.admin");
      }
   }

   void broadcast(String var1) {
      for (UUID var3 : new ArrayList<UUID>(this.participants)) {
         Player var4 = this.plugin.getServer().getPlayer(var3);
         if (var4 != null) {
            var4.sendMessage("§8[§6CaveWars§8] §f" + var1);
         }
      }
   }

   void tell(Player var1, String var2) {
      var1.sendMessage("§8[§6CaveWars§8] §f" + var2);
   }

   String statusLine() {
      String var1 = "";
      if (this.state == GameState.PLAYING) {
         var1 = " §8| §7czas: §f"
            + formatTime(this.gameSecondsRemaining())
            + " §8| §7PvP: "
            + (this.pvpEnabled() ? "§cON" : "§a" + formatTime(this.pvpSecondsRemaining()));
      }

      return "§7Stan: §f"
         + this.state
         + " §8| §7gracze: §f"
         + this.participants.size()
         + "/"
         + this.config.maxPlayers()
         + " §8| §7zywi: §f"
         + this.alive.size()
         + var1;
   }

   private static String formatTime(long var0) {
      long var2 = Math.max(0L, var0) / 60L;
      long var4 = Math.max(0L, var0) % 60L;
      return String.format("%d:%02d", new Object[]{var2, var4});
   }
}
