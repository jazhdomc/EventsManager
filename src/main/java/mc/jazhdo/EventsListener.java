package mc.jazhdo;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.md_5.bungee.api.chat.TextComponent;

public class EventsListener implements Listener {
    private final FileConfiguration config;
    private final EventsLoop loop;
    private final List<String> playerlist = new ArrayList<>();

    public EventsListener(EventsManager plugin, EventsLoop loop) {
        this.config = plugin.getConfig();
        this.loop = loop;
    }

    public Location getSpawn() {
        return new Location(
            Bukkit.getWorld(config.getString("worldname")),
            config.getDouble("spawn.x"),
            config.getDouble("spawn.y"),
            config.getDouble("spawn.z"),
            (float) config.getDouble("spawn.yaw"),
            (float) config.getDouble("spawn.pitch")
        );
    }

    @EventHandler
    public void playerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        sendMessage(player, config.getString("welcome-msg"));
        player.teleport(getSpawn());
    }

    @EventHandler
    public void playerQuit(PlayerQuitEvent event) {
        String playername = event.getPlayer().getName();
        if (playerlist.remove(playername)) {
            for (Player p : Bukkit.getOnlinePlayers())
                p.sendMessage(config.getString("leave-msg").replace("%p", playername));
            if (loop.gameActive()) loop.midQuit(playername);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        GameSession session = checkPlayer(event.getPlayer());
        if (session != null) session.onPlayerMove(event);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        GameSession session = checkPlayer(event.getEntity());
        if (session != null) session.onPlayerDeath(event);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        GameSession session = checkPlayer(event.getPlayer());
        if (session != null) session.onBlockBreak(event);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        GameSession session = checkPlayer(event.getPlayer());
        if (session != null) session.onBlockPlace(event);
    }

    private GameSession checkPlayer(Player player) {
        // Only run if a game is open for the highest probability of needing this
        if (!loop.gameActive()) return null;

        // Go through each session in the current round and check if the player is in one of them
        GameSession target = null;
        for (GameSession session : loop.getCurrentRound()) 
            if (session.containsPlayer(player.getName())) {
                target = session;
                break;
            }

        // Make sure a session is found and still playing
        if (target == null || target.ended()) return null;

        // Return the found GameSession
        return target;
    }

    private void sendMessage(Player player, String msg) {
        player.spigot().sendMessage(new TextComponent(msg));
    }

    public List<String> getPlayerlist() {
        return playerlist;
    }

    public Boolean removePlayer(Player player) {
        return playerlist.remove(player.getName().toLowerCase());
    }

    public Boolean addPlayer(Player player) {
        return playerlist.add(player.getName().toLowerCase());
    }
}
