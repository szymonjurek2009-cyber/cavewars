package pl.cavewars.autopickup;


import pl.cavewars.CaveWarsPlugin;
import java.util.HashMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

public final class CaveWarsAutoPickupFix implements Listener {
   private final CaveWarsPlugin plugin;

   public CaveWarsAutoPickupFix(CaveWarsPlugin plugin) {
      this.plugin = plugin;
   }
   private static final ThreadLocal<BreakContext> ACTIVE_BREAK = new ThreadLocal();

   public void enable() {
      this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
   public void onBreakStart(BlockBreakEvent var1) {
      Block var2 = var1.getBlock();
      if (!isArenaWorld(var2.getWorld())) {
         ACTIVE_BREAK.remove();
      } else {
         ACTIVE_BREAK.set(new BreakContext(var1.getPlayer(), var2.getLocation()));
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onItemSpawn(ItemSpawnEvent var1) {
      BreakContext var2 = (BreakContext)ACTIVE_BREAK.get();
      if (var2 != null) {
         Item var3 = var1.getEntity();
         Location var4 = var3.getLocation();
         if (sameBreakLocation(var2.location, var4)) {
            ItemStack var5 = var3.getItemStack();
            if (var5 != null && var5.getAmount() > 0) {
               int var6 = var5.getAmount();
               ItemStack var7 = var5.clone();
               HashMap<Integer, ItemStack> var8 = var2.player.getInventory().addItem(new ItemStack[]{var7});
               int var9 = 0;
               ItemStack var10 = null;

               for (ItemStack var12 : var8.values()) {
                  if (var12 != null && var12.getAmount() > 0) {
                     var9 += var12.getAmount();
                     if (var10 == null) {
                        var10 = var12;
                     }
                  }
               }

               if (var9 < var6) {
                  if (var9 > 0 && var10 != null) {
                     var3.setItemStack(var10);
                  } else {
                     var1.setCancelled(true);
                  }

                  var2.player.playSound(var2.player.getLocation(), "minecraft:entity.item.pickup", 1.0F, 1.0F);
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onBreakEnd(BlockBreakEvent var1) {
      ACTIVE_BREAK.remove();
   }

   private static boolean isArenaWorld(World var0) {
      if (var0 == null) {
         return false;
      }

      String var1 = var0.getName();
      return var1 != null && (var1.startsWith("cw_") || var1.startsWith("Arena_"));
   }

   private static boolean sameBreakLocation(Location var0, Location var1) {
      if (var0 != null && var1 != null && var0.getWorld() != null && var1.getWorld() != null) {
         if (var0.getWorld() != var1.getWorld() && !var0.getWorld().getName().equals(var1.getWorld().getName())) {
            return false;
         }

         double var2 = var0.getX() - var1.getX();
         double var4 = var0.getY() - var1.getY();
         double var6 = var0.getZ() - var1.getZ();
         return var2 * var2 + var4 * var4 + var6 * var6 <= 4.0;
      } else {
         return false;
      }
   }
   private static final class BreakContext {
      final Player player;
      final Location location;

      BreakContext(Player player, Location location) {
         this.player = player;
         this.location = location;
      }
   }

}
