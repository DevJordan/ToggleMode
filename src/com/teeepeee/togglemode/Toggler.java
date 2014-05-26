/**
 * 
 */
package com.teeepeee.togglemode;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * @author Jordan Sicherman
 * 
 *         A simple tool to toggle the online state of your Minecraft server.
 *         Feel free to use in your plugins but please give credit!
 * 
 */
public class Toggler extends JavaPlugin {

	private enum ToggleMode {
		ON, OFF, DYNAMIC;

		/**
		 * Get the mode for a boolean state.
		 * 
		 * @param state
		 *            The boolean state.
		 * @return true -> ON, false -> OFF
		 */
		public static ToggleMode getModeFor(boolean state) {
			return state ? ON : OFF;
		}

		/**
		 * Get the online-mode value of a given ToggleMode.
		 * 
		 * @param mode
		 *            The ToggleMode.
		 * @return ON -> true, OFF -> false, default -> true/false (server mode)
		 */
		public static boolean getBooleanValue(ToggleMode mode) {
			switch (mode) {
			case ON:
				return true;
			case OFF:
				return false;
			default:
				return getServerMode();
			}
		}
	}

	private boolean isEnabled; // Whether or not the plugin is enabled.
	private ToggleMode toggleMode; // The current toggle mode.

	/*
	 * Ensure obfuscation compatibility by grabbing the package name.
	 */
	private static final String packageName = Bukkit.getServer().getClass().getPackage().getName();
	private static final String version = packageName.substring(packageName.lastIndexOf(".") + 1);

	/**
	 * The online mode state of the server.
	 * 
	 * @return False if the server is in online-mode: off, true otherwise.
	 */
	public static boolean getServerMode() {
		try {
			Class<?> serverclass = Class.forName("org.bukkit.craftbukkit." + version + ".CraftServer");
			Object server = serverclass.cast(Bukkit.getServer());
			Class<?> consoleclass = Class.forName("net.minecraft.server." + version + ".MinecraftServer");
			Method consolemethod = serverclass.getMethod("getServer");
			Object console = consolemethod.invoke(server);

			Method getmode = consoleclass.getMethod("getOnlineMode");
			return (Boolean) getmode.invoke(console);
		} catch (Exception exc) {
			exc.printStackTrace();
		}
		return true;
	}

	/**
	 * Toggle the online-mode of a server to ON, OFF or dynamic.
	 * 
	 * @param mode
	 *            The mode to toggle to.
	 */
	public void toggleMode(ToggleMode mode) {
		// Update our mode and save it in our config.
		toggleMode = mode;
		getConfig().set("toggle-mode", toggleMode.toString());
		saveConfig();

		if (mode == ToggleMode.DYNAMIC) {
			/*
			 * Connect to the Mojang status site.
			 */
			boolean online = false;
			try {
				URL url = new URL("http://status.mojang.com");

				BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
				String str;
				while ((str = reader.readLine()) != null) {
					// We're good to go. Online!
					if ("OK".equals(str)) {
						online = true;
						break;
					}
				}
				reader.close();
			} catch (Exception exc) {
				// Guess we're offline.
			}
			setMode(online); // Update us.
		} else {
			setMode(ToggleMode.getBooleanValue(mode)); // Update us.
		}
	}

