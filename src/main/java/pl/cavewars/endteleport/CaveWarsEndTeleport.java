package pl.cavewars.endteleport;


import pl.cavewars.CaveWarsPlugin;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
public final class CaveWarsEndTeleport implements Listener {
   private final CaveWarsPlugin plugin;

   public CaveWarsEndTeleport(CaveWarsPlugin plugin) {
      this.plugin = plugin;
   }
   private final Set<UUID> participants = new HashSet();
   private final Set<UUID> pendingReturn = new HashSet();
   private String lastState = "";
   private String arenaWorldName;
   private long roundSerial = 0L;

   public void enable() {
      this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
      this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
      this.plugin.getLogger().info("CaveWars end teleport 2.0 enabled: final spawn return after CaveWars finishRound.");
   }

   private void tick() {
      String var1 = this.readGameState();
      if (var1 != null) {
         if (var1.equals("WAITING") || var1.equals("STARTING") || var1.equals("PLAYING") || var1.equals("ENDING")) {
            this.captureArenaPlayers();
         }

         if (var1.equals("ENDING") && !this.lastState.equals("ENDING")) {
            this.pendingReturn.addAll(this.participants);
            long var2 = ++this.roundSerial;
            this.plugin.getLogger().info("Round ending: captured " + this.pendingReturn.size() + " participant(s); waiting for CaveWars finishRound.");
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.finalReturn(var2), 110L);
         }

         if (this.lastState.equals("ENDING") && !var1.equals("ENDING")) {
            this.finalReturn(this.roundSerial);
         }

         if (var1.equals("IDLE") && !this.lastState.equals("ENDING") && this.pendingReturn.isEmpty()) {
            this.participants.clear();
            this.arenaWorldName = null;
         }

         this.lastState = var1;
      }
   }

   private void captureArenaPlayers() {
      for (Player var2 : Bukkit.getOnlinePlayers()) {
         World var3 = var2.getWorld();
         if (var3 != null) {
            String var4 = var3.getName();
            if (this.isArenaWorld(var4)) {
               this.participants.add(var2.getUniqueId());
               this.arenaWorldName = var4;
            }
         }
      }
   }

   private boolean isArenaWorld(String var1) {
      return var1 != null && (var1.startsWith("cw_cavewars_") || var1.startsWith("cw_") || var1.startsWith("Arena_"));
   }

   private void finalReturn(long var1) {
      if (var1 == this.roundSerial && !this.pendingReturn.isEmpty()) {
         Location var3 = this.spawnLocation();
         if (var3 != null) {
            for (Player var5 : Bukkit.getOnlinePlayers()) {
               World var6 = var5.getWorld();
               if (var6 != null && this.isArenaWorld(var6.getName())) {
                  this.pendingReturn.add(var5.getUniqueId());
               }
            }

            int var9 = 0;
            HashSet<UUID> var10 = new HashSet<>();

            for (UUID var7 : new HashSet<UUID>(this.pendingReturn)) {
               Player var8 = Bukkit.getPlayer(var7);
               if (var8 != null && var8.isOnline() && !var8.isDead()) {
                  var8.getInventory().clear();
                  if (var8.teleport(var3)) {
                     var10.add(var7);
                     var9++;
                  }
               }
            }

            this.pendingReturn.removeAll(var10);
            this.plugin.getLogger().info("Final round return: teleported " + var9 + " player(s) to main spawn; pending respawns=" + this.pendingReturn.size() + ".");
            this.scheduleArenaCleanup(var1, 10);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onRespawn(PlayerRespawnEvent var1) {
      UUID var2 = var1.getPlayer().getUniqueId();
      if (this.pendingReturn.contains(var2)) {
         Location var3 = this.spawnLocation();
         if (var3 != null) {
            var1.setRespawnLocation(var3);
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
               Player var3x = Bukkit.getPlayer(var2);
               if (var3x != null && var3x.isOnline()) {
                  var3x.getInventory().clear();
                  var3x.teleport(var3);
               }

               this.pendingReturn.remove(var2);
               this.scheduleArenaCleanup(this.roundSerial, 2);
            }, 1L);
         }
      }
   }

   private void scheduleArenaCleanup(long var1, int var3) {
      if (var1 == this.roundSerial) {
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (var1 == this.roundSerial) {
               boolean var4 = false;
               if (this.arenaWorldName != null) {
                  World var5 = Bukkit.getWorld(this.arenaWorldName);
                  var4 = var5 != null && !var5.getPlayers().isEmpty();
               }

               if (!var4 && this.pendingReturn.isEmpty()) {
                  Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cww end cavewars");
                  this.participants.clear();
                  this.arenaWorldName = null;
                  this.plugin.getLogger().info("Requested CWW arena cleanup after final spawn return.");
               } else if (var3 > 0) {
                  this.scheduleArenaCleanup(var1, var3 - 1);
               }
            }
         }, 10L);
      }
   }

   private Location spawnLocation() {
      World var1 = Bukkit.getWorld("world");
      return var1 == null ? null : new Location(var1, -15.0, 8.0, 5.0, 0.0F, 0.0F);
   }

   private String readGameState() {
      try {
         Plugin var1 = Bukkit.getPluginManager().getPlugin("CaveWars");
         if (var1 == null || !var1.isEnabled()) {
            return null;
         }

         Field var2 = var1.getClass().getDeclaredField("game");
         var2.setAccessible(true);
         Object var3 = var2.get(var1);
         if (var3 == null) {
            return null;
         }

         for (Field var7 : var3.getClass().getDeclaredFields()) {
            if (var7.getType().isEnum() && var7.getType().getSimpleName().equals("GameState")) {
               var7.setAccessible(true);
               Object var8 = var7.get(var3);
               return var8 == null ? null : ((Enum)var8).name();
            }
         }
      } catch (Throwable var9) {
         this.plugin.getLogger().warning("Cannot read CaveWars state: " + var9.getClass().getSimpleName() + ": " + var9.getMessage());
      }

      return null;
   }
}
