package pl.cavewars.worlds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class CaveWorldCommand implements CommandExecutor, TabCompleter {
   private final CaveWarsWorldsPlugin plugin;
   private final ArenaWorldManager manager;

   public CaveWorldCommand(CaveWarsWorldsPlugin var1, ArenaWorldManager var2) {
      this.plugin = var1;
      this.manager = var2;
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (!var1.hasPermission("caveworlds.admin")) {
         msg(var1, "&cNie masz permisji caveworlds.admin.");
         return true;
      }

      if (var4.length != 0 && !var4[0].equalsIgnoreCase("help")) {
         try {
            switch (var4[0].toLowerCase()) {
               case "create":
                  this.createArena(var1, var4);
                  break;
               case "end":
               case "delete":
                  this.deleteArena(var1, var4);
                  break;
               case "tp":
                  this.teleportToArena(var1, var4);
                  break;
               case "list":
                  this.listArenas(var1);
                  break;
               case "templates":
                  this.listTemplates(var1);
                  break;
               case "template":
                  this.handleTemplate(var1, var4);
                  break;
               default:
                  this.sendHelp(var1);
            }
         } catch (IllegalArgumentException var7) {
            msg(var1, "&c" + var7.getMessage());
         } catch (Exception var8) {
            msg(var1, "&cBłąd: " + var8.getMessage());
            this.plugin.getLogger().warning("Błąd komendy /cww: " + var8);
         }

         return true;
      } else {
         this.sendHelp(var1);
         return true;
      }
   }

   private void createArena(CommandSender var1, String[] var2) {
      if (var2.length < 3) {
         msg(var1, "&eUżycie: /cww create <idRozgrywki> <szablon>");
      } else {
         String var3 = ArenaWorldManager.normalizeId(var2[1]);
         String var4 = ArenaWorldManager.normalizeId(var2[2]);
         msg(var1, "&eTworzenie areny &f" + var3 + " &ez szablonu &f" + var4 + "&e...");
         this.manager.createArena(var3, var4).whenComplete((var2x, var3x) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (var3x != null) {
               msg(var1, "&cNie udało się utworzyć areny: " + rootMessage(var3x));
            } else {
               msg(var1, "&aArena gotowa. Świat: &f" + var2x.getName());
               msg(var1, "&7Poczekalnia = spawn zapisany w szablonie.");
            }
         }));
      }
   }

   private void deleteArena(CommandSender var1, String[] var2) {
      if (var2.length < 2) {
         msg(var1, "&eUżycie: /cww end <idRozgrywki>");
      } else {
         String var3 = ArenaWorldManager.normalizeId(var2[1]);
         msg(var1, "&eKończenie areny &f" + var3 + "&e...");
         this.manager.deleteArena(var3).whenComplete((var2x, var3x) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (var3x != null) {
               msg(var1, "&cNie udało się usunąć areny: " + rootMessage(var3x));
            } else {
               msg(var1, "&aArena została odładowana i jej folder został usunięty.");
            }
         }));
      }
   }

   private void teleportToArena(CommandSender var1, String[] var2) {
      if (var1 instanceof Player var3) {
         if (var2.length < 2) {
            msg(var1, "&eUżycie: /cww tp <idRozgrywki>");
         } else {
            this.manager.getLobby(var2[1]).ifPresentOrElse(var1x -> {
               var3.teleport(var1x);
               msg(var3, "&aPrzeniesiono do poczekalni areny.");
            }, () -> msg(var3, "&cNie znaleziono aktywnej areny."));
         }
      } else {
         msg(var1, "&cTej komendy może użyć gracz.");
      }
   }

   private void listArenas(CommandSender var1) {
      if (this.manager.getActiveArenaIds().isEmpty()) {
         msg(var1, "&7Brak aktywnych aren.");
      } else {
         msg(var1, "&eAktywne areny: &f" + String.join(", ", this.manager.getActiveArenaIds()));
      }
   }

   private void listTemplates(CommandSender var1) {
      List var2 = this.manager.getTemplateNames();
      if (var2.isEmpty()) {
         msg(var1, "&7Brak zapisanych szablonów.");
      } else {
         msg(var1, "&eSzablony: &f" + String.join(", ", var2));
      }
   }

   private void handleTemplate(CommandSender var1, String[] var2) {
      if (var2.length < 3) {
         msg(var1, "&e/cww template create <nazwa> &7- pusty świat do zbudowania mapy");
         msg(var1, "&e/cww template save <nazwa> &7- zapisuje obecny świat jako szablon");
      } else {
         String var3 = var2[1].toLowerCase();
         String var4 = ArenaWorldManager.normalizeId(var2[2]);
         switch (var3) {
            case "create":
               if (!(var1 instanceof Player var9)) {
                  msg(var1, "&cTę komendę uruchom jako gracz.");
                  return;
               }

               World var8 = this.manager.createTemplateEditorWorld(var4);
               var9.teleport(var8.getSpawnLocation());
               msg(var9, "&aUtworzono pusty świat edycji: &f" + var8.getName());
               msg(var9, "&7Zbuduj/wklej mapę oraz poczekalnię, ustaw spawn w poczekalni, potem wpisz:");
               msg(var9, "&f/cww template save " + var4);
               break;
            case "save":
               if (!(var1 instanceof Player var7)) {
                  msg(var1, "&cTę komendę uruchom jako gracz stojący na świecie z mapą.");
                  return;
               }

               msg(var7, "&eZapisywanie szablonu &f" + var4 + "&e...");
               this.manager.saveTemplateFromWorld(var4, var7.getWorld()).whenComplete((var3x, var4x) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
                  if (var4x != null) {
                     msg(var7, "&cNie udało się zapisać szablonu: " + rootMessage(var4x));
                  } else {
                     msg(var7, "&aSzablon zapisany: &f" + var4);
                  }
               }));
               break;
            default:
               msg(var1, "&cDostępne: /cww template create <nazwa> lub /cww template save <nazwa>");
         }
      }
   }

   private void sendHelp(CommandSender var1) {
      msg(var1, "&6CaveWarsWorlds");
      msg(var1, "&e/cww template create <nazwa> &7- tworzy pusty świat do mapy");
      msg(var1, "&e/cww template save <nazwa> &7- zapisuje mapę + poczekalnię jako szablon");
      msg(var1, "&e/cww create <id> <szablon> &7- tworzy świeżą kopię na mecz");
      msg(var1, "&e/cww tp <id> &7- teleport do poczekalni");
      msg(var1, "&e/cww end <id> &7- kończy mecz i usuwa cały świat");
      msg(var1, "&e/cww list &7- aktywne areny");
      msg(var1, "&e/cww templates &7- zapisane szablony");
   }

   private static void msg(CommandSender var0, String var1) {
      var0.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&6CWW&8] &r" + var1));
   }

   private static String rootMessage(Throwable var0) {
      Throwable var1 = var0;

      while (var1.getCause() != null) {
         var1 = var1.getCause();
      }

      return var1.getMessage() == null ? var1.getClass().getSimpleName() : var1.getMessage();
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var4.length == 1) {
         return filter(var4[0], Arrays.asList(new String[]{"help", "template", "templates", "create", "tp", "end", "list"}));
      } else if (var4.length == 2 && var4[0].equalsIgnoreCase("template")) {
         return filter(var4[1], Arrays.asList(new String[]{"create", "save"}));
      } else if (var4.length != 2 || !var4[0].equalsIgnoreCase("tp") && !var4[0].equalsIgnoreCase("end")) {
         return var4.length == 3 && var4[0].equalsIgnoreCase("create") ? filter(var4[2], this.manager.getTemplateNames()) : Collections.emptyList();
      } else {
         return filter(var4[1], new ArrayList(this.manager.getActiveArenaIds()));
      }
   }

   private static List<String> filter(String var0, List<String> var1) {
      String var2 = var0.toLowerCase();
      return var1.stream().filter(var1x -> var1x.toLowerCase().startsWith(var2)).sorted().toList();
   }
}