	/**
	 * Set the online mode of the server to a given boolean mode.
	 * 
	 * @param mode
	 *            true -> online, false -> offline
	 * @return True if the server mode reflects the given mode, false if an
	 *         error occured.
	 */
	public boolean setMode(boolean mode) {
		if (getServerMode() == mode) { return true; } // Don't need to change
														// anything!
		try {
			Class<?> serverclass = Class.forName("org.bukkit.craftbukkit." + version + ".CraftServer");
			Object server = serverclass.cast(Bukkit.getServer());
			Class<?> consoleclass = Class.forName("net.minecraft.server." + version + ".MinecraftServer");
			Method consolemethod = serverclass.getMethod("getServer");
			Object console = consolemethod.invoke(server);

			Method setmode = consoleclass.getMethod("setOnlineMode", boolean.class);
			setmode.invoke(console, mode);

			/*
			 * Update the server cache to make sure we maintain through reloads and reload immediately.
			 */
			updateServerCache(console, mode);
			Bukkit.reload();

			// Log stuff.
			Bukkit.getConsoleSender().sendMessage(
					ChatColor.YELLOW + "Server mode successfully updated to: " + (mode ? "online" : "offline"));
		} catch (Exception exc) {
			exc.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Update the server.yml file with the proper online-mode value.
	 * 
	 * @param server
	 *            The MinecraftServer object.
	 * @param mode
	 *            The mode to assign.
	 */
	public void updateServerCache(Object server, boolean mode) {
		try {
			Class<?> propertymanagerclass = Class.forName("net.minecraft.server." + version + ".PropertyManager");
			Object propertymanager = propertymanagerclass.cast(server.getClass().getMethod("getPropertyManager").invoke(server));

			// 'a' is the only method subject to change used in this class.
			Method setmethod = propertymanagerclass.getMethod("a", String.class, Object.class);
			Method savemethod = propertymanagerclass.getMethod("savePropertiesFile");

			// Set the mode and save the file.
			setmethod.invoke(propertymanager, "online-mode", mode);
			savemethod.invoke(propertymanager);
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}

	@Override
	public void onEnable() {
		// Save our config and update our data.
		saveDefaultConfig();
		
		isEnabled = getConfig().getBoolean("enabled");
		if (!isEnabled) { return; } // Don't need to run.
		toggleMode = ToggleMode.valueOf(getConfig().getString("toggle-mode"));
		// Log fun stuff.
		Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "Server mode set to: " + toggleMode.toString());

		/*
		 * Run a scheduler on a delay to make sure dynamic servers update with time if they have to.
		 */
		getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
			@Override
			public void run() {
				// Only toggle if we have to.
				if (toggleMode == ToggleMode.DYNAMIC)
					toggleMode(ToggleMode.DYNAMIC);
			}
		}, 0L, getConfig().getLong("delay"));
	}

	@Override
	public void onDisable() {
		if (!isEnabled) { return; }
		getServer().getScheduler().cancelTasks(this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		/*
		 * Modetoggle command.
		 */
		if (cmd.getName().equalsIgnoreCase("modetoggle")) {
			// Permission check.
			if (!sender.hasPermission("togglemode.toggle")) {
				sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
				return true;
			}

			// A mode we're switching to.
			ToggleMode toggle = null;

			// No arguments...
			if (args.length == 0) {
				// Use the opposite online-mode we're already using.
				toggle = ToggleMode.getModeFor(!ToggleMode.getBooleanValue(toggleMode));
			}
			// An argument!
			else {
				String mode = args[0].toLowerCase();
				if (mode.equals("true") || mode.equals("yes") || mode.equals("on")) {
					toggle = ToggleMode.getModeFor(true); // Make us online.
				} else if (mode.equals("false") || mode.equals("no") || mode.equals("off")) {
					toggle = ToggleMode.getModeFor(false); // Make us offline.
				} else if (mode.equals("dynamic")) {
					toggle = ToggleMode.DYNAMIC; // Make us dynamic!
				}
			}

			// We didn't receive a valid yes/no/dynamic answer :(
			if (toggle == null) {
				sender.sendMessage(ChatColor.RED + "Please only enter a toggle-state (true/on/yes/false/off/no/dynamic).");
				return true;
			}

			// Update our mode.
			toggleMode(toggle);
		}
		/*
		 * Mode command.
		 */
		else if (cmd.getName().equalsIgnoreCase("mode")) {
			// Permission check.
			if (!sender.hasPermission("togglemode.check")) {
				sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
				return true;
			}
			// Send data.
			sender.sendMessage(ChatColor.YELLOW + "Online-mode: " + getServerMode());
		}
		return true;
	}
}
