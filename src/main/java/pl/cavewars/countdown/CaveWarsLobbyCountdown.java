package pl.cavewars.countdown;


import pl.cavewars.CaveWarsPlugin;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
public final class CaveWarsLobbyCountdown {
   private final CaveWarsPlugin plugin;

   public CaveWarsLobbyCountdown(CaveWarsPlugin plugin) {
      this.plugin = plugin;
   }
   private static final Set<Integer> MARKS = Set.of(new Integer[]{30, 15, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 60});
   private Object game;
   private Method stateMethod;
   private Method remainingMethod;
   private Plugin caveWars;
   private String previousState = "";
   private long countdownStartedAt = -1L;
   private int totalSeconds = 60;
   private int lastRemaining = Integer.MAX_VALUE;

   public void enable() {
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
         this.remainingMethod = this.game.getClass().getDeclaredMethod("startSecondsRemaining", new Class[0]);
         this.remainingMethod.setAccessible(true);
         Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
         this.plugin.getLogger().info("CaveWars lobby countdown enabled: 2 players=30s, 3/4 players=15s.");
      } catch (Exception var2) {
         this.plugin.getLogger().severe("Could not hook CaveWars: " + var2);
      }
   }

   private void tick() {
      try {
         String state = String.valueOf(this.stateMethod.invoke(this.game, new Object[0]));
         if ("STARTING".equals(state)) {
            int remaining = ((Number)this.remainingMethod.invoke(this.game, new Object[0])).intValue();
            if (remaining > 0 && remaining != this.lastRemaining && MARKS.contains(remaining)) {
               this.announce(remaining);
            }
            this.lastRemaining = remaining;
         } else {
            this.lastRemaining = Integer.MAX_VALUE;
         }
         this.previousState = state;
      } catch (Exception ex) {
         this.plugin.getLogger().warning("Countdown tick failed: " + ex.getClass().getSimpleName());
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
      this.plugin.getLogger().info("Lobby countdown: " + var1 + "s");
   }

   private static int broadcastWithSound(String var0) {
      int var1 = Bukkit.broadcastMessage(var0);
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute as @a at @s run playsound minecraft:block.note_block.pling master @s ~ ~ ~ 1 1.2");
      return var1;
   }
}
