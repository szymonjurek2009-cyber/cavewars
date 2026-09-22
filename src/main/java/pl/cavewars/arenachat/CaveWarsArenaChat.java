package pl.cavewars.arenachat;


import pl.cavewars.CaveWarsPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import net.kyori.adventure.audience.Audience;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
public final class CaveWarsArenaChat implements Listener {
   private final CaveWarsPlugin plugin;

   public CaveWarsArenaChat(CaveWarsPlugin plugin) {
      this.plugin = plugin;
   }
   public void enable() {
      this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
      PluginCommand var1 = this.plugin.getCommand("arenachatdebugsend");
      if (var1 != null) {
         var1.setExecutor(this::onTestCommand);
      }
   }

   private boolean onTestCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var4.length < 2) {
         return false;
      }

      Player var5 = this.plugin.getServer().getPlayerExact(var4[0]);
      if (var5 == null) {
         return false;
      }

      String var6 = String.join(" ", (CharSequence[])Arrays.copyOfRange(var4, 1, var4.length));
      var5.chat(var6);
      return true;
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onChat(AsyncChatEvent var1) {
      Player var2 = var1.getPlayer();
      String var3 = channel(var2.getWorld());
      Iterator var4 = var1.viewers().iterator();

      while (var4.hasNext()) {
         Audience var5 = (Audience)var4.next();
         if (var5 instanceof Player var6 && !var3.equals(channel(var6.getWorld()))) {
            var4.remove();
         }
      }

      if (var2.hasPermission("cavewars.arenachat.debug")) {
         StringBuilder var9 = new StringBuilder();

         for (Audience var7 : var1.viewers()) {
            if (var7 instanceof Player var8) {
               if (var9.length() > 0) {
                  var9.append(',');
               }

               var9.append(var8.getName()).append('@').append(var8.getWorld().getName());
            }
         }

         System.out
            .println("[CaveWarsArenaChat TEST] sender=" + var2.getName() + " world=" + var2.getWorld().getName() + " channel=" + var3 + " viewers=" + var9);
      }
   }

   private static String channel(World var0) {
      String var1 = var0.getName();
      String var2 = var1.toLowerCase(Locale.ROOT);
      return !var2.startsWith("cw_") && !var2.startsWith("arena_") ? "lobby" : "arena:" + var1;
   }
}
