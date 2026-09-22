const fs=require('fs'),path=require('path');
const base='/workspace/.cache/cavewars-merge/decomp';
const out='/workspace/.cache/cavewars-merge/src';
fs.rmSync(out,{recursive:true,force:true});

function transform(rel, className, opts={}){
 let s=fs.readFileSync(base+'/'+rel,'utf8');
 s=s.replace(/import org\.bukkit\.plugin\.java\.JavaPlugin;\s*/g,'');
 const pkg=(s.match(/^package ([^;]+);/m)||[])[1];
 if(pkg!=='pl.cavewars'&&!s.includes('import pl.cavewars.CaveWarsPlugin;')){
   s=s.replace(/(package [^;]+;\s*)/,'$1\nimport pl.cavewars.CaveWarsPlugin;\n');
 }
 s=s.replace(new RegExp('public final class '+className+'\\s+extends JavaPlugin\\s+implements'),'public final class '+className+' implements');
 s=s.replace(new RegExp('public final class '+className+'\\s+extends JavaPlugin\\s*\\{'),'public final class '+className+' {');
 s=s.replace(new RegExp('public class '+className+'\\s+extends JavaPlugin\\s+implements'),'public class '+className+' implements');
 // remove old no-arg ctor if present
 s=s.replace(new RegExp('\\n\\s*public '+className+'\\(\\) \\{[\\s\\S]*?\\n\\s*\\}\\n','m'), m => {
   return /Holder\.INSTANCE = this/.test(m) ? '\n' : m;
 });
 const marker=new RegExp('(public (?:final )?class '+className+'[^\\{]*\\{)');
 const extra=opts.holder?'\n      Holder.INSTANCE = this;':'';
 s=s.replace(marker,'$1\n   private final CaveWarsPlugin plugin;\n\n   public '+className+'(CaveWarsPlugin plugin) {\n      this.plugin = plugin;'+extra+'\n   }');
 s=s.replace(/public void onEnable\(\)/g,'public void enable()');
 s=s.replace(/public void onDisable\(\)/g,'public void disable()');
 s=s.replace(/this\.getServer\(\)/g,'this.plugin.getServer()');
 s=s.replace(/this\.getLogger\(\)/g,'this.plugin.getLogger()');
 s=s.replace(/this\.getCommand\(/g,'this.plugin.getCommand(');
 s=s.replace(/this\.getConfig\(\)/g,'this.plugin.getConfig()');
 s=s.replace(/registerEvents\(this, this\)/g,'registerEvents(this, this.plugin)');
 s=s.replace(/registerOutgoingPluginChannel\(this, /g,'registerOutgoingPluginChannel(this.plugin, ');
 s=s.replace(/runTaskTimer\(this, /g,'runTaskTimer(this.plugin, ');
 s=s.replace(/runTaskLater\(this, /g,'runTaskLater(this.plugin, ');
 s=s.replace(/runTaskAsynchronously\(this, /g,'runTaskAsynchronously(this.plugin, ');
 s=s.replace(/runTask\(this, /g,'runTask(this.plugin, ');
 s=s.replace(/Bukkit\.getScheduler\(\)\.runTaskTimer\(this, /g,'Bukkit.getScheduler().runTaskTimer(this.plugin, ');
 s=s.replace(/Bukkit\.getScheduler\(\)\.runTaskLater\(this, /g,'Bukkit.getScheduler().runTaskLater(this.plugin, ');
 s=s.replace(/Bukkit\.getScheduler\(\)\.runTask\(this, /g,'Bukkit.getScheduler().runTask(this.plugin, ');
 s=s.replace(/sendPluginMessage\(this, /g,'sendPluginMessage(this.plugin, ');
 if(opts.dataFolder){
   s=s.replace(/this\.getDataFolder\(\)/g,'this.dataFolder()');
   const helper='\n   private File dataFolder() {\n      File dir = new File(this.plugin.getDataFolder().getParentFile(), "'+opts.dataFolder+'");\n      if (!dir.exists()) dir.mkdirs();\n      return dir;\n   }\n';
   const idx=s.lastIndexOf('\n}');
   s=s.slice(0,idx)+helper+s.slice(idx);
 }
 if(opts.modify) s=opts.modify(s);
 const dest=out+'/'+rel;
 fs.mkdirSync(path.dirname(dest),{recursive:true});
 fs.writeFileSync(dest,s);
 console.log('WROTE',rel,s.length);
}
transform('pl/cavewars/arenachat/CaveWarsArenaChat.java','CaveWarsArenaChat');
transform('pl/cavewars/autopickup/CaveWarsAutoPickupFix.java','CaveWarsAutoPickupFix');
transform('pl/cavewars/endteleport/CaveWarsEndTeleport.java','CaveWarsEndTeleport');
transform('pl/cavewars/countdown/CaveWarsLobbyCountdown.java','CaveWarsLobbyCountdown');
transform('pl/cavewars/lossstats/CaveWarsLossStats.java','CaveWarsLossStats',{dataFolder:'CaveWarsLossStats',holder:true});
transform('pl/cavewars/rules/CaveWarsRulesPatch.java','CaveWarsRulesPatch',{dataFolder:'CaveWarsRulesPatch'});
transform('pl/cavewars/bridge/CaveWarsWorldBridge.java','CaveWarsWorldBridge');
transform('pl/cavewars/grajfix/GrajNpcFix.java','GrajNpcFix',{modify:s=>s
 .replace('if ("Graj".equalsIgnoreCase(var1.getName())) {','if ("Graj".equalsIgnoreCase(var1.getName()) || "Adold".equalsIgnoreCase(var1.getName())) {')
 .replace('GrajNpcFix 2.0 enabled: Citizens click events -> /arena.','CaveWars NPC click handler enabled: Graj + Adold -> /arena.')
});
