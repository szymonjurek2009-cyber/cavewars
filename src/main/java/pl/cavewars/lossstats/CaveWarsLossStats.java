package pl.cavewars.lossstats;


import pl.cavewars.CaveWarsPlugin;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class CaveWarsLossStats implements Listener, CommandExecutor {
   private final CaveWarsPlugin plugin;

   public CaveWarsLossStats(CaveWarsPlugin plugin) {
      this.plugin = plugin;
      Holder.INSTANCE = this;
   }
   private final Map<UUID, Long> losses = new ConcurrentHashMap();
   private final Map<UUID, String> names = new ConcurrentHashMap();
   private final Set<UUID> participants = new LinkedHashSet();
   private final Set<UUID> eliminated = new LinkedHashSet();
   private File store;
   private Object game;
   private Method stateM;
   private Method isParticipantM;
   private Method isAliveM;
   private Method remainingM;
   private String lastState = "IDLE";
   private long lastRemaining = Long.MAX_VALUE;
   private UUID aliveCandidate;

   public void enable() {
      if (!this.dataFolder().exists()) {
         this.dataFolder().mkdirs();
      }

      this.store = new File(this.dataFolder(), "losses.properties");
      this.loadOrImport();
      this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
      PluginCommand var1 = this.plugin.getCommand("staty");
      if (var1 != null) {
         var1.setExecutor(this);
      }

      PluginCommand var2 = this.plugin.getCommand("przegrane");
      if (var2 != null) {
         var2.setExecutor(this);
      }

      this.bindGame();
      this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tick, 20L, 1L);
      this.plugin.getLogger().info("Loss stats enabled. /topki przegrane + /staty");
   }

   public void disable() {
      this.save();
   }

   private void bindGame() {
      try {
         Plugin var1 = this.plugin.getServer().getPluginManager().getPlugin("CaveWars");
         if (var1 == null) {
            return;
         }

         Field var2 = var1.getClass().getDeclaredField("game");
         var2.setAccessible(true);
         this.game = var2.get(var1);
         Class var3 = this.game.getClass();
         this.stateM = var3.getDeclaredMethod("state", new Class[0]);
         this.stateM.setAccessible(true);
         this.isParticipantM = var3.getDeclaredMethod("isParticipant", new Class[]{UUID.class});
         this.isParticipantM.setAccessible(true);
         this.isAliveM = var3.getDeclaredMethod("isAlive", new Class[]{UUID.class});
         this.isAliveM.setAccessible(true);
         this.remainingM = var3.getDeclaredMethod("gameSecondsRemaining", new Class[0]);
         this.remainingM.setAccessible(true);
      } catch (Throwable var4) {
         this.plugin.getLogger().warning("Nie mozna podpiac GameManager: " + var4.getMessage());
      }
   }

   private void tick() {
      try {
         if (this.game == null) {
            this.bindGame();
            if (this.game == null) {
               return;
            }
         }

         String var1 = String.valueOf(this.stateM.invoke(this.game, new Object[0]));
         if (var1.equals("WAITING") || var1.equals("STARTING") || var1.equals("PLAYING")) {
            for (Player var3 : this.plugin.getServer().getOnlinePlayers()) {
               UUID var4 = var3.getUniqueId();
               if (Boolean.TRUE.equals(this.isParticipantM.invoke(this.game, new Object[]{var4}))) {
                  this.participants.add(var4);
                  this.names.put(var4, var3.getName());
               }
            }
         }

         if (var1.equals("PLAYING")) {
            long var9 = ((Number)this.remainingM.invoke(this.game, new Object[0])).longValue();
            this.lastRemaining = var9;
            UUID var10 = null;
            int var5 = 0;

            for (UUID var7 : this.participants) {
               if (Boolean.TRUE.equals(this.isAliveM.invoke(this.game, new Object[]{var7}))) {
                  var5++;
                  var10 = var7;
               }
            }

            if (var5 == 1) {
               this.aliveCandidate = var10;
            }
         }

         if (this.lastState.equals("PLAYING") && !var1.equals("PLAYING")) {
            this.finishIfRealRound();
         }

         if (var1.equals("IDLE") && !this.lastState.equals("PLAYING")) {
            this.participants.clear();
            this.eliminated.clear();
            this.aliveCandidate = null;
            this.lastRemaining = Long.MAX_VALUE;
         }

         this.lastState = var1;
      } catch (Throwable var8) {
         this.plugin.getLogger().warning("Loss tracker tick: " + var8.getClass().getSimpleName() + ": " + var8.getMessage());
         this.game = null;
      }
   }

   private void finishIfRealRound() {
      if (this.participants.size() < 2) {
         this.resetRound();
      } else {
         LinkedHashSet<UUID> var1 = new LinkedHashSet<>(this.participants);
         var1.removeAll(this.eliminated);
         UUID var2 = var1.size() == 1 ? (UUID)var1.iterator().next() : this.aliveCandidate;
         boolean var3 = var2 != null || !this.eliminated.isEmpty() || this.lastRemaining <= 1L;
         if (!var3) {
            this.resetRound();
         } else {
            for (UUID var5 : this.participants) {
               if (var2 == null || !var2.equals(var5)) {
                  this.losses.merge(var5, 1L, Long::sum);
               }
            }

            this.save();
            this.plugin.getLogger()
               .info(
                  "Round losses recorded: players="
                     + this.participants.size()
                     + ", winner="
                     + (var2 == null ? "none" : (String)this.names.getOrDefault(var2, var2.toString()))
               );
            this.resetRound();
         }
      }
   }

   private void resetRound() {
      this.participants.clear();
      this.eliminated.clear();
      this.aliveCandidate = null;
      this.lastRemaining = Long.MAX_VALUE;
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onDeath(PlayerDeathEvent var1) {
      UUID var2 = var1.getEntity().getUniqueId();
      if (this.participants.contains(var2)) {
         this.eliminated.add(var2);
         this.names.put(var2, var1.getEntity().getName());
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent var1) {
      UUID var2 = var1.getPlayer().getUniqueId();
      if (this.participants.contains(var2) && this.lastState.equals("PLAYING")) {
         this.eliminated.add(var2);
      }

      this.names.put(var2, var1.getPlayer().getName());
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onCommand(PlayerCommandPreprocessEvent var1) {
      String var2 = var1.getMessage();
      if (var2 != null) {
         String[] var3 = var2.trim().split("\\s+");
         if (var3.length >= 2 && var3[0].equalsIgnoreCase("/topki") && this.isLossWord(var3[1])) {
            var1.setCancelled(true);
            this.sendTop(var1.getPlayer());
         }
      }
   }

   private boolean isLossWord(String var1) {
      return var1.equalsIgnoreCase("przegrane") || var1.equalsIgnoreCase("porazki") || var1.equalsIgnoreCase("porażki") || var1.equalsIgnoreCase("losses");
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var3.equalsIgnoreCase("przegrane")) {
         this.sendTop(var1);
         return true;
      }

      if (var3.equalsIgnoreCase("staty")) {
         Player var5 = var1 instanceof Player var6 ? var6 : null;
         String var12 = var4.length > 0 ? var4[0] : (var5 == null ? null : var5.getName());
         PlayerData var7 = this.findPlayer(var12);
         if (var7 == null) {
            var1.sendMessage("§cNie znaleziono gracza.");
            return true;
         } else {
            long var8 = (Long)this.losses.getOrDefault(var7.id, 0L);
            double var10 = var7.deaths == 0L ? var7.kills : (double)var7.kills / var7.deaths;
            var1.sendMessage("§6§lSTATYSTYKI §f" + var7.name);
            var1.sendMessage("§aWygrane rundy: §f" + var7.wins);
            var1.sendMessage("§6Przegrane rundy: §f" + var8);
            var1.sendMessage("§eZabójstwa graczy: §f" + var7.kills);
            var1.sendMessage("§cZgony: §f" + var7.deaths);
            var1.sendMessage(String.format(Locale.US, "§bK/D: §f%.2f", new Object[]{var10}));
            var1.sendMessage("§dCzas gry: §f" + this.formatTicks(var7.ticks));
            return true;
         }
      } else {
         return false;
      }
   }

   private void sendTop(CommandSender var1) {
      ArrayList<Entry<UUID, Long>> var2 = new ArrayList<>(this.losses.entrySet());
      var2.sort((var0, var1x) -> Long.compare((Long)var1x.getValue(), (Long)var0.getValue()));
      var1.sendMessage("§6§lTOP 10 PRZEGRANE");
      int var3 = 0;

      for (Entry<UUID, Long> var5 : var2) {
         if ((Long)var5.getValue() > 0L) {
            var1.sendMessage(
               "§e" + ++var3 + ". §f" + (String)this.names.getOrDefault(var5.getKey(), ((UUID)var5.getKey()).toString()) + " §7- §c" + var5.getValue()
            );
            if (var3 >= 10) {
               break;
            }
         }
      }

      if (var3 == 0) {
         var1.sendMessage("§7Brak danych graczy.");
      }
   }

   public static String appendLossLine(String var0, String var1) {
      CaveWarsLossStats var2 = Holder.INSTANCE;
      if (var2 == null) {
         return var0;
      }

      long var3 = var2.lossesByName(var1);
      return var0 + "\n§6Przegrane rundy: §f" + var3;
   }

   private long lossesByName(String var1) {
      if (var1 == null) {
         return 0L;
      }

      for (Entry var3 : this.names.entrySet()) {
         if (((String)var3.getValue()).equalsIgnoreCase(var1)) {
            return (Long)this.losses.getOrDefault(var3.getKey(), 0L);
         }
      }

      return 0L;
   }

   private PlayerData findPlayer(String var1) {
      if (var1 == null) {
         return null;
      }

      for (PlayerData var3 : this.readTopki()) {
         if (var3.name.equalsIgnoreCase(var1) || var3.id.toString().equalsIgnoreCase(var1)) {
            return var3;
         }
      }

      return null;
   }

   private List<PlayerData> readTopki() {
      File var1 = new File(this.dataFolder().getParentFile(), "Topki/players.yml");
      ArrayList<PlayerData> var2 = new ArrayList<>();

      try {
         List<String> var3 = Files.readAllLines(var1.toPath(), StandardCharsets.UTF_8);
         UUID var4 = null;
         String var5 = null;
         long var6 = 0L;
         long var8 = 0L;
         long var10 = 0L;
         long var12 = 0L;
         boolean var14 = false;

         for (String var16 : var3) {
            String var17 = var16.trim();
            if (var16.startsWith("  ") && !var16.startsWith("    ") && var17.endsWith(":")) {
               if (var14 && var4 != null) {
                  var2.add(new PlayerData(var4, var5 == null ? var4.toString() : var5, var6, var8, var10, var12));
               }

               var14 = false;

               try {
                  var4 = UUID.fromString(var17.substring(0, var17.length() - 1));
                  var14 = true;
                  var5 = null;
                  var12 = 0L;
                  var10 = 0L;
                  var8 = 0L;
                  var6 = 0L;
               } catch (Exception var24) {
                  var4 = null;
               }
            } else if (var14 && var16.startsWith("    ")) {
               int var18 = var17.indexOf(58);
               if (var18 > 0) {
                  String var19 = var17.substring(0, var18);
                  String var20 = var17.substring(var18 + 1).trim();
                  if (var19.equals("name")) {
                     var5 = var20;
                  } else {
                     try {
                        long var21 = Long.parseLong(var20);
                        if (var19.equals("kills")) {
                           var6 = var21;
                        } else if (var19.equals("deaths")) {
                           var8 = var21;
                        } else if (var19.equals("wins")) {
                           var10 = var21;
                        } else if (var19.equals("ticks")) {
                           var12 = var21;
                        }
                     } catch (Exception var23) {
                     }
                  }
               }
            }
         }

         if (var14 && var4 != null) {
            var2.add(new PlayerData(var4, var5 == null ? var4.toString() : var5, var6, var8, var10, var12));
         }
      } catch (Exception var25) {
      }

      return var2;
   }

   private String formatTicks(long var1) {
      long var3 = var1 / 20L;
      long var5 = var3 / 3600L;
      long var7 = var3 % 3600L / 60L;
      return var5 + "h " + var7 + "m";
   }

   private void loadOrImport() {
      Properties var1 = new Properties();
      if (this.store.exists()) {
         try {
            FileInputStream var2 = new FileInputStream(this.store);

            try {
               var1.load(var2);
            } catch (Throwable var8) {
               try {
                  var2.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }

               throw var8;
            }

            var2.close();
         } catch (Exception var9) {
         }
      }

      for (String var3 : var1.stringPropertyNames()) {
         if (var3.endsWith(".losses")) {
            try {
               UUID var4 = UUID.fromString(var3.substring(0, var3.length() - 7));
               this.losses.put(var4, Long.parseLong(var1.getProperty(var3, "0")));
            } catch (Exception var6) {
            }
         } else if (var3.endsWith(".name")) {
            try {
               UUID var13 = UUID.fromString(var3.substring(0, var3.length() - 5));
               this.names.put(var13, var1.getProperty(var3));
            } catch (Exception var5) {
            }
         }
      }

      if (!"true".equals(var1.getProperty("initialized"))) {
         for (PlayerData var12 : this.readTopki()) {
            this.losses.putIfAbsent(var12.id, var12.deaths);
            this.names.put(var12.id, var12.name);
         }

         this.save();
      }
   }

   private synchronized void save() {
      if (this.store != null) {
         Properties var1 = new Properties();
         var1.setProperty("initialized", "true");

         for (Entry var3 : this.losses.entrySet()) {
            var1.setProperty(var3.getKey() + ".losses", Long.toString((Long)var3.getValue()));
         }

         for (Entry var10 : this.names.entrySet()) {
            var1.setProperty(var10.getKey() + ".name", (String)var10.getValue());
         }

         try {
            FileOutputStream var9 = new FileOutputStream(this.store);

            try {
               var1.store(var9, "CaveWars losses by UUID");
            } catch (Throwable var6) {
               try {
                  var9.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }

               throw var6;
            }

            var9.close();
         } catch (Exception var7) {
            this.plugin.getLogger().warning("Nie mozna zapisac losses.properties: " + var7.getMessage());
         }
      }
   }

   public static long lossesValue(String var0) {
      return Holder.INSTANCE.lossesByName(var0);
   }
   private File dataFolder() {
      File dir = new File(this.plugin.getDataFolder().getParentFile(), "CaveWarsLossStats");
      if (!dir.exists()) dir.mkdirs();
      return dir;
   }

   private static final class Holder {
      static CaveWarsLossStats INSTANCE;
      private Holder() {}
   }

   private record PlayerData(UUID id, String name, long kills, long deaths, long wins, long ticks) {}

}
