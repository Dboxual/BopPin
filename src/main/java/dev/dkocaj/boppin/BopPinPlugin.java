package dev.dkocaj.boppin;

import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BopPinPlugin extends JavaPlugin {
    static final String TAG = "[BopPin]";
    static final String BUILD_MARKER = "BOPPIN NEW BUILD LOADED";

    private PinStore store;

    @Override
    public void onEnable() {
        Logger log = getLogger();
        banner(log, "================ " + BUILD_MARKER + " ================");
        banner(log, "BopPin version: " + getPluginMeta().getVersion());
        banner(log, "Paper/server version: " + Bukkit.getVersion()
                + "  (Minecraft " + Bukkit.getMinecraftVersion() + ")");
        banner(log, "Java runtime: " + System.getProperty("java.version")
                + "  (" + System.getProperty("java.vm.name") + ")");

        saveDefaultConfig();
        List<String> bedrockPrefixes = getConfig().getStringList("bedrock-prefixes");
        banner(log, "Bedrock prefixes: " + bedrockPrefixes);

        Path dataDir = getDataFolder().toPath();
        banner(log, "Plugin data folder: " + dataDir.toAbsolutePath());
        try {
            Files.createDirectories(dataDir);
        } catch (Exception e) {
            log.severe(TAG + " cannot create data folder: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Path dbFile = dataDir.resolve("pins.sqlite");
        try {
            this.store = new PinStore(dbFile, log);
        } catch (SQLException e) {
            log.severe(TAG + " cannot open SQLite at " + dbFile + ": " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        banner(log, "PIN database ready at " + dbFile.toAbsolutePath());

        PreJoinListener preJoin = new PreJoinListener(store, log, this, bedrockPrefixes);
        DialogClickListener clicks = new DialogClickListener(preJoin, log);
        getServer().getPluginManager().registerEvents(preJoin, this);
        getServer().getPluginManager().registerEvents(clicks, this);

        int cfgHandlers = AsyncPlayerConnectionConfigureEvent.getHandlerList()
                .getRegisteredListeners().length;
        int clickHandlers = PlayerCustomClickEvent.getHandlerList()
                .getRegisteredListeners().length;
        banner(log, "Listeners registered. AsyncPlayerConnectionConfigureEvent handlers="
                + cfgHandlers + ", PlayerCustomClickEvent handlers=" + clickHandlers);

        banner(log, "================ BOPPIN READY ================");
    }

    @Override
    public void onDisable() {
        Logger log = getLogger();
        if (store != null) {
            store.close();
            log.info(TAG + " PIN database closed");
        }
        log.info(TAG + " shutdown complete");
    }

    private static void banner(Logger log, String line) {
        log.info(TAG + " " + line);
    }
}
