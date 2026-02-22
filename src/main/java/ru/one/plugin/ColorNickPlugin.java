package ru.one.plugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ColorNickPlugin extends JavaPlugin implements Listener {

    private Map<String, ChatColor> playerColors = new HashMap<>();
    private Map<String, Boolean> adminSetColors = new HashMap<>();
    private Scoreboard scoreboard;

    private FileConfiguration colorConfig;
    private FileConfiguration langConfig;
    private FileConfiguration mainConfig;
    private String currentLanguage = "ru";

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("colornick").setExecutor(new ColorNickCommand());
        getCommand("setcolornick").setExecutor(new SetColorNickCommand());
        getCommand("unlockcolornick").setExecutor(new UnlockColorNickCommand());

        // Initialize scoreboard for team colors
        scoreboard = getServer().getScoreboardManager().getMainScoreboard();

        // Load configurations
        loadMainConfig();
        loadColors();
        loadLanguage();

        getLogger().info("ColorNickPlugin enabled!");
    }

    @Override
    public void onDisable() {
        // Save data
        saveColors();
        saveLanguage();
        saveMainConfig();
        getLogger().info("ColorNickPlugin disabled!");
    }

    private class ColorNickCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length > 0) {
                // Console usage: /colornick <player> <color>
                if (!(sender instanceof Player) || sender.hasPermission("colornick.admin")) {
                    return handleColorCommand(sender, args);
                }
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage(getMessage("console-only-players"));
                return true;
            }
            Player player = (Player) sender;

            // Check if color was set by admin
            String playerUUID = player.getUniqueId().toString();
            if (adminSetColors.containsKey(playerUUID) && adminSetColors.get(playerUUID) && !player.hasPermission("colornick.admin")) {
                player.sendMessage(getMessage("color-locked-by-admin"));
                return true;
            }

            openColorGUI(player);
            return true;
        }
    }

    private class SetColorNickCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("colornick.admin")) {
                sender.sendMessage(ChatColor.RED + getMessage("no-permission"));
                return true;
            }
            return handleColorCommand(sender, args);
        }
    }

    private class UnlockColorNickCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("colornick.admin")) {
                sender.sendMessage(ChatColor.RED + getMessage("no-permission"));
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + getMessage("usage-unlock"));
                return true;
            }

            Player target = getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + getMessage("player-not-found"));
                return true;
            }

            String playerUUID = target.getUniqueId().toString();
            if (!adminSetColors.containsKey(playerUUID) || !adminSetColors.get(playerUUID)) {
                sender.sendMessage(ChatColor.YELLOW + getMessage("color-not-locked").replace("%player%", target.getName()));
                return true;
            }

            adminSetColors.put(playerUUID, false);
            sender.sendMessage(ChatColor.GREEN + getMessage("color-unlocked").replace("%player%", target.getName()));
            target.sendMessage(ChatColor.GREEN + getMessage("your-color-unlocked"));

            return true;
        }
    }

    private boolean handleColorCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + getMessage("usage-command"));
            sender.sendMessage(ChatColor.YELLOW + getMessage("available-colors"));
            return true;
        }

        Player target = getServer().getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + getMessage("player-not-found"));
            return true;
        }

        ChatColor color = getColorFromString(args[1]);
        if (color == null && !args[1].equalsIgnoreCase("reset")) {
            sender.sendMessage(ChatColor.RED + getMessage("invalid-color"));
            return true;
        }

        boolean isAdmin = sender.hasPermission("colornick.admin") || !(sender instanceof Player);

        if (args[1].equalsIgnoreCase("reset")) {
            removePlayerColor(target);
            adminSetColors.remove(target.getUniqueId().toString());
            sender.sendMessage(ChatColor.GREEN + getMessage("color-reset").replace("%player%", target.getName()));
            target.sendMessage(ChatColor.GREEN + getMessage("your-color-reset"));
        } else {
            setPlayerColor(target, color, isAdmin);
            sender.sendMessage(ChatColor.GREEN + getMessage("color-set").replace("%player%", target.getName()).replace("%color%", color.name()));
            target.sendMessage(ChatColor.GREEN + getMessage("your-color-changed").replace("%color%", color.name()));
        }

        return true;
    }

    private ChatColor getColorFromString(String colorName) {
        switch (colorName.toLowerCase()) {
            case "red": return ChatColor.RED;
            case "blue": return ChatColor.BLUE;
            case "green": return ChatColor.GREEN;
            case "yellow": return ChatColor.YELLOW;
            case "aqua": return ChatColor.AQUA;
            case "light_purple": return ChatColor.LIGHT_PURPLE;
            case "gold": return ChatColor.GOLD;
            case "gray": return ChatColor.GRAY;
            case "white": return ChatColor.WHITE;
            default: return null;
        }
    }

    private void openColorGUI(Player player) {
        Inventory gui = getServer().createInventory(null, 9, getMessage("gui-title"));

        // Colors: RED, BLUE, GREEN, YELLOW, etc.
        Map<Integer, ChatColor> colors = new HashMap<>();
        colors.put(0, ChatColor.RED);
        colors.put(1, ChatColor.BLUE);
        colors.put(2, ChatColor.GREEN);
        colors.put(3, ChatColor.YELLOW);
        colors.put(4, ChatColor.AQUA);
        colors.put(5, ChatColor.LIGHT_PURPLE);
        colors.put(6, ChatColor.GOLD);
        colors.put(7, ChatColor.GRAY);
        colors.put(8, ChatColor.WHITE);

        for (Map.Entry<Integer, ChatColor> entry : colors.entrySet()) {
            ItemStack item = new ItemStack(getWoolMaterial(entry.getValue()), 1);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(entry.getValue() + getMessage("color-" + entry.getValue().name().toLowerCase()));
            item.setItemMeta(meta);
            gui.setItem(entry.getKey(), item);
        }

        player.openInventory(gui);
    }

    private void setPlayerColor(Player player, ChatColor color, boolean isAdmin) {
        String playerUUID = player.getUniqueId().toString();
        playerColors.put(playerUUID, color);
        adminSetColors.put(playerUUID, isAdmin);

        // Set display name
        player.setDisplayName(color + player.getName());

        // Create or update team for tab list and name tag colors
        String teamName = "colornick_" + color.name().toLowerCase();
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.setColor(color);
            team.setPrefix(color.toString());
        }

        // Remove player from other color teams
        for (Team t : scoreboard.getTeams()) {
            if (t.getName().startsWith("colornick_") && !t.getName().equals(teamName)) {
                t.removeEntry(player.getName());
            }
        }

        // Add player to the color team
        team.addEntry(player.getName());
    }

    private void removePlayerColor(Player player) {
        playerColors.remove(player.getUniqueId().toString());

        // Reset display name
        player.setDisplayName(player.getName());

        // Remove from all color teams
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith("colornick_")) {
                team.removeEntry(player.getName());
            }
        }
    }

    private Material getWoolMaterial(ChatColor color) {
        switch (color) {
            case RED: return Material.RED_WOOL;
            case BLUE: return Material.BLUE_WOOL;
            case GREEN: return Material.GREEN_WOOL;
            case YELLOW: return Material.YELLOW_WOOL;
            case AQUA: return Material.CYAN_WOOL;
            case LIGHT_PURPLE: return Material.MAGENTA_WOOL;
            case GOLD: return Material.ORANGE_WOOL;
            case GRAY: return Material.GRAY_WOOL;
            case WHITE: return Material.WHITE_WOOL;
            default: return Material.WHITE_WOOL;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(getMessage("gui-title"))) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        Map<Integer, ChatColor> colors = new HashMap<>();
        colors.put(0, ChatColor.RED);
        colors.put(1, ChatColor.BLUE);
        colors.put(2, ChatColor.GREEN);
        colors.put(3, ChatColor.YELLOW);
        colors.put(4, ChatColor.AQUA);
        colors.put(5, ChatColor.LIGHT_PURPLE);
        colors.put(6, ChatColor.GOLD);
        colors.put(7, ChatColor.GRAY);
        colors.put(8, ChatColor.WHITE);

        ChatColor selectedColor = colors.get(slot);
        if (selectedColor != null) {
            setPlayerColor(player, selectedColor, false);
            player.sendMessage(ChatColor.GREEN + getMessage("your-color-changed").replace("%color%", getMessage("color-" + selectedColor.name().toLowerCase())));
            player.closeInventory();
        }
    }

    private void loadColors() {
        colorConfig = YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(), "colors.yml"));
        for (String key : colorConfig.getKeys(false)) {
            if (key.endsWith("_admin")) {
                String playerUUID = key.substring(0, key.length() - 6); // Remove "_admin" suffix
                adminSetColors.put(playerUUID, colorConfig.getBoolean(key));
            } else {
                try {
                    ChatColor color = ChatColor.valueOf(colorConfig.getString(key));
                    playerColors.put(key, color);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid color for player " + key + ": " + colorConfig.getString(key));
                }
            }
        }

        // Apply colors to online players
        for (Player player : getServer().getOnlinePlayers()) {
            ChatColor color = playerColors.get(player.getUniqueId().toString());
            if (color != null) {
                boolean isAdmin = adminSetColors.getOrDefault(player.getUniqueId().toString(), false);
                setPlayerColor(player, color, isAdmin);
            }
        }
    }

    private void saveColors() {
        if (colorConfig == null) {
            colorConfig = new YamlConfiguration();
        }
        for (Map.Entry<String, ChatColor> entry : playerColors.entrySet()) {
            colorConfig.set(entry.getKey(), entry.getValue().name());
        }
        for (Map.Entry<String, Boolean> entry : adminSetColors.entrySet()) {
            colorConfig.set(entry.getKey() + "_admin", entry.getValue());
        }
        try {
            colorConfig.save(new java.io.File(getDataFolder(), "colors.yml"));
        } catch (Exception e) {
            getLogger().severe("Could not save colors.yml: " + e.getMessage());
        }
    }

    private void loadMainConfig() {
        saveDefaultConfig();
        mainConfig = getConfig();
        currentLanguage = mainConfig.getString("language", "ru");
    }

    private void saveMainConfig() {
        mainConfig.set("language", currentLanguage);
        saveConfig();
    }

    private void loadLanguage() {
        langConfig = YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(), "lang.yml"));
        if (langConfig.getKeys(false).isEmpty()) {
            createDefaultLanguage();
        }
    }

    private void saveLanguage() {
        if (langConfig != null) {
            try {
                langConfig.save(new java.io.File(getDataFolder(), "lang.yml"));
            } catch (Exception e) {
                getLogger().severe("Could not save lang.yml: " + e.getMessage());
            }
        }
    }

    private void createDefaultLanguage() {
        if ("ru".equals(currentLanguage)) {
            // Russian messages
            langConfig.set("gui-title", "Выберите цвет ника");
            langConfig.set("console-only-players", "Эта команда доступна только игрокам!");
            langConfig.set("color-locked-by-admin", "§cВаш цвет установлен администратором и не может быть изменен!");
            langConfig.set("usage-command", "Использование: /colornick <игрок> <цвет>");
            langConfig.set("usage-unlock", "Использование: /unlockcolornick <игрок>");
            langConfig.set("available-colors", "Доступные цвета: red, blue, green, yellow, aqua, light_purple, gold, gray, white, reset");
            langConfig.set("player-not-found", "Игрок не найден!");
            langConfig.set("invalid-color", "Неверный цвет! Доступные цвета: red, blue, green, yellow, aqua, light_purple, gold, gray, white, reset");
            langConfig.set("color-reset", "Цвет игрока %player% сброшен");
            langConfig.set("your-color-reset", "Ваш цвет ника сброшен!");
            langConfig.set("color-set", "Установлен цвет %color% для игрока %player%");
            langConfig.set("your-color-changed", "Ваш цвет ника изменен на %color%!");
            langConfig.set("color-unlocked", "Ограничение на изменение цвета снято для игрока %player%");
            langConfig.set("your-color-unlocked", "Администратор снял ограничение на изменение вашего цвета!");
            langConfig.set("color-not-locked", "Цвет игрока %player% не был заблокирован");
            langConfig.set("no-permission", "У вас нет прав для использования этой команды!");
            langConfig.set("color-red", "Красный");
            langConfig.set("color-blue", "Синий");
            langConfig.set("color-green", "Зеленый");
            langConfig.set("color-yellow", "Желтый");
            langConfig.set("color-aqua", "Голубой");
            langConfig.set("color-light_purple", "Розовый");
            langConfig.set("color-gold", "Золотой");
            langConfig.set("color-gray", "Серый");
            langConfig.set("color-white", "Белый");
        } else {
            // English messages (default)
            langConfig.set("gui-title", "Choose Nick Color");
            langConfig.set("console-only-players", "This command can only be used by players!");
            langConfig.set("color-locked-by-admin", "§cYour color was set by an administrator and cannot be changed!");
            langConfig.set("usage-command", "Usage: /colornick <player> <color>");
            langConfig.set("usage-unlock", "Usage: /unlockcolornick <player>");
            langConfig.set("available-colors", "Available colors: red, blue, green, yellow, aqua, light_purple, gold, gray, white, reset");
            langConfig.set("player-not-found", "Player not found!");
            langConfig.set("invalid-color", "Invalid color! Available colors: red, blue, green, yellow, aqua, light_purple, gold, gray, white, reset");
            langConfig.set("color-reset", "Removed color from player %player%");
            langConfig.set("your-color-reset", "Your nickname color has been reset!");
            langConfig.set("color-set", "Set color %color% for player %player%");
            langConfig.set("your-color-changed", "Your nickname color has been changed to %color%!");
            langConfig.set("color-unlocked", "Color lock removed for player %player%");
            langConfig.set("your-color-unlocked", "Administrator removed the color change restriction!");
            langConfig.set("color-not-locked", "Player %player%'s color was not locked");
            langConfig.set("no-permission", "You don't have permission to use this command!");
            langConfig.set("color-red", "Red");
            langConfig.set("color-blue", "Blue");
            langConfig.set("color-green", "Green");
            langConfig.set("color-yellow", "Yellow");
            langConfig.set("color-aqua", "Aqua");
            langConfig.set("color-light_purple", "Light Purple");
            langConfig.set("color-gold", "Gold");
            langConfig.set("color-gray", "Gray");
            langConfig.set("color-white", "White");
        }
    }

    private String getMessage(String key) {
        return langConfig.getString(key, key);
    }
}