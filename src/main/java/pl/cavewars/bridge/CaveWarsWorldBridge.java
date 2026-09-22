package pl.cavewars.bridge;


import pl.cavewars.CaveWarsPlugin;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
public final class CaveWarsWorldBridge implements Listener {
   private final CaveWarsPlugin plugin;

   public CaveWarsWorldBridge(CaveWarsPlugin plugin) {
      this.plugin = plugin;
   }
   private static final String ARENA_ID = "cavewars";
   private static final String TEMPLATE = "cavewars";
   private final AtomicBoolean creating = new AtomicBoolean(false);
   private Object api;
   private Method getArenaWorld;
   private Method createArena;

   public void enable() {
      try {
         Class var1 = Class.forName("pl.cavewars.worlds.api.CaveWorldsApi");
         this.api = Bukkit.getServicesManager().load(var1);
         if (this.api == null) {
            throw new IllegalStateException("CaveWorldsApi service unavailable");
         }

         this.getArenaWorld = var1.getMethod("getArenaWorld", new Class[]{String.class});
         this.createArena = var1.getMethod("createArena", new Class[]{String.class, String.class});
         Bukkit.getPluginManager().registerEvents(this, this.plugin);
         Optional var2 = this.arenaWorld();
         if (var2.isPresent()) {
            this.syncCaveWars((World)var2.get());
            this.reloadCaveWars();
         }

         this.plugin.getLogger().info("CaveWarsWorldBridge enabled.");
      } catch (Throwable var3) {
         this.plugin.getLogger().severe("Cannot connect to CaveWarsWorlds API: " + var3.getMessage());
      }
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
   public void onPlayerCommand(PlayerCommandPreprocessEvent var1) {
      String var2 = var1.getMessage().trim().toLowerCase(Locale.ROOT);
      if (var2.equals("/cw join") || var2.equals("/cavewars join")) {
         try {
            Optional var3 = this.arenaWorld();
            if (var3.isPresent()) {
               this.syncCaveWars((World)var3.get());
               this.reloadCaveWars();
               return;
            }

            var1.setCancelled(true);
            Player var4 = var1.getPlayer();
            if (!this.creating.compareAndSet(false, true)) {
               var4.sendMessage("§8[§6CaveWars§8] §eArena jest właśnie przygotowywana. Spróbuj za chwilę.");
               return;
            }

            var4.sendMessage("§8[§6CaveWars§8] §ePrzygotowuję świeżą arenę...");
            CompletableFuture<World> var5 = (CompletableFuture<World>)this.createArena.invoke(this.api, new Object[]{"cavewars", "cavewars"});
            var5.whenComplete((var2x, var3x) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
               this.creating.set(false);
               if (var3x == null && var2x instanceof World var4x) {
                  try {
                     this.syncCaveWars(var4x);
                     this.reloadCaveWars();
                     var4.sendMessage("§8[§6CaveWars§8] §aArena gotowa. Dołączam...");
                     var4.performCommand("cw join");
                  } catch (Throwable var6x) {
                     this.plugin.getLogger().severe("Arena sync failed: " + var6x.getMessage());
                     var4.sendMessage("§8[§6CaveWars§8] §cNie udało się zsynchronizować areny.");
                  }
               } else {
                  String var5x = var3x == null ? "nieznany błąd" : String.valueOf(var3x.getMessage());
                  this.plugin.getLogger().severe("Arena creation failed: " + var5x);
                  var4.sendMessage("§8[§6CaveWars§8] §cNie udało się utworzyć areny.");
               }
            }));
         } catch (Throwable var6) {
            var1.setCancelled(true);
            this.plugin.getLogger().severe("Join bridge failed: " + var6.getMessage());
            var1.getPlayer().sendMessage("§8[§6CaveWars§8] §cBłąd integracji świata areny.");
         }
      }
   }

   private Optional<World> arenaWorld() throws Exception {
      return (Optional<World>)this.getArenaWorld.invoke(this.api, new Object[]{"cavewars"});
   }

   private void syncCaveWars(World var1) throws Exception {
      Path var2 = Path.of("plugins", new String[]{"CaveWars", "arena.properties"});
      Properties var3 = new Properties();
      InputStream var4 = Files.newInputStream(var2, new OpenOption[0]);

      try {
         var3.load(var4);
      } catch (Throwable var12) {
         if (var4 != null) {
            try {
               var4.close();
            } catch (Throwable var10) {
               var12.addSuppressed(var10);
            }
         }

         throw var12;
      }

      if (var4 != null) {
         var4.close();
      }

      String var13 = var1.getName();
      boolean var5 = !var13.equals(var3.getProperty("arena.world")) || !var13.equals(var3.getProperty("lobby.world"));
      if (var5) {
         var3.setProperty("arena.world", var13);
         var3.setProperty("lobby.world", var13);
         OutputStream var6 = Files.newOutputStream(var2, new OpenOption[0]);

         try {
            var3.store(var6, "CaveWars arena - synced by CaveWarsWorldBridge");
         } catch (Throwable var11) {
            if (var6 != null) {
               try {
                  var6.close();
               } catch (Throwable var9) {
                  var11.addSuppressed(var9);
               }
            }

            throw var11;
         }

         if (var6 != null) {
            var6.close();
         }

         this.plugin.getLogger().info("CaveWars arena synced to world: " + var13);
      }
   }

   private void reloadCaveWars() {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cw reload");
   }
}
