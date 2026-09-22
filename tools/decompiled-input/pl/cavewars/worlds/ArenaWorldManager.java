package pl.cavewars.worlds;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;

public final class ArenaWorldManager {
   private static final String TEMP_MARKER = ".cavewars-temp";
   private static final Set<String> SKIPPED_ROOT_DIRS = Set.of("playerdata", "stats", "advancements");
   private static final Set<String> SKIPPED_FILES = Set.of("session.lock", "uid.dat", ".cavewars-temp");
   private final CaveWarsWorldsPlugin plugin;
   private final Map<String, ArenaInstance> arenas = new ConcurrentHashMap();
   private final Path templatesDirectory;

   public ArenaWorldManager(CaveWarsWorldsPlugin var1) {
      this.plugin = var1;
      this.templatesDirectory = var1.getDataFolder().toPath().resolve("templates");
   }

   public void initialize() throws IOException {
      Files.createDirectories(this.templatesDirectory, new FileAttribute[0]);
      this.cleanupStaleTemporaryWorlds();
   }

   public CompletableFuture<World> createArena(String var1, String var2) {
      String var3 = normalizeId(var1);
      String var4 = normalizeId(var2);
      Path var5 = this.templatesDirectory.resolve(var4).normalize();
      if (!var5.startsWith(this.templatesDirectory)
         || !Files.isDirectory(var5, new LinkOption[0])
         || !Files.exists(var5.resolve("level.dat"), new LinkOption[0])) {
         return failedFuture(new IllegalArgumentException("Nie znaleziono szablonu: " + var4));
      }

      if (this.arenas.containsKey(var3)) {
         return failedFuture(new IllegalStateException("Arena o ID '" + var3 + "' już istnieje."));
      }

      String var6 = buildWorldName(var3);
      Path var7 = Bukkit.getWorldContainer().toPath().resolve(var6).normalize();
      ArenaInstance var8 = new ArenaInstance(var3, var4, var6, var7);
      ArenaInstance var9 = (ArenaInstance)this.arenas.putIfAbsent(var3, var8);
      if (var9 != null) {
         return failedFuture(new IllegalStateException("Arena o ID '" + var3 + "' już istnieje."));
      }

      CompletableFuture var10 = new CompletableFuture();
      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
         try {
            if (Files.exists(var7, new LinkOption[0])) {
               deleteDirectory(var7);
            }

            copyWorldDirectory(var5, var7);
            Files.writeString(var7.resolve(".cavewars-temp"), "temporary CaveWars world\n", new OpenOption[0]);
         } catch (Exception var7x) {
            this.arenas.remove(var3);
            this.safeDelete(var7);
            var10.completeExceptionally(var7x);
            return;
         }

         Bukkit.getScheduler().runTask(this.plugin, () -> {
            try {
               WorldCreator var5xx = new WorldCreator(var6);
               var5xx.environment(Environment.NORMAL);
               var5xx.generateStructures(false);
               var5xx.generator(new VoidChunkGenerator());
               World var6x = var5xx.createWorld();
               if (var6x == null) {
                  throw new IllegalStateException("Paper nie utworzył świata " + var6);
               }

               this.configureArenaWorld(var6x);
               var6x.getChunkAt(var6x.getSpawnLocation()).load(true);
               var10.complete(var6x);
            } catch (Exception var7xx) {
               this.arenas.remove(var3);
               Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.safeDelete(var7));
               var10.completeExceptionally(var7xx);
            }
         });
      });
      return var10;
   }

   public CompletableFuture<Void> deleteArena(String var1) {
      String var2 = normalizeId(var1);
      ArenaInstance var3 = (ArenaInstance)this.arenas.get(var2);
      if (var3 == null) {
         return failedFuture(new IllegalArgumentException("Nie ma aktywnej areny: " + var2));
      }

      CompletableFuture var4 = new CompletableFuture();
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         World var4x = Bukkit.getWorld(var3.worldName());
         if (var4x != null) {
            Location var5 = this.findFallbackLocation(var4x);

            for (Player var7 : new ArrayList(var4x.getPlayers())) {
               teleportAndClear(var7, var5);
            }

            boolean var8 = Bukkit.unloadWorld(var4x, false);
            if (!var8) {
               var4.completeExceptionally(new IllegalStateException("Nie udało się odładować świata " + var3.worldName()));
               return;
            }
         }

         this.arenas.remove(var2);
         Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
               deleteDirectoryWithRetries(var3.worldFolder(), 5);
               var4.complete(null);
            } catch (Exception var3xx) {
               var4.completeExceptionally(var3xx);
            }
         });
      });
      return var4;
   }

   public CompletableFuture<Path> saveTemplateFromWorld(String var1, World var2) {
      String var3 = normalizeId(var1);
      Path var4 = this.templatesDirectory.resolve(var3).normalize();
      if (!var4.startsWith(this.templatesDirectory)) {
         return failedFuture(new IllegalArgumentException("Nieprawidłowa nazwa szablonu."));
      }

      var2.save();
      CompletableFuture var5 = new CompletableFuture();
      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
         try {
            if (Files.exists(var4, new LinkOption[0])) {
               deleteDirectory(var4);
            }

            copyWorldDirectory(var2.getWorldFolder().toPath(), var4);
            var5.complete(var4);
         } catch (Exception var4x) {
            var5.completeExceptionally(var4x);
         }
      });
      return var5;
   }

   public World createTemplateEditorWorld(String var1) {
      String var2 = normalizeId(var1);
      String var3 = "cwt_" + var2;
      World var4 = Bukkit.getWorld(var3);
      if (var4 != null) {
         return var4;
      }

      WorldCreator var5 = new WorldCreator(var3);
      var5.environment(Environment.NORMAL);
      var5.generateStructures(false);
      var5.generator(new VoidChunkGenerator());
      World var6 = var5.createWorld();
      if (var6 == null) {
         throw new IllegalStateException("Nie udało się utworzyć świata edycji szablonu.");
      }

      var6.setAutoSave(true);
      var6.setGameRule(GameRule.DO_MOB_SPAWNING, false);
      var6.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
      var6.setStorm(false);
      var6.setThundering(false);
      int var7 = this.plugin.getConfig().getInt("template-editor.platform-y", 100);
      int var8 = Math.max(0, this.plugin.getConfig().getInt("template-editor.platform-radius", 2));
      if (this.plugin.getConfig().getBoolean("template-editor.platform", true)) {
         for (int var9 = -var8; var9 <= var8; var9++) {
            for (int var10 = -var8; var10 <= var8; var10++) {
               var6.getBlockAt(var9, var7, var10).setType(Material.STONE, false);
            }
         }
      }

      var6.setSpawnLocation(0, var7 + 1, 0);
      return var6;
   }

   public Optional<World> getArenaWorld(String var1) {
      String var2;
      try {
         var2 = normalizeId(var1);
      } catch (IllegalArgumentException var4) {
         return Optional.empty();
      }

      ArenaInstance var3 = (ArenaInstance)this.arenas.get(var2);
      return var3 == null ? Optional.empty() : Optional.ofNullable(Bukkit.getWorld(var3.worldName()));
   }

   public Optional<Location> getLobby(String var1) {
      return this.getArenaWorld(var1).map(World::getSpawnLocation);
   }

   public Collection<String> getActiveArenaIds() {
      return List.copyOf(this.arenas.keySet());
   }

   public List<String> getTemplateNames() {
      if (!Files.isDirectory(this.templatesDirectory, new LinkOption[0])) {
         return List.of();
      }

      try {
         Stream var1 = Files.list(this.templatesDirectory);

         List var2;
         try {
            var2 = var1.filter(var0 -> Files.isDirectory(var0, new LinkOption[0]))
               .filter(var0 -> Files.exists(var0.resolve("level.dat"), new LinkOption[0]))
               .map(var0 -> var0.getFileName().toString())
               .sorted()
               .toList();
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

         return var2;
      } catch (IOException var6) {
         this.plugin.getLogger().warning("Nie udało się odczytać listy szablonów: " + var6.getMessage());
         return List.of();
      }
   }

   public void shutdownAndDeleteAll() {
      for (ArenaInstance var2 : new ArrayList(this.arenas.values())) {
         World var3 = Bukkit.getWorld(var2.worldName());
         if (var3 != null) {
            Location var4 = this.findFallbackLocation(var3);

            for (Player var6 : new ArrayList(var3.getPlayers())) {
               teleportAndClear(var6, var4);
            }

            Bukkit.unloadWorld(var3, false);
         }

         this.safeDelete(var2.worldFolder());
      }

      this.arenas.clear();
   }

   private void configureArenaWorld(World var1) {
      var1.setAutoSave(this.plugin.getConfig().getBoolean("world.autosave", false));
      if (this.plugin.getConfig().getBoolean("world.disable-mob-spawning", true)) {
         var1.setGameRule(GameRule.DO_MOB_SPAWNING, false);
      }

      if (this.plugin.getConfig().getBoolean("world.freeze-time", true)) {
         var1.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
      }

      if (this.plugin.getConfig().getBoolean("world.clear-weather", true)) {
         var1.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
         var1.setStorm(false);
         var1.setThundering(false);
         var1.setWeatherDuration(0);
      }
   }

   private Location findFallbackLocation(World var1) {
      String var2 = this.plugin.getConfig().getString("fallback-world", "world");
      World var3 = var2 == null ? null : Bukkit.getWorld(var2);
      if (var3 == null || var3.equals(var1)) {
         var3 = (World)Bukkit.getWorlds()
            .stream()
            .filter(var1x -> !var1x.equals(var1))
            .filter(var0 -> !var0.getName().startsWith("cw_"))
            .findFirst()
            .orElse(null);
      }

      if (var3 == null) {
         throw new IllegalStateException("Brak świata, do którego można przenieść graczy przed usunięciem areny.");
      } else {
         return var3.getSpawnLocation();
      }
   }

   private void cleanupStaleTemporaryWorlds() {
      Path var1 = Bukkit.getWorldContainer().toPath();
      Bukkit.getScheduler()
         .runTaskAsynchronously(
            this.plugin,
            () -> {
               try {
                  Stream var2 = Files.list(var1);

                  try {
                     var2.filter(var0 -> Files.isDirectory(var0, new LinkOption[0]))
                        .filter(var0 -> Files.exists(var0.resolve(".cavewars-temp"), new LinkOption[0]))
                        .forEach(var1xx -> {
                           if (Bukkit.getWorld(var1xx.getFileName().toString()) != null) {
                              this.plugin.getLogger().warning("Pominięto czyszczenie załadowanego świata: " + var1xx.getFileName());
                           } else {
                              this.plugin.getLogger().info("Usuwanie pozostałości po starej arenie: " + var1xx.getFileName());
                              this.safeDelete(var1xx);
                           }
                        });
                  } catch (Throwable var6) {
                     if (var2 != null) {
                        try {
                           var2.close();
                        } catch (Throwable var5) {
                           var6.addSuppressed(var5);
                        }
                     }

                     throw var6;
                  }

                  if (var2 != null) {
                     var2.close();
                  }
               } catch (IOException var7) {
                  this.plugin.getLogger().warning("Nie udało się sprawdzić starych światów: " + var7.getMessage());
               }
            }
         );
   }

   private static String buildWorldName(String var0) {
      String var1 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
      return "cw_" + var0.toLowerCase(Locale.ROOT) + "_" + var1;
   }

   public static String normalizeId(String var0) {
      if (var0 == null) {
         throw new IllegalArgumentException("ID nie może być puste.");
      } else {
         String var1 = var0.trim();
         if (!var1.matches("[A-Za-z0-9_-]{1,24}")) {
            throw new IllegalArgumentException("Użyj tylko liter, cyfr, _ i - (maks. 24 znaki).");
         } else {
            return var1.toLowerCase(Locale.ROOT);
         }
      }
   }

   private static void copyWorldDirectory(Path var0, Path var1) throws IOException {
      Files.createDirectories(var1, new FileAttribute[0]);
      Stream var2 = Files.walk(var0, new FileVisitOption[0]);

      try {
         for (Path var4 : var2.toList()) {
            Path var5 = var0.relativize(var4);
            if (var5.getNameCount() != 0) {
               String var6 = var5.getName(0).toString();
               String var7 = var4.getFileName().toString();
               if (!SKIPPED_ROOT_DIRS.contains(var6) && !SKIPPED_FILES.contains(var7)) {
                  Path var8 = var1.resolve(var5);
                  if (Files.isDirectory(var4, new LinkOption[0])) {
                     Files.createDirectories(var8, new FileAttribute[0]);
                  } else {
                     Files.createDirectories(var8.getParent(), new FileAttribute[0]);
                     Files.copy(var4, var8, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES});
                  }
               }
            }
         }
      } catch (Throwable var10) {
         if (var2 != null) {
            try {
               var2.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }
         }

         throw var10;
      }

      if (var2 != null) {
         var2.close();
      }
   }

   private static void deleteDirectoryWithRetries(Path var0, int var1) throws IOException, InterruptedException {
      IOException var2 = null;

      for (int var3 = 0; var3 < var1; var3++) {
         try {
            deleteDirectory(var0);
            return;
         } catch (IOException var5) {
            var2 = var5;
            Thread.sleep(250L * (var3 + 1));
         }
      }

      if (var2 != null) {
         throw var2;
      }
   }

   private static void deleteDirectory(Path var0) throws IOException {
      if (Files.exists(var0, new LinkOption[0])) {
         Stream var1 = Files.walk(var0, new FileVisitOption[0]);

         try {
            for (Path var4 : var1.sorted(Comparator.reverseOrder()).toList()) {
               Files.deleteIfExists(var4);
            }
         } catch (Throwable var6) {
            if (var1 != null) {
               try {
                  var1.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }

         if (var1 != null) {
            var1.close();
         }
      }
   }

   private void safeDelete(Path var1) {
      try {
         deleteDirectory(var1);
      } catch (IOException var3) {
         this.plugin.getLogger().warning("Nie udało się usunąć " + var1 + ": " + var3.getMessage());
      }
   }

   private static <T> CompletableFuture<T> failedFuture(Throwable var0) {
      CompletableFuture var1 = new CompletableFuture();
      var1.completeExceptionally(var0);
      return var1;
   }

   private static boolean teleportAndClear(Player var0, Location var1) {
      boolean var2 = var0.teleport(var1);
      var0.getInventory().clear();
      return var2;
   }
}
