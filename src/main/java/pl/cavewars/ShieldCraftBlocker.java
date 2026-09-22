package pl.cavewars;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;

public final class ShieldCraftBlocker implements Listener {
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPrepareCraft(PrepareItemCraftEvent event) {
      if (event.getRecipe() != null && event.getRecipe().getResult().getType() == Material.SHIELD) {
         event.getInventory().setResult(null);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onCraft(CraftItemEvent event) {
      if (event.getRecipe() != null && event.getRecipe().getResult().getType() == Material.SHIELD) {
         event.setCancelled(true);
      }
   }
}
