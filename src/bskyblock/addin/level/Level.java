package bskyblock.addin.level;

import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import bskyblock.addin.level.commands.Commands;
import bskyblock.addin.level.config.LocaleManager;
import bskyblock.addin.level.config.PluginConfig;
import bskyblock.addin.level.database.object.Levels;
import us.tastybento.bskyblock.BSkyBlock;
import us.tastybento.bskyblock.config.BSBLocale;
import us.tastybento.bskyblock.database.BSBDatabase;
import us.tastybento.bskyblock.database.managers.AbstractDatabaseHandler;

/**
 * Addin to BSkyBlock that enables island level scoring and top ten functionality
 * @author tastybento
 *
 */
public class Level extends JavaPlugin {


    // The BSkyBlock plugin instance.
    private BSkyBlock bSkyBlock;

    // Level calc checker
    BukkitTask checker = null;

    // Locale manager for this plugin
    private LocaleManager localeManager;

    // Database handler for level data
    private AbstractDatabaseHandler<Levels> handler;

    // The BSkyBlock database object
    private BSBDatabase database;

    // A cache of island levels. Island levels are not kept in memory unless required.
    // The cache is saved when the server shuts down and the plugin is disabled.
    // TODO: Save regularly to avoid crash issues.
    private HashMap<UUID, Long> levelsCache;

    // The Top Ten object
    private TopTen topTen;

    @SuppressWarnings("unchecked")
    @Override
    public void onEnable() {
        // Load the plugin's config
        new PluginConfig(this);
        // Get the BSkyBlock plugin. This will be available because this plugin depends on it in plugin.yml.
        bSkyBlock = BSkyBlock.getPlugin();
        // Check if it is enabled - it might be loaded, but not enabled.
        if (!bSkyBlock.isEnabled()) {
            this.setEnabled(false);
            return;
        }
        // Get the BSkyBlock database
        database = BSBDatabase.getDatabase();
        // Set up the database handler to store and retrieve Island classes
        // Note that these are saved by the BSkyBlock database
        handler = (AbstractDatabaseHandler<Levels>) database.getHandler(bSkyBlock, Levels.class);
        // Initialize the cache
        levelsCache = new HashMap<>();
        // Start the top ten and register it for clicks
        topTen = new TopTen(this);
        getServer().getPluginManager().registerEvents(topTen, this);
        // Local locales
        localeManager = new LocaleManager(this);
        // Register commands
        new Commands(this);
        // Done
    }

    @Override
    public void onDisable(){
        // Save the cache
        if (levelsCache != null) {
            save(false);
        }
    }
    
    /**
     * Save the levels to the database
     * @param async - if true, saving will be done async
     */
    public void save(boolean async){
        Runnable save = () -> {
            try {
                for (Entry<UUID, Long> en : levelsCache.entrySet()) {
                    Levels lv = new Levels();
                    lv.setLevel(en.getValue());
                    lv.setUniqueId(en.getKey().toString());
                    handler.saveObject(lv);
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException
                    | InstantiationException | NoSuchMethodException | IntrospectionException | SQLException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        };
        if(async){
            getServer().getScheduler().runTaskAsynchronously(this, save);
        } else {
            save.run();
        }
    }

    /**
     * Get level from cache for a player
     * @param targetPlayer
     * @return Level of player
     */
    public long getIslandLevel(UUID targetPlayer) {
        //getLogger().info("DEBUG: getting island level for " + bSkyBlock.getPlayers().getName(targetPlayer));
        if (levelsCache.containsKey(targetPlayer)) {
            return levelsCache.get(targetPlayer);
        }
        // Get from database
        Levels level;
        try {
            level = handler.loadObject(targetPlayer.toString());
            if (level == null) {
                // We do not know this player, set to zero
                return 0;
            }
            levelsCache.put(targetPlayer, level.getLevel());
            return level.getLevel();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | SecurityException | ClassNotFoundException | IntrospectionException | SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Save the player's level 
     * @param targetPlayer
     * @param level
     */
    public void setIslandLevel(UUID targetPlayer, long level) {
        //getLogger().info("DEBUG: set island level to " + level + " for " + bSkyBlock.getPlayers().getName(targetPlayer));
        // Add to cache
        levelsCache.put(targetPlayer, level);
        topTen.addEntry(targetPlayer, level);
    }

    /**
     * Get the locale for this player
     * @param sender
     * @return Locale object for sender
     */
    public BSBLocale getLocale(CommandSender sender) {
        return localeManager.getLocale(sender);
    }

    /**
     * Get the locale for this UUID
     * @param uuid
     * @return Locale object for UUID
     */
    public BSBLocale getLocale(UUID uuid) {
        return localeManager.getLocale(uuid);
    }

    public AbstractDatabaseHandler<Levels> getHandler() {
        return handler;
    }

    public TopTen getTopTen() {
        return topTen;
    }

}