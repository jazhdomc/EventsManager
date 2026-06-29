package mc.jazhdo;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public class Bridge extends GameSession {
    private Scoreboard scoreboard;
    private Objective objective;
    private BukkitTask countdown;
    private final int[] score = {0, 0};
    private final Set<String> scoringCooldown = new HashSet<>();

    public Bridge(String player1, String player2, String gameName, int count, EventsManager plugin, EventsListener listener) {
        super(player1, player2, gameName, count, plugin, listener);
    }

    @Override
    public void start(String player1, String player2, EventsManager plugin, World world) {
        this.world = world;
        this.config = plugin.getConfig();
        this.plugin = plugin;

        // Tell players their coresponding teams
        Player p1 = Bukkit.getPlayer(player1);
        Player p2 = Bukkit.getPlayer(player2);
        if (p1 == null || p2 == null) {
            plugin.getLogger().warning("A player disconnected before the game started.");
            return;
        }
        p1.sendMessage(ChatColor.RED + "You are on Team Red!");
        p2.sendMessage(ChatColor.BLUE + "You are on Team Blue!");

        // Update player locations and inventories
        respawnPlayer(p1);
        respawnPlayer(p2);

        // Reset and setup scoreboard
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = scoreboard.registerNewObjective(world.getName(), "dummy");
        objective.setDisplayName(ChatColor.GOLD + "The Bridge");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Do countdown
        countdown = new BukkitRunnable() {
            int count = 3;

            @Override
            public void run() {
                // Show message
                for (Player p : world.getPlayers()) {
                    if (count > 0) {
                        p.sendTitle(ChatColor.YELLOW + Integer.toString(count), "", 5, 18, 7);
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    } else {
                        p.sendTitle(ChatColor.GREEN + "GOO!", ChatColor.WHITE + "Bridge to the other side!", 5, 30, 10);
                        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_LAUNCH, 1f, 1f);
                    }
                }

                // If its time to go
                if (count == 0) {
                    // Remove Red and Blue cages
                    removeCages(new Location(world, config.getInt("bridge.cage.red.x"), config.getInt("bridge.cage.red.y"), config.getInt("bridge.cage.red.z")));
                    removeCages(new Location(world, config.getInt("bridge.cage.blue.x"), config.getInt("bridge.cage.blue.y"), config.getInt("bridge.cage.blue.z")));
                    
                    // Show starting score
                    updateScoreboard();

                    // Don't say any more countdown
                    this.cancel();
                }

                // Go down each second
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void respawnPlayer(Player player) {
        // Get team
        int playerNumber = getPlayerNumber(player.getName());

        // Null check
        if (playerNumber == 0) return;

        // Use redTeam var for better readability
        Boolean redTeam = playerNumber == 1;

        // Reset health, food, and saturation
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(20);

        // (Re)Set player location
        player.teleport(getLocation("bridge.".concat(redTeam ? "red" : "blue").concat("."), world));

        // Clear inventory to prevent stacking items in inventory's back slots
        PlayerInventory inv = player.getInventory();
        inv.clear();

        // Clear effets like golden apple
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());

        // Give sword for usual combat
        inv.setItem(0, new ItemStack(Material.IRON_SWORD));

        // Give pickaxe with faster dig speed metadata
        ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta pickaxeMeta = pickaxe.getItemMeta();
        pickaxeMeta.addEnchant(Enchantment.DIG_SPEED, 5, true);
        pickaxe.setItemMeta(pickaxeMeta);
        inv.setItem(1, pickaxe);

        // Iron axe to attack because of java ed.
        inv.setItem(2, new ItemStack(Material.IRON_AXE));
        inv.setItem(3, new ItemStack(Material.BOW));

        // Give beef for food if on the bridge for long
        inv.setItem(4, new ItemStack(Material.COOKED_BEEF, 8));

        // Golden apple for effects
        inv.setItem(5, new ItemStack(Material.GOLDEN_APPLE, 8));

        // Calculate clay type and fill rest of the inv slots with it
        short clayColor = (short) (redTeam ? 14 : 11);
        for (int i = 6; i < 9; i++) inv.setItem(i, new ItemStack(Material.STAINED_CLAY, 64, clayColor));

        // Give arrows for bow
        inv.setItem(9, new ItemStack(Material.ARROW, 64));
        inv.setItem(10, new ItemStack(Material.ARROW, 64));
    }

    private void removeCages(Location center) {
        // Remove each block in this rectangular prism
        int xHalfLength = config.getInt("bridge.cage.size.x-half-length");
        int yHalfLength = config.getInt("bridge.cage.size.y-half-length");
        for (int x = -xHalfLength; x <= xHalfLength; x++)
            for (int y = 0; y <= config.getInt("bridge.cage.size.height"); y++)
                for (int z = -yHalfLength; z <= yHalfLength; z++) {
                    Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (block.getType() == Material.STAINED_GLASS || block.getType() == Material.STAINED_CLAY) block.setType(Material.AIR);
                }
    }

    private void updateScoreboard() {
        // Set scores
        objective.getScore(ChatColor.RED + "Red").setScore(score[0]);
        objective.getScore(ChatColor.BLUE + "Blue").setScore(score[1]);

        // Update for each player
        for (Player p : world.getPlayers()) p.setScoreboard(scoreboard);
    }

    @Override
    public int winningPlayer() {
        if (score[0] > score[1]) return 1;
        else if (score[0] < score[1]) return 2;
        else return 0;
    }

    @Override
    public void showEnd(String winner) {
        if (countdown != null && !countdown.isCancelled()) countdown.cancel();
        String winnerColor = switch (getPlayerNumber(winner)) {
            case 1 -> "Red";
            case 2 -> "Blue";
            default -> null;
        };
        if (winnerColor == null) winnerColor = "Gray";
        if (winner.equals("")) winner = "Nobody";
        for (Player p : world.getPlayers()) {
            p.sendTitle(ChatColor.valueOf(winnerColor.toUpperCase()) + winner + " Wins!", ChatColor.GOLD + "Good Game!", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        // Simulate void
        Player player = event.getPlayer();
        if (event.getTo().getY() < config.getInt("bridge.void-y")) {
            player.setHealth(0);
            return;
        }

        // Portal touch listener
        Location loc = event.getTo();
        if (loc.getBlockY() <= config.getInt("bridge.portal-y")) {
            // Check touching portal
            Block feet = loc.getBlock();
            Block below = loc.clone().subtract(0, 1, 0).getBlock();
            if (feet.getType() == Material.ENDER_PORTAL || below.getType() == Material.ENDER_PORTAL) {
                // Ignore if this player already triggered a score this crossing
                if (scoringCooldown.contains(player.getName())) return;
                scoringCooldown.add(player.getName());
                Bukkit.getScheduler().runTaskLater(plugin, () -> scoringCooldown.remove(player.getName()), 20L);

                // Determine which team scored
                int playerNumber = getPlayerNumber(player.getName());
                String team = switch (playerNumber) {
                    case 1 -> "Red";
                    case 2 -> "Blue";
                    default -> null;
                };

                // Null check for no team
                if (team == null) return;

                // Determine which side of the portal was scored in
                String portalSide = loc.getBlockZ() < 0 ? "Red" : "Blue";

                // Make sure they scored into their own portal
                if (!portalSide.equals(team)) {
                    // Increase the score
                    int scoreCount = playerNumber - 1;
                    score[scoreCount]++;

                    // Send a who scored message
                    broadcast(ChatColor.valueOf(team.toUpperCase()) + player.getName() + " scored for Team ".concat(team).concat("! ") + ChatColor.RED + score[0] + ChatColor.WHITE + " - " + ChatColor.BLUE + score[1]);
                    
                    // Update scoreboard on the side
                    updateScoreboard();
                    
                    // Reset each player to their locations
                    for (Player p : world.getPlayers()) respawnPlayer(p);

                    // Check for wins
                    if (score[scoreCount] >= config.getInt("bridge.winning")) endGame(getOtherPlayer(player));
                    else {
                        // Display score when someone scores
                        for (Player p : world.getPlayers()) 
                            p.sendTitle(
                                ChatColor.RED + String.valueOf(score[0]) + ChatColor.WHITE + " - " + ChatColor.BLUE + String.valueOf(score[1]),
                                ChatColor.valueOf(team.toUpperCase()) + player.getName() + " scored!",
                                5, 30, 10
                            );
                    }
                } else {
                    broadcast(player.getName() + " has tried to scored in their own portal! Shame on them!");
                    respawnPlayer(player);
                }
            }
        }
    }

    private void broadcast(String msg) {
        for (Player p : world.getPlayers()) p.sendMessage(msg);
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Make sure that the death broadcast doesn't send and no loot drops
        event.setDeathMessage(null);
        event.getDrops().clear();

        // Respawn on next tick to give time
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getEntity();
            player.spigot().respawn();
            respawnPlayer(player);
        }, 1L);
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event) {
        if (
            event.getBlock().getLocation().getY() > 100 ||
            Math.abs(event.getBlock().getLocation().getZ()) > 20 ||
            Math.abs(event.getBlock().getLocation().getX()) > 4
        ) event.setCancelled(true);
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getY() > 100) {
            event.setCancelled(true);
        } else if (Math.abs(event.getBlock().getZ()) > 20) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot build in this area!");
        } else if (Math.abs(event.getBlock().getX()) > 4) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot build this far out!");
        }
    }
}
