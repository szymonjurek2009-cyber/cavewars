package pl.cavewars;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Properties;

final class CaveConfig {
   private final Path path;
   private final Properties props = new Properties();

   CaveConfig(Path var1) {
      this.path = var1.resolve("config.properties");
   }

   void load() throws IOException {
      Files.createDirectories(this.path.getParent(), new FileAttribute[0]);
      this.props.clear();
      if (Files.exists(this.path, new LinkOption[0])) {
         InputStream var1 = Files.newInputStream(this.path, new OpenOption[0]);

         try {
            this.props.load(var1);
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
      }

      this.putDefault("min-players", "2");
      this.putDefault("max-players", "12");
      this.putDefault("auto-start-seconds", "10");
      this.putDefault("game-duration-seconds", "600");
      this.putDefault("pvp-after-seconds", "120");
      this.putDefault("arena-radius", "20");
      this.putDefault("arena-height", "18");
      this.putDefault("starter-bread", "4");
      this.putDefault("starter-torches", "8");
      this.putDefault("reset-after-game", "true");
      this.save();
   }

   private void putDefault(String var1, String var2) {
      if (!this.props.containsKey(var1)) {
         this.props.setProperty(var1, var2);
      }
   }

   void save() throws IOException {
      OutputStream var1 = Files.newOutputStream(this.path, new OpenOption[0]);

      try {
         this.props.store(var1, "CaveWars 1.0.4 - Paper 1.21.10 / Java 21");
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
   }

   int getInt(String var1, int var2, int var3, int var4) {
      try {
         int var5 = Integer.parseInt(this.props.getProperty(var1, Integer.toString(var2)).trim());
         return Math.max(var3, Math.min(var4, var5));
      } catch (NumberFormatException var6) {
         return var2;
      }
   }

   boolean getBoolean(String var1, boolean var2) {
      String var3 = this.props.getProperty(var1);
      return var3 == null ? var2 : Boolean.parseBoolean(var3.trim());
   }

   int minPlayers() {
      return this.getInt("min-players", 2, 2, 100);
   }

   int maxPlayers() {
      return this.getInt("max-players", 12, 2, 100);
   }

   int autoStartSeconds() {
      return this.getInt("auto-start-seconds", 10, 1, 120);
   }

   int gameDurationSeconds() {
      return this.getInt("game-duration-seconds", 600, 60, 3600);
   }

   int pvpAfterSeconds() {
      return this.getInt("pvp-after-seconds", 120, 0, 1800);
   }

   int arenaRadius() {
      return this.getInt("arena-radius", 20, 12, 40);
   }

   int arenaHeight() {
      return this.getInt("arena-height", 18, 8, 40);
   }

   int starterBread() {
      return this.getInt("starter-bread", 4, 0, 64);
   }

   int starterTorches() {
      return this.getInt("starter-torches", 8, 0, 64);
   }

   boolean resetAfterGame() {
      return this.getBoolean("reset-after-game", true);
   }
}
