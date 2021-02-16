package com.rmjtromp.deathnote;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import me.clip.placeholderapi.PlaceholderAPI;
import net.milkbowl.vault.economy.Economy;

public class DeathNote extends JavaPlugin {
	
	private Material MATERIAL = Material.PAPER;
	private String DISPLAY_NAME = "&6&lDeath Note &7(Right Click)";
	private List<String> LORES = new ArrayList<>(Arrays.asList("&e%player_name%&7''s death note", "&7right click this note to utilize", "", "&7Value: &e$%note_value%"));
	private String version = "";
	private boolean version_match = false;
	
    private Economy econ = null;

	@Override
	public void onEnable() {
		// initialize config
		saveDefaultConfig();
		if(getConfig().isSet("note.material") && getConfig().isString("note.material")) {
			Material m = Material.getMaterial(getConfig().getString("note.material", MATERIAL.toString()));
			if(m != null) MATERIAL = m;
		} else getConfig().set("note.material", MATERIAL.toString());
		
		if(getConfig().isSet("note.display.name") && getConfig().isString("note.display.name")) {
			String name = getConfig().getString("note.display.name", DISPLAY_NAME);
			if(!ChatColor.translateAlternateColorCodes('&', name).isEmpty()) DISPLAY_NAME = name;
		} else getConfig().set("note.display.name", DISPLAY_NAME);
		
		if(getConfig().isSet("note.display.lores") && getConfig().isList("note.display.lores")) {
			List<String> lores = getConfig().getStringList("note.display.lores");
			LORES = lores;
		} else getConfig().set("note.display.lores", LORES);
		
		// get version
		version = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
		version_match = version.equals("v1_16_R3");

		// setup economy
        if (getServer().getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        econ = rsp != null ? rsp.getProvider() : null;
		
        // register listeners
		getServer().getPluginManager().registerEvents(new Listener() {
			
			@EventHandler
			public void onPlayerDeath(PlayerDeathEvent e) {
				if(econ != null && !e.getEntity().getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY)) {
					double balance = econ.getBalance(e.getEntity());
					if(balance > 0.0D && econ.withdrawPlayer(e.getEntity(), balance).transactionSuccess()) {
						ItemStack item = new ItemStack(MATERIAL);
						ItemMeta meta = item.getItemMeta();
						String roundedValue = Double.toString(Math.round(balance * 100.0D) / 100.0D);
						meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', PlaceholderAPI.setPlaceholders(e.getEntity(), DISPLAY_NAME.replaceAll("%note_value%", roundedValue))));
						List<String> newLore = new ArrayList<>();
						LORES.forEach(lore -> newLore.add(ChatColor.translateAlternateColorCodes('&', lore.isEmpty() ? "&7" : PlaceholderAPI.setPlaceholders(e.getEntity(), lore.replaceAll("%note_value%", roundedValue)))));
						meta.setLore(newLore);
						item.setItemMeta(meta);
						
						if(version_match) {
							try {
								net.minecraft.server.v1_16_R3.ItemStack craftItem = org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack.asNMSCopy(item);
								net.minecraft.server.v1_16_R3.NBTTagCompound compound = (craftItem.hasTag()) ? craftItem.getTag() : new net.minecraft.server.v1_16_R3.NBTTagCompound();
								compound.setDouble("note.value", balance);
								craftItem.setTag(compound);
								org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack.asBukkitCopy(craftItem);
								item.setItemMeta(org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack.getItemMeta(craftItem));
							} catch(Exception ex1) {
								ex1.printStackTrace();
							}
						} else {
							try {
								Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit."+version+".inventory.CraftItemStack");
								Method asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
								Object craftItem = asNMSCopyMethod.invoke(craftItemStackClass, item);
								
								Method hasTagMethod = craftItem.getClass().getMethod("hasTag");
								Method getTagMethod = craftItem.getClass().getMethod("getTag");
								Class<?> NBTTagCompoundClass = Class.forName("net.minecraft.server."+version+".NBTTagCompound");
								Constructor<?> NBTTagCompoundConstructor = NBTTagCompoundClass.getConstructor();
								Object compound = (boolean) hasTagMethod.invoke(craftItem) ? getTagMethod.invoke(craftItem) : NBTTagCompoundConstructor.newInstance();
								
								Method setDoubleMethod = compound.getClass().getMethod("setDouble", String.class, double.class);
								setDoubleMethod.invoke(compound, "note.value", balance);
								
								Method setTagMethod = craftItem.getClass().getMethod("setTag", compound.getClass());
								setTagMethod.invoke(craftItem, compound);
								
								Method asBukkitCopyMethod = craftItemStackClass.getMethod("asBukkitCopy", craftItem.getClass());
								asBukkitCopyMethod.invoke(craftItemStackClass, craftItem);
								
								Method getItemMetaMethod = craftItemStackClass.getMethod("getItemMeta", craftItem.getClass());
								Object itemMeta = getItemMetaMethod.invoke(craftItemStackClass, craftItem);
								item.setItemMeta((ItemMeta) itemMeta);
							} catch(Exception ex) {
								ex.printStackTrace();
							}
						}
						
						e.getDrops().add(item);
					}
				}
			}
			
			@EventHandler
			public void onPlayerInteract(PlayerInteractEvent e) {
				if(e.getAction().toString().contains("CLICK") && e.getItem() != null) {
					if(version_match) {
						try {
							net.minecraft.server.v1_16_R3.ItemStack craftItem = org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack.asNMSCopy(e.getItem());
							net.minecraft.server.v1_16_R3.NBTTagCompound compound = (craftItem.hasTag()) ? craftItem.getTag() : new net.minecraft.server.v1_16_R3.NBTTagCompound();
							if(compound.hasKey("note.value")) {
								double value = compound.getDouble("note.value");
								if(econ.depositPlayer(e.getPlayer(), value).transactionSuccess()) {
									if(e.getItem().getAmount() > 1) e.getItem().setAmount(e.getItem().getAmount() - 1);
									else e.getPlayer().getInventory().remove(e.getItem());
									e.getPlayer().updateInventory();
									e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
									e.getPlayer().sendMessage(ChatColor.GREEN+"$"+Double.toString(Math.round(value * 100.0D) / 100.0D)+" was deposited to your account.");
								} else e.getPlayer().sendMessage(ChatColor.RED + "There was an error depositing money to your account.");
							}
						} catch(Exception ex1) {
							ex1.printStackTrace();
						}
					} else {
						try {
							Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit."+version+".inventory.CraftItemStack");
							Method asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
							Object craftItem = asNMSCopyMethod.invoke(craftItemStackClass, e.getItem());
							
							Method hasTagMethod = craftItem.getClass().getMethod("hasTag");
							Method getTagMethod = craftItem.getClass().getMethod("getTag");
							Class<?> NBTTagCompoundClass = Class.forName("net.minecraft.server."+version+".NBTTagCompound");
							Constructor<?> NBTTagCompoundConstructor = NBTTagCompoundClass.getConstructor();
							Object compound = (boolean) hasTagMethod.invoke(craftItem) ? getTagMethod.invoke(craftItem) : NBTTagCompoundConstructor.newInstance();
							
							Method hasKeyMethod = compound.getClass().getMethod("hasKey", String.class);
							if((boolean) hasKeyMethod.invoke(compound, "note.value")) {
								Method getDoubleMethod = compound.getClass().getMethod("getDouble", String.class);
								double value = (double) getDoubleMethod.invoke(compound, "note.value");
								if(econ.depositPlayer(e.getPlayer(), value).transactionSuccess()) {
									if(e.getItem().getAmount() > 1) e.getItem().setAmount(e.getItem().getAmount() - 1);
									else e.getPlayer().getInventory().remove(e.getItem());
									e.getPlayer().updateInventory();
									e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
									e.getPlayer().sendMessage(ChatColor.GREEN+"$"+Double.toString(Math.round(value * 100.0D) / 100.0D)+" was deposited to your account.");
								} else e.getPlayer().sendMessage(ChatColor.RED + "There was an error depositing money to your account.");
							}
						} catch(Exception ex) {
							ex.printStackTrace();
						}
					}
				}
			}
			
		}, this);
	}
	
}
