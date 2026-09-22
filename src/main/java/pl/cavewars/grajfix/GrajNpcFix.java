package pl.cavewars.grajfix;


import pl.cavewars.CaveWarsPlugin;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
public final class GrajNpcFix implements Listener {
   private final CaveWarsPlugin plugin;

   public GrajNpcFix(CaveWarsPlugin plugin) {
      this.plugin = plugin;
   }
   public void enable() {
      this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
      this.plugin.getLogger().info("CaveWars NPC click handler enabled: Graj + Adold -> /arena.");
   }

   @EventHandler
   public void onRight(NPCRightClickEvent var1) {
      this.handle(var1.getNPC(), var1.getClicker());
   }

   @EventHandler
   public void onLeft(NPCLeftClickEvent var1) {
      this.handle(var1.getNPC(), var1.getClicker());
   }

   private void handle(NPC var1, Player var2) {
      if (var1 != null && var2 != null) {
         if ("Graj".equalsIgnoreCase(var1.getName()) || "Adold".equalsIgnoreCase(var1.getName())) {
            Bukkit.getScheduler().runTask(this.plugin, () -> var2.performCommand("arena"));
         }
      }
   }
}
