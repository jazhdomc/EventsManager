package mc.jazhdo;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitScheduler;

public abstract class GameSession {
    protected final String player1;
    protected final String player2;
    protected final String gameName;
    protected final String worldName;
    protected World world;
    protected EventsManager plugin;
    protected FileConfiguration config;
    protected String winner;
    protected final BukkitScheduler scheduler;
    protected final EventsListener listener;

    public GameSession(String player1, String player2, String gameName, int count, EventsManager plugin, EventsListener listener) {
        this.player1 = player1;
        this.player2 = player2;
        this.gameName = plugin.capitalize(gameName).replace(" ", "");
        this.worldName = this.gameName.concat("Game").concat(Integer.toString(count));
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.winner = null;
        this.scheduler = Bukkit.getScheduler();
        this.listener = listener;
    }

    // To check if the game ended
    public Boolean ended() {
        return winner != null;
    }

    // Ends the game
    public void endGame(String loser) {
        winner = getOtherPlayer(loser);
        if (winner == null) {
            winner = getPlayer(winningPlayer());
            if (winner == null) winner = "";
        }
        if (!winner.equals("")) listener.getPlayerlist().remove(getOtherPlayer(winner).toLowerCase());
        showEnd(winner);
        scheduler.runTaskLater(plugin, this::finalEnd, 100);
    }

    // Get a location from a config value
    public Location getLocation(String prefix, World world) {
        return new Location(
            world,
            config.getDouble(prefix.concat("x")),
            config.getDouble(prefix.concat("y")),
            config.getDouble(prefix.concat("z")),
            (float) config.getDouble(prefix.concat("yaw")),
            (float) config.getDouble(prefix.concat("pitch"))
        );
    }

    // Finish showing scores and kick players and cleanup
    private void finalEnd() {
        // Reset players and send them to lobby
        for (Player p : world.getPlayers()) {
            p.getInventory().clear();
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            p.teleport(getLocation("spawn.", Bukkit.getWorld(config.getString("worldname"))));
        }

        // Cleanup world 5s later
        scheduler.runTaskLater(plugin, this::cleanup, 100);
    }

    // To get the winner (If its null, the game hasn't ended, playername if there was a winner, "" if there wasn't a winner (tie))
    public String getWinner() {
        return winner;
    }

    private String getPlayer(int number) {
        return switch (number) {
            case 1 -> player1;
            case 2 -> player2;
            default -> null;
        };
    }

    // To get the first player
    public String getPlayer1() {
        return player1;
    }

    // To get the second player
    public String getPlayer2() {
        return player2;
    }

    // Returns the other player in the 1v1 game or null if input player not found
    public String getOtherPlayer(String player) {
        // Return right player (if not a player in this game, return null)
        return switch (getPlayerNumber(player)) {
            case 1 -> getPlayer2();
            case 2 -> getPlayer1();
            default -> null;
        };
    }

    // getOtherPlayer but with player object as a input
    public String getOtherPlayer(Player player) {
        return getOtherPlayer(player.getName());
    }

    // Returns the game world
    public World getWorld() {
        return world;
    }

    // Get the winner's playerNumber
    private int getWinnerPlayerNumber() {
        if (winner.equalsIgnoreCase(player1)) return 1;
        else if (winner.equalsIgnoreCase(player2)) return 2;
        else if (winner.equals("")) return 4;
        else return 0;
    }

    // Get ChatColor to use depending on whether a player has won or not
    public ChatColor getPlayerColor(int playerNumber) {
        int winnerNumber = getWinnerPlayerNumber();
        if (winnerNumber == 0) return ChatColor.WHITE;
        else if (winnerNumber == 4 || playerNumber == winnerNumber) return ChatColor.GREEN;
        else return ChatColor.RED;
    }

    // Returns 0 for neither, 1 for player1, and 2 for player2
    public byte getPlayerNumber(String playername) {
        if (playername.equalsIgnoreCase(player1)) return 1;
        else if (playername.equalsIgnoreCase(player2)) return 2;
        return 0;
    }

    // To check if a player was in the round
    public Boolean containsPlayer(String player) {
        return player1.equalsIgnoreCase(player) || player2.equalsIgnoreCase(player);
    }

    // Start the game
    public void start() {
        // Get a map
        List<String> maps = config.getStringList(getConfigPrefix().concat("maps"));
        if (maps.isEmpty()) {
            plugin.getLogger().warning("No maps found");
            return;
        }
        String map = maps.get(new Random().nextInt(maps.size()));

        // Make sure map exists
        File templateFolder = new File(Bukkit.getWorldContainer(), map);
        Logger logger = plugin.getLogger();
        if (!templateFolder.exists()) logger.warning("Game ".concat(gameName).concat("'s world map ".concat(map).concat(" doesn't exist.")));

        // Delete world if already exists from weird shutdown
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) Bukkit.unloadWorld(existing, false);

        // Delete world folder if already exists from weird shutdown
        File gameFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (gameFolder.exists()) plugin.deleteFolder(gameFolder);

        // Generate world folder's map and load the world in
        plugin.copyFolder(templateFolder, gameFolder);
        this.world = new WorldCreator(worldName).environment(World.Environment.NORMAL).createWorld();

        // Make sure world exists before continuing
        if (world == null) {
            logger.warning("World ".concat(worldName).concat(" was unsuccessfully created."));
            return;
        }

        // Start game
        start(player1, player2, plugin, world);
    }

    // Cleanup files
    public void cleanup() {
        // Make sure world exists
        if (world == null) return;

        // Unload & Delete
        Bukkit.unloadWorld(world, false);
        plugin.deleteFolder(new File(Bukkit.getWorldContainer(), worldName));
    }

    // Interface parts
    public abstract void start(String player1, String player2, EventsManager plugin, World world);
    public abstract void showEnd(String winner);
    public abstract int winningPlayer();
    public abstract void onPlayerMove(PlayerMoveEvent event);
    public abstract void onPlayerDeath(PlayerDeathEvent event);
    public abstract void onBlockBreak(BlockBreakEvent event);
    public abstract void onBlockPlace(BlockPlaceEvent event);
    public abstract String getConfigPrefix();
}
