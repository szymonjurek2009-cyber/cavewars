package pl.cavewars.countdown;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class CaveWarsLobbyCountdown extends JavaPlugin {
   private static final Set<Integer> MARKS = Set.of(new Integer[]{30, 15, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 60});
   private Object game;
   private Method stateMethod;
   private Plugin caveWars;
   private String previousState = "";
   private long countdownStartedAt = -1L;
   private int totalSeconds = 60;
   private int lastRemaining = Integer.MAX_VALUE;

   public void onEnable() {
      try {
         this.caveWars = Bukkit.getPluginManager().getPlugin("CaveWars");
         if (this.caveWars == null) {
            throw new IllegalStateException("CaveWars not found");
         }

         Field var1 = this.caveWars.getClass().getDeclaredField("game");
         var1.setAccessible(true);
         this.game = var1.get(this.caveWars);
         this.stateMethod = this.game.getClass().getDeclaredMethod("state", new Class[0]);
         this.stateMethod.setAccessible(true);
         Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
         this.getLogger().info("CaveWars lobby countdown enabled: 30,15,10..1.");
      } catch (Exception var2) {
         this.getLogger().severe("Could not hook CaveWars: " + var2);
      }
   }

   private void tick() {
      try {
         String var1 = String.valueOf(this.stateMethod.invoke(this.game, new Object[0]));
         if ("STARTING".equals(var1) && !"STARTING".equals(this.previousState)) {
            this.totalSeconds = this.readAutoStartSeconds();
            this.countdownStartedAt = System.currentTimeMillis();
            this.lastRemaining = this.totalSeconds + 1;
         }

         if ("STARTING".equals(var1) && this.countdownStartedAt > 0L) {
            long var2 = Math.max(0L, System.currentTimeMillis() - this.countdownStartedAt);
            int var4 = Math.max(0, this.totalSeconds - (int)(var2 / 1000L));
            if (var4 != this.lastRemaining && MARKS.contains(var4)) {
               this.announce(var4);
            }

            this.lastRemaining = var4;
         } else if (!"STARTING".equals(var1)) {
            this.countdownStartedAt = -1L;
            this.lastRemaining = Integer.MAX_VALUE;
         }

         this.previousState = var1;
      } catch (Exception var5) {
         this.getLogger().warning("Countdown tick failed: " + var5.getClass().getSimpleName());
      }
   }

   private int readAutoStartSeconds() {
      try {
         Method var1 = this.caveWars.getClass().getMethod("getDataFolder", new Class[0]);
         File var2 = (File)var1.invoke(this.caveWars, new Object[0]);
         Properties var3 = new Properties();
         FileInputStream var4 = new FileInputStream(new File(var2, "config.properties"));

         try {
            var3.load(var4);
         } catch (Throwable var8) {
            try {
               var4.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }

            throw var8;
         }

         var4.close();
         return Math.max(1, Integer.parseInt(var3.getProperty("auto-start-seconds", "60").trim()));
      } catch (Exception var9) {
         return 60;
      }
   }

   private void announce(int var1) {
      String var2 = "§8[§6CaveWars§8] §aStart za §f" + var1 + " s§a!";
      broadcastWithSound(var2);
      this.getLogger().info("Lobby countdown: " + var1 + "s");
   }

   private static int broadcastWithSound(String var0) {
      int var1 = Bukkit.broadcastMessage(var0);
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute as @a at @s run playsound minecraft:block.note_block.pling master @s ~ ~ ~ 1 1.2");
      return var1;
   }
}
