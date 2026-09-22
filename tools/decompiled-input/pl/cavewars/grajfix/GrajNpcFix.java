package pl.cavewars.grajfix;

import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class GrajNpcFix extends JavaPlugin implements Listener {
   public void onEnable() {
      this.getServer().getPluginManager().registerEvents(this, this);
      this.getLogger().info("GrajNpcFix 2.0 enabled: Citizens click events -> /arena.");
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
         if ("Graj".equalsIgnoreCase(var1.getName())) {
            Bukkit.getScheduler().runTask(this, () -> var2.performCommand("arena"));
         }
      }
   }
}
