package io.github.mrredcon.randomcontinenttp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.MemorySection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class RandomContinentTp extends JavaPlugin
{
    private HashMap<String, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable()
    {
        saveDefaultConfig();
        Essentials.setup();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (command.getName().equalsIgnoreCase("rctp") && args.length == 1)
        {
            List<String> list = new ArrayList<String>();

            Set<String> continents = getConfig().getConfigurationSection("boundaries").getKeys(false);
            list.addAll(continents);

            if (sender.hasPermission("rctp.reload"))
                list.add("reload");

            return list;
        }

        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (args[0].equals("reload"))
        {
            if (sender.hasPermission("rctp.reload"))
            {
                this.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "The config file was successfully reloaded");
            }
            else
            {
                sender.sendMessage(ChatColor.DARK_RED + "You do not have permission to reload the configuration file.");
            }

            return true;
        }

        // checks if a player is sending the command
        if (sender instanceof Player)
        {
            Player me = (Player) sender;
            World world = me.getWorld();

            // Checks if the player is on cooldown
            Long cooldownFinish = cooldowns.get(me.getName());
            Long currentTime = System.currentTimeMillis();

            if (cooldownFinish != null && cooldownFinish > currentTime)
            {
                Duration timeLeft = Duration.ofMillis(cooldownFinish - currentTime);

                String formatted = String.format("%d hours, %d minutes, %d seconds", 
                    timeLeft.toHoursPart(),
                    timeLeft.toMinutesPart(), 
                    timeLeft.toSecondsPart());
                
                me.sendMessage(ChatColor.DARK_RED + "RCTP is on cooldown for another " + ChatColor.RED + formatted);

                return true;
            }

            Map<String, Object> allBounds = getConfig().getConfigurationSection("boundaries").getValues(false);

            // Use the continent code as a key and get map boundaries
            Object continentBounds = allBounds.get(args[0]);
            if (continentBounds instanceof MemorySection)
            {
                // Cast from object to a configuration section and then turn it into a map
                teleport(((MemorySection)continentBounds).getValues(false), me, world);
                return true;
            }
            else
            {
                me.sendMessage(ChatColor.RED + "Please enter the name of a location. (" + String.join(", ", allBounds.keySet()) + ")");
                return true;
            }
        }

        return false;
    }

    private void teleport(Map<?, ?> bounds, Player player, World world)
    {
        Integer minX = (Integer) bounds.get("min-x");
        Integer minZ = (Integer) bounds.get("min-z");

        Integer maxX = (Integer) bounds.get("max-x");
        Integer maxZ = (Integer) bounds.get("max-z");

        double tpx = ThreadLocalRandom.current().nextDouble(minX, maxX + 1);
        double tpy = 255;
        double tpz = ThreadLocalRandom.current().nextDouble(minZ, maxZ + 1);

        Location dest = new Location(world, tpx, tpy, tpz);

        // Figure out where the ground is
        while (dest.getBlock().getType() == Material.AIR)
        {
            tpy--;
            dest = new Location(world, tpx, tpy, tpz);

            // Make sure we're not gonna kill our player
            if (dest.getBlock().getType() == Material.WATER || dest.getBlock().getType() == Material.LAVA)
            {
                // If we are, then start over
                tpx = ThreadLocalRandom.current().nextDouble(minX, maxX + 1);
                tpy = 255;
                tpz = ThreadLocalRandom.current().nextDouble(minZ, maxZ + 1);
                dest = new Location(world, tpx, tpy, tpz);
            }
        }

        // Put our player 5m above ground (so they don't suffocate)
        dest = new Location(world, tpx, tpy + 5, tpz);

        int cooldown = getConfig().getInt("cooldown");
        cooldowns.put(player.getName(), System.currentTimeMillis() + (cooldown * 1000));

        // Try to use Essentials to TP, if it doesn't work, just use Bukkit
        if (!Essentials.handleTeleport(player, dest, getConfig().getDouble("cost")))
        {
            player.teleport(dest);
        }
    }
}
