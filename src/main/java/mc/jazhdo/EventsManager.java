package mc.jazhdo;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.chat.TextComponent;

public class EventsManager extends JavaPlugin {
    private EventsLoop loop;
    private EventsListener listener;

    private class OnCommand implements CommandExecutor {
        private final FileConfiguration config;
        private final EventsLoop loop;

        public OnCommand(EventsManager plugin, EventsLoop loop) {
            this.config = plugin.getConfig();
            this.loop = loop;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            // Make sure console isn't sending this
            if (!(sender instanceof Player)) {
                if (sender != null) sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return false;
            }

            // Check permissions
            Player player = (Player) sender;
            String cmdName = command.getName();
            if (!checkPerms(player, cmdName)) return true;

            switch (cmdName) {
                case "join" -> {
                    if (!loop.gameActive()) {
                        if (!listener.getPlayerlist().contains(player.getName().toLowerCase())) {
                            if (listener.addPlayer(player)) 
                                for (Player p : Bukkit.getOnlinePlayers()) 
                                    p.sendMessage(config.getString("join-msg").replace("%p", player.getName()));
                        } else player.sendMessage("You are already in the event.");
                    } else player.sendMessage("You cannot join mid-event.");
                }
                case "leave" -> {
                    if (listener.removePlayer(player)) {
                        if (loop.gameActive()) loop.midQuit(player.getName());
                        for (Player p : Bukkit.getOnlinePlayers())
                            p.sendMessage(config.getString("leave-msg").replace("%p", player.getName()));
                    } else player.sendMessage("An error occured while removing you.");
                }
                case "leaderboard" -> {
                    // Get msg
                    String text = buildLeaderboard();
                    if (!loop.gameActive()) text = "No event is currently active. Type \"/events\" in chat to learn more.";

                    // Send to player
                    player.spigot().sendMessage(new TextComponent(text));
                }
                case "events" -> {
                    // Set correct message
                    String text;
                    List<String> upcoming = config.getStringList("upcoming");
                    if (loop.gameActive()) text = "Current Event: ".concat(loop.getEventName()).concat("\nGame: ").concat(capitalize(loop.getEventGame()));
                    else if (upcoming.isEmpty()) text = "No events found.";
                    else {
                        List<String> event = loop.parseEventString(upcoming.get(0));
                        text = "Next Event: ".concat(event.get(1)).concat("\nGame: ").concat(capitalize(event.get(2))).concat("\nDate: ").concat(LocalDateTime.parse(event.get(0)).format(DateTimeFormatter.ofPattern("MM/dd/yyyy at HH:mm")));
                    }

                    // Send message
                    player.spigot().sendMessage(new TextComponent(text));
                }
                case "view" -> {
                    // Send a error if no event is currently active
                    if (!loop.gameActive()) {
                        player.sendMessage("No event is currently active. Check the next event by typing \"/events\" in chat.");
                        return true;
                    }

                    // Make sure args[0] was given
                    if (args.length == 0) {
                        player.sendMessage("\"player\" argument required. Teleporting you to the lobby...");
                        player.setGameMode(GameMode.SURVIVAL);
                        player.teleport(listener.getSpawn());
                        return true;
                    }

                    // Make sure player is not playing
                    List<GameSession> currentRound = loop.getCurrentRound();
                    for (GameSession session : currentRound)
                        if (session.containsPlayer(player.getName()) && !session.ended()) {
                            player.sendMessage(ChatColor.RED + "You cannot spectate another game while playing one.");
                            return true;
                        }

                    // Try to find the right game and teleport player to it
                    for (GameSession session : currentRound)
                        if (session.containsPlayer(args[0]) && !session.ended()) {
                            player.sendMessage("Teleporting you to the game...");
                            player.setGameMode(GameMode.SPECTATOR);
                            player.teleport(new Location(session.getWorld(), 0, 0, 0));
                            return true;
                        }

                    // Teleport player to lobby if game not found
                    player.sendMessage("No game found with player ".concat(args[0]).concat(". Teleporting you to the lobby..."));
                    player.teleport(listener.getSpawn());
                }
            }
            
            // Say command is valid
            return true;
        }
    }

    // Build the leaderboard
    public String buildLeaderboard() {
        String text = "";
        List<List<GameSession>> rounds = loop.getRounds();
        for (int i = 0; i < rounds.size(); i++) {
            List<GameSession> game = rounds.get(i);
            text += ChatColor.GOLD + "\nRound ".concat(Integer.toString(i + 1));
            for (GameSession session : game) {
                String player1 = session.getPlayer1();
                String player2 = session.getPlayer2();
                text += "\n" + session.getPlayerColor(1) + player1 + ChatColor.GRAY + " versus " + session.getPlayerColor(2) + player2;
            }
        }
        if (text.equals("")) return "";
        return text.substring(1);
    }

    // Capitalize every word
    public String capitalize(String input) {
        input = input.toLowerCase();
        char prevChar = '\0';
        char[] output = input.toCharArray();
        output[0] = Character.toUpperCase(input.charAt(0));
        for (int i = 1; i < input.length(); i++) {
            char current = input.charAt(i);
            if (prevChar == (char) ' ') output[i] = Character.toUpperCase(current);
            else output[i] = current;
            prevChar = current;
        }
        return String.valueOf(output);
    }

    public void copyFolder(File src, File dest) {
        dest.mkdirs();
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) copyFolder(f, new File(dest, f.getName()));
            else {
                try {
                    java.nio.file.Files.copy(f.toPath(), new File(dest, f.getName()).toPath());
                } catch (IOException e) {
                    getLogger().warning("An error occurred copying folder ".concat(src.getPath()));
                }
            }
        }
        File uidFile = new File(dest, "uid.dat");
        if (uidFile.exists()) uidFile.delete();
    }

    public void deleteFolder(File folder) {
        File[] fileList = folder.listFiles();
        if (fileList != null) 
            for (File file : fileList) 
                if (file.isDirectory()) deleteFolder(file);
                else file.delete();
        folder.delete();
    }

    @Override
    public void onEnable() {
        getLogger().info("EventsManager starting...");

        saveDefaultConfig();

        loop = new EventsLoop(this);
        listener = new EventsListener(this, loop);
        loop.setListener(listener);
        loop.start();
        OnCommand commands = new OnCommand(this, loop);
        getServer().getPluginManager().registerEvents(listener, this);
        for (String cmd : List.of("join", "events", "leaderboard", "leave", "view")) getCommand(cmd).setExecutor(commands);
    }

    @Override
    public void onDisable() {
        getLogger().info("EventsManager shutting down...");
        if (loop != null) loop.end();
    }

    public Boolean checkPerms(Player player, String perm) {
        if (player.hasPermission("events.".concat(perm))) return true;
        player.sendMessage(ChatColor.RED + "Sorry, but you do not have permission to perform that command. If you believe this is a error, contact a mod or admin for help.");
        return false;
    }
}