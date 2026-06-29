package mc.jazhdo;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class EventsLoop {
    private final EventsManager plugin;
    private EventsListener listener;
    private BukkitRunnable loop;
    private final List<List<GameSession>> rounds = new ArrayList<>();
    private String eventName = null;
    private String eventGame = null;

    public EventsLoop(EventsManager plugin) {
        this.plugin = plugin;
    }

    // Returns event name (used for /events)
    public String getEventName() {
        return eventName;
    }

    // Returns event game (used for /events)
    public String getEventGame() {
        return eventGame;
    }

    // Sets the listener reference (used to have a circular reference)
    public void setListener(EventsListener listener) {
        this.listener = listener;
    }

    public List<String> parseEventString(String eventString) {
        String[] sections = eventString.split("-");
        if (sections.length != 4) return null;
        String[] dateParts = sections[2].split("/");
        if (dateParts.length != 3) return null;
        String[] timeParts = sections[3].split(":");
        if (timeParts.length != 2) return null;
        LocalDateTime parsed = LocalDateTime.of(Integer.parseInt(dateParts[2]), Month.of(Integer.parseInt(dateParts[0])), Integer.parseInt(dateParts[1]), Integer.parseInt(timeParts[0]), Integer.parseInt(timeParts[1]));
        return Arrays.asList(parsed.toString(), sections[0], sections[1].toLowerCase());
    }

    // Gets the rounds list
    public List<List<GameSession>> getRounds() {
        return rounds;
    }

    // Get the current round list
    public List<GameSession> getCurrentRound() {
        return rounds.getLast();
    }

    public void setupRound() {
        List<String> playerlist = new ArrayList<>(listener.getPlayerlist());
        int playerlistSize = playerlist.size();
        if (playerlistSize <= 1) {
            String winnerMsg = switch (playerlistSize) {
                case 0 -> "No winner found. Everybody who was left had left.";
                case 1 -> ChatColor.GREEN + "Winner: ".concat(playerlist.get(0));
                default -> "Error. Number of players in event is negative.";
            };
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(winnerMsg);
                player.sendMessage(plugin.buildLeaderboard());
            }
            rounds.clear();
        } else {
            int roundNumber = rounds.size();
            rounds.add(new ArrayList<>());
            for (int i = 0; playerlist.size() > 1; i++) {
                String player1 = playerlist.get((int) (Math.random() * playerlist.size()));
                playerlist.remove(player1);
                String player2 = playerlist.get((int) (Math.random() * playerlist.size()));
                playerlist.remove(player2);
                GameSession session = switch (eventGame) {
                    case "Bridge" -> new Bridge(player1, player2, eventGame, i, plugin, listener);
                    default -> new Bridge(player1, player2, eventGame, i, plugin, listener);
                };
                session.start();
                rounds.get(roundNumber).add(session);
            }
        }
    }

    public void start() {
        loop = new BukkitRunnable() {
            FileConfiguration config = plugin.getConfig();
            LocalDateTime startTime;

            @Override
            public void run() {
                // Check if a event is currently in place or not
                LocalDateTime now = LocalDateTime.now();
                if (rounds.isEmpty()) {
                    List<String> upcoming = config.getStringList("upcoming");
                        now = now.truncatedTo(ChronoUnit.MINUTES);
                    for (String whole : upcoming) {
                        // Break down the encoded date and time
                        List<String> parsed = parseEventString(whole);
                        LocalDateTime saidTime = LocalDateTime.parse(parsed.get(0));
                        
                        // Make sure time exists
                        if (saidTime == null) break;

                        // Remove it if its old
                        if (saidTime.isBefore(now)) {
                            List<String> upcome = config.getStringList("upcoming");
                            upcome.remove(whole);
                            config.set("upcoming", upcome);
                            plugin.saveConfig();
                        }

                        // Check if its right now
                        String tempGame = plugin.capitalize(parsed.get(1));
                        if (saidTime.equals(now)) {
                            eventName = tempGame;
                            eventGame = plugin.capitalize(parsed.get(2));
                            setupRound();
                            startTime = now;
                            break;
                        }

                        // Check if its 1m, 5m, 10m, 30m, or 1hr before
                        long timeTill = ChronoUnit.SECONDS.between(now, saidTime);
                        String timeMsg = switch (Long.toString(timeTill)) {
                            case "60" -> "1 Minute";
                            case "300" -> "5 Minutes";
                            case "600" -> "10 Minutes";
                            case "1800" -> "30 Minutes";
                            case "3600" -> "1 Hour";
                            default -> null;
                        };

                        // Null check in case none of the specified times match
                        if (timeMsg == null) continue;

                        // Send status message if one of the specified times before
                        for (Player player : Bukkit.getOnlinePlayers())
                            player.sendMessage("The ".concat(tempGame).concat(" event is starting in ".concat(timeMsg).concat(".")));
                    }
                } else {
                    // Check if its the 10s till end mark or new round time
                    long seconds = ChronoUnit.SECONDS.between(startTime, now);
                    if (seconds != 0) {
                        long since15mins = seconds % 900;
                        if (since15mins == 890)
                            for (GameSession session : getCurrentRound())
                                if (!session.ended()) session.endGame("");
                        else if (since15mins == 0)
                            setupRound();
                    }
                }
            }
        };
        loop.runTaskTimer(plugin, 0l, 20l);
    }

    public void end() {
        if (!loop.isCancelled()) loop.cancel();
    }

    public Boolean gameActive() {
        return !rounds.isEmpty();
    }

    public void midQuit(String player) {
        if (!rounds.isEmpty()) {
            List<GameSession> round = getCurrentRound();
            for (GameSession game : round)
                if (game.containsPlayer(player)) {
                    if (!game.ended()) game.endGame(player);
                    break;
                }
        }
    }
}
