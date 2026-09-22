package pl.cavewars.rules;


import pl.cavewars.CaveWarsPlugin;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

public final class CaveWarsRulesPatch implements Listener {
   private final CaveWarsPlugin plugin;

   public CaveWarsRulesPatch(CaveWarsPlugin plugin) {
      this.plugin = plugin;
   }
   private static final int RAW_IRON_TARGET = 6;
   private static final String CAVEWARS = "CaveWars";
   private static final String BUNGEE = "BungeeCord";
   private File pendingFile;
   private final Properties pending = new Properties();
   private boolean lastPlaying = false;
   private boolean shrinkStarted = false;
   private String lastWorldName = "";

   public void enable() {
      this.dataFolder().mkdirs();
      this.pendingFile = new File(this.dataFolder(), "pending-return.properties");
      this.loadPending();
      this.applyCaveWarsConfig();
      this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);

      try {
         this.plugin.getServer().getMessenger().registerOutgoingPluginChannel(this.plugin, "BungeeCord");
      } catch (Throwable var2) {
      }

      this.disableAdvancementAnnouncements();
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, this::enrichCurrentArena, 100L);
      this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tick, 20L, 20L);
      this.plugin.getLogger().info("CaveWarsRulesPatch enabled: lobby dynamic 30s/15s, max 4, border 10m, raw iron, apples 10%, reconnect cleanup.");
   }

   private void applyCaveWarsConfig() {
      File var1 = new File("plugins/CaveWars/config.properties");
      Properties var2 = new Properties();

      try {
         InputStream var3 = Files.newInputStream(var1.toPath(), new OpenOption[0]);

         try {
            var2.load(var3);
         } catch (Throwable var8) {
            if (var3 != null) {
               try {
                  var3.close();
               } catch (Throwable var6) {
                  var8.addSuppressed(var6);
               }
            }

            throw var8;
         }

         if (var3 != null) {
            var3.close();
         }
      } catch (Exception var11) {
         this.plugin.getLogger().warning("Cannot read CaveWars config: " + var11.getMessage());
         return;
      }

      var2.setProperty("auto-start-seconds", "30");
      var2.setProperty("max-players", "4");
      var2.setProperty("border-enabled", "true");
      var2.setProperty("border-start-seconds", "600");
      var2.setProperty("border-shrink-seconds", "180");
      var2.setProperty("border-damage", "2");
      var2.setProperty("border-final-size", "6");

      try {
         BufferedWriter var12 = Files.newBufferedWriter(var1.toPath(), StandardCharsets.UTF_8, new OpenOption[0]);

         try {
            var2.store(var12, "CaveWars rules - managed by CaveWarsRulesPatch");
         } catch (Throwable var9) {
            if (var12 != null) {
               try {
                  var12.close();
               } catch (Throwable var7) {
                  var9.addSuppressed(var7);
               }
            }

            throw var9;
         }

         if (var12 != null) {
            var12.close();
         }
      } catch (Exception var10) {
         this.plugin.getLogger().warning("Cannot write CaveWars config: " + var10.getMessage());
      }
   }

   private void tick() {
      try {
         ArenaInfo var1 = this.readArena();
         if (var1 != null && !Objects.equals(this.lastWorldName, var1.world)) {
            this.lastWorldName = var1.world;
            this.shrinkStarted = false;
            this.prepareBorder(var1, false);
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, this::enrichCurrentArena, 80L);
         }

         Object var2 = this.getGameManager();
         if (var2 == null) {
            return;
         }

         boolean var3 = this.invokeBoolean(var2, "isPlaying");
         if (var3 && !this.lastPlaying) {
            this.shrinkStarted = false;
            if (var1 != null) {
               this.prepareBorder(var1, false);
            }

            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, this::enrichCurrentArena, 40L);
         }

         if (var3 && !this.shrinkStarted) {
            long var4 = this.invokeLong(var2, "gameSecondsRemaining");
            int var6 = this.readIntConfig("game-duration-seconds", 900);
            int var7 = this.readIntConfig("border-start-seconds", 600);
            long var8 = Math.max(0L, var6 - var4);
            if (var8 >= var7 && var1 != null) {
               this.prepareBorder(var1, true);
               this.shrinkStarted = true;
            }
         }

         if (!var3 && this.lastPlaying) {
            this.shrinkStarted = false;
            if (var1 != null) {
               this.prepareBorder(var1, false);
            }

            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, this::enrichCurrentArena, 100L);
         }

         this.lastPlaying = var3;
      } catch (Throwable var10) {
         this.plugin.getLogger().warning("Rules tick failed: " + var10.getMessage());
      }
   }

   private void prepareBorder(ArenaInfo var1, boolean var2) {
      World var3 = this.plugin.getServer().getWorld(var1.world);
      if (var3 != null) {
         WorldBorder var4 = var3.getWorldBorder();
         var4.setCenter(var1.x, var1.z);
         var4.setDamageAmount(this.readDoubleConfig("border-damage", 2.0));

         try {
            var4.setDamageBuffer(0.0);
         } catch (Throwable var6) {
         }

         if (var2) {
            var4.setSize(this.readDoubleConfig("border-final-size", 6.0), this.readIntConfig("border-shrink-seconds", 180));
            this.plugin.getLogger().info("Border shrinking in " + var1.world + " after 10 minutes.");
         } else {
            var4.setSize(Math.max(8.0, var1.radius * 2.0));
         }
      }
   }

   private void disableAdvancementAnnouncements() {
      for (World var2 : this.plugin.getServer().getWorlds()) {
         try {
            var2.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
         } catch (Throwable var4) {
         }
      }
   }

   @EventHandler
   public void onWorldLoad(WorldLoadEvent var1) {
      try {
         var1.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
      } catch (Throwable var3) {
      }

      if (var1.getWorld().getName().startsWith("cw_cavewars_")) {
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, this::enrichCurrentArena, 100L);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBreak(BlockBreakEvent var1) {
      Block var2 = var1.getBlock();
      if (this.isArenaWorld(var2.getWorld())) {
         String var3 = var2.getType().name();
         if (var3.endsWith("_LEAVES")) {
            double var4 = !"OAK_LEAVES".equals(var3) && !"DARK_OAK_LEAVES".equals(var3) ? 0.1 : 0.095;
            if (!(ThreadLocalRandom.current().nextDouble() >= var4)) {
               ItemStack var6 = new ItemStack(Material.APPLE, 1);
               HashMap<Integer, ItemStack> var7 = var1.getPlayer().getInventory().addItem(new ItemStack[]{var6});

               for (ItemStack var9 : var7.values()) {
                  var2.getWorld().dropItemNaturally(var2.getLocation(), var9);
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onQuit(PlayerQuitEvent var1) {
      this.rememberIfParticipant(var1.getPlayer());
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
   public void onKick(PlayerKickEvent var1) {
      this.rememberIfParticipant(var1.getPlayer());
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      String var2 = var1.getPlayer().getUniqueId().toString();
      if ("true".equals(this.pending.getProperty(var2))) {
         this.pending.remove(var2);
         this.savePending();
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.returnToLobby(var1.getPlayer()), 10L);
      }
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
   public void onPlayerCommand(PlayerCommandPreprocessEvent var1) {
      String var2 = var1.getMessage().trim().toLowerCase(Locale.ROOT);
      if ((var2.equals("/cw leave") || var2.startsWith("/cw leave ")) && this.isParticipant(var1.getPlayer())) {
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.returnToLobby(var1.getPlayer()), 5L);
      }

      if (var2.equals("/cw rebuild") || var2.startsWith("/cw rebuild ")) {
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, this::enrichCurrentArena, 100L);
      }
   }

   @EventHandler
   public void onServerCommand(ServerCommandEvent var1) {
      String var2 = var1.getCommand().trim().toLowerCase(Locale.ROOT);
      if (var2.equals("cw rebuild") || var2.startsWith("cw rebuild ")) {
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, this::enrichCurrentArena, 100L);
      }
   }

   private void rememberIfParticipant(Player var1) {
      if (this.isParticipant(var1)) {
         this.clearInventory(var1);
         this.pending.setProperty(var1.getUniqueId().toString(), "true");
         this.savePending();
      }
   }

   private void returnToLobby(Player var1) {
      this.clearInventory(var1);

      try {
         Properties var2 = this.loadProperties(new File("plugins/CaveWars/exit.properties"));
         World var3 = this.plugin.getServer().getWorld(var2.getProperty("spawn.world", "world"));
         if (var3 != null) {
            double var4 = Double.parseDouble(var2.getProperty("spawn.x", "0"));
            double var6 = Double.parseDouble(var2.getProperty("spawn.y", "80"));
            double var8 = Double.parseDouble(var2.getProperty("spawn.z", "0"));
            float var10 = Float.parseFloat(var2.getProperty("spawn.yaw", "0"));
            float var11 = Float.parseFloat(var2.getProperty("spawn.pitch", "0"));
            var1.teleport(new Location(var3, var4, var6, var8, var10, var11));
         }
      } catch (Throwable var13) {
         this.plugin.getLogger().warning("Lobby teleport failed: " + var13.getMessage());
      }

      try {
         ByteArrayOutputStream var14 = new ByteArrayOutputStream();
         DataOutputStream var15 = new DataOutputStream(var14);
         var15.writeUTF("Connect");
         var15.writeUTF("lobby");
         var1.sendPluginMessage(this.plugin, "BungeeCord", var14.toByteArray());
      } catch (Throwable var12) {
      }
   }

   private void clearInventory(Player var1) {
      PlayerInventory var2 = var1.getInventory();
      var2.clear();

      try {
         var2.setArmorContents(new ItemStack[4]);
      } catch (Throwable var5) {
      }

      try {
         var2.setItemInOffHand(null);
      } catch (Throwable var4) {
      }
   }

   private boolean isParticipant(Player var1) {
      try {
         Object var2 = this.getGameManager();
         if (var2 == null) {
            return false;
         }

         Method var3 = var2.getClass().getDeclaredMethod("isParticipant", new Class[]{UUID.class});
         var3.setAccessible(true);
         return Boolean.TRUE.equals(var3.invoke(var2, new Object[]{var1.getUniqueId()}));
      } catch (Throwable var4) {
         return false;
      }
   }

   private Object getGameManager() {
      try {
         Plugin var1 = this.plugin.getServer().getPluginManager().getPlugin("CaveWars");
         if (var1 == null) {
            return null;
         }

         Field var2 = var1.getClass().getDeclaredField("game");
         var2.setAccessible(true);
         return var2.get(var1);
      } catch (Throwable var3) {
         return null;
      }
   }

   private boolean invokeBoolean(Object var1, String var2) throws Exception {
      Method var3 = var1.getClass().getDeclaredMethod(var2, new Class[0]);
      var3.setAccessible(true);
      return Boolean.TRUE.equals(var3.invoke(var1, new Object[0]));
   }

   private long invokeLong(Object var1, String var2) throws Exception {
      Method var3 = var1.getClass().getDeclaredMethod(var2, new Class[0]);
      var3.setAccessible(true);
      Object var4 = var3.invoke(var1, new Object[0]);
      return ((Number)var4).longValue();
   }

   private void enrichCurrentArena() {
      ArenaInfo var1 = this.readArena();
      if (var1 != null) {
         World var2 = this.plugin.getServer().getWorld(var1.world);
         if (var2 != null) {
            int var3 = Math.max(var2.getMinHeight(), var1.y - var1.height);
            int var4 = Math.min(var2.getMaxHeight() - 1, var1.y + var1.height);
            ArrayList<Block> var5 = new ArrayList<>();
            int var6 = 0;
            int var7 = var1.radius * var1.radius;

            for (int var8 = var1.x - var1.radius; var8 <= var1.x + var1.radius; var8++) {
               for (int var9 = var1.z - var1.radius; var9 <= var1.z + var1.radius; var9++) {
                  int var10 = var8 - var1.x;
                  int var11 = var9 - var1.z;
                  if (var10 * var10 + var11 * var11 <= var7) {
                     for (int var12 = var3; var12 <= var4; var12++) {
                        Block var13 = var2.getBlockAt(var8, var12, var9);
                        Material var14 = var13.getType();
                        if (var14 == Material.RAW_IRON_BLOCK) {
                           var6++;
                        } else if (var14 == Material.IRON_ORE || var14 == Material.DEEPSLATE_IRON_ORE) {
                           var5.add(var13);
                        }
                     }
                  }
               }
            }

            int var15 = Math.max(0, 6 - var6);
            if (var15 != 0 && !var5.isEmpty()) {
               Collections.shuffle(var5);
               int var16 = Math.min(var15, var5.size());

               for (int var17 = 0; var17 < var16; var17++) {
                  ((Block)var5.get(var17)).setType(Material.RAW_IRON_BLOCK, false);
               }

               this.plugin.getLogger().info("Added " + var16 + " raw iron block(s) to " + var1.world + ".");
            }
         }
      }
   }

   private boolean isArenaWorld(World var1) {
      ArenaInfo var2 = this.readArena();
      return var2 != null && var2.world.equals(var1.getName());
   }

   private ArenaInfo readArena() {
      try {
         Properties var1 = this.loadProperties(new File("plugins/CaveWars/arena.properties"));
         String var2 = var1.getProperty("arena.world");
         return var2 != null && !var2.isBlank()
            ? new ArenaInfo(
               var2,
               Integer.parseInt(var1.getProperty("arena.x", "0")),
               Integer.parseInt(var1.getProperty("arena.y", "0")),
               Integer.parseInt(var1.getProperty("arena.z", "0")),
               Integer.parseInt(var1.getProperty("arena.radius", "20")),
               Integer.parseInt(var1.getProperty("arena.height", "18"))
            )
            : null;
      } catch (Throwable var3) {
         return null;
      }
   }

   private int readIntConfig(String var1, int var2) {
      try {
         return Integer.parseInt(this.loadProperties(new File("plugins/CaveWars/config.properties")).getProperty(var1, String.valueOf(var2)));
      } catch (Exception var4) {
         return var2;
      }
   }

   private double readDoubleConfig(String var1, double var2) {
      try {
         return Double.parseDouble(this.loadProperties(new File("plugins/CaveWars/config.properties")).getProperty(var1, String.valueOf(var2)));
      } catch (Exception var5) {
         return var2;
      }
   }

   private Properties loadProperties(File var1) throws IOException {
      Properties var2 = new Properties();
      InputStream var3 = Files.newInputStream(var1.toPath(), new OpenOption[0]);

      try {
         var2.load(var3);
      } catch (Throwable var7) {
         if (var3 != null) {
            try {
               var3.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (var3 != null) {
         var3.close();
      }

      return var2;
   }

   private void loadPending() {
      if (this.pendingFile.isFile()) {
         try {
            InputStream var1 = Files.newInputStream(this.pendingFile.toPath(), new OpenOption[0]);

            try {
               this.pending.load(var1);
            } catch (Throwable var5) {
               if (var1 != null) {
                  try {
                     var1.close();
                  } catch (Throwable var4) {
                     var5.addSuppressed(var4);
                  }
               }

               throw var5;
            }

            if (var1 != null) {
               var1.close();
            }
         } catch (Exception var6) {
         }
      }
   }

   private void savePending() {
      try {
         BufferedWriter var1 = Files.newBufferedWriter(this.pendingFile.toPath(), StandardCharsets.UTF_8, new OpenOption[0]);

         try {
            this.pending.store(var1, "Players to return to lobby");
         } catch (Throwable var5) {
            if (var1 != null) {
               try {
                  var1.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }

         if (var1 != null) {
            var1.close();
         }
      } catch (Exception var6) {
         this.plugin.getLogger().warning("Cannot save pending returns: " + var6.getMessage());
      }
   }
   private File dataFolder() {
      File dir = new File(this.plugin.getDataFolder().getParentFile(), "CaveWarsRulesPatch");
      if (!dir.exists()) dir.mkdirs();
      return dir;
   }

   private static final class ArenaInfo {
      final String world;
      final int x;
      final int y;
      final int z;
      final int radius;
      final int height;

      ArenaInfo(String world, int x, int y, int z, int radius, int height) {
         this.world = world; this.x = x; this.y = y; this.z = z; this.radius = radius; this.height = height;
      }
   }

}
