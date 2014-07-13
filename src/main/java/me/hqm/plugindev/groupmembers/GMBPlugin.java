package me.hqm.plugindev.groupmembers;

import com.google.common.collect.Lists;
import mkremins.fanciful.FancyMessage;
import org.anjocaido.groupmanager.GroupManager;
import org.anjocaido.groupmanager.data.Group;
import org.anjocaido.groupmanager.data.User;
import org.anjocaido.groupmanager.dataholder.WorldDataHolder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Data export into yaml files for GroupManager.
 */
public class GMBPlugin extends JavaPlugin implements CommandExecutor {
    private static final GroupManager GROUP_MANAGER = (GroupManager) Bukkit.getPluginManager().getPlugin("GroupManager");

    @Override
    public void onEnable() {
        getCommand("groupmembers").setExecutor(this);

        if(!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        final boolean isPlayer = sender instanceof Player;
        if("groupmembers".equalsIgnoreCase(command.getName())) {
            if(args.length == 0) {
                sender.sendMessage(ChatColor.GOLD + "GROUP LIST:");
                if (isPlayer) {
                    new FancyMessage("- ").
                            color(ChatColor.WHITE).
                            then("All groups").
                            style(ChatColor.ITALIC).
                            tooltip("This will make all of the groups below into their own files.").
                            command("/groupmembers allgroups blah blah").send((Player) sender);
                }
                for(WorldDataHolder worldDataHolder : GROUP_MANAGER.getWorldsHolder().allWorldsDataList()) {
                    formattedGroupList(isPlayer, " ", sender, worldDataHolder);
                }
                return true;
            } else if (args.length == 1) {
                if(GROUP_MANAGER.getWorldsHolder().isWorldKnown(args[0])) {
                    formattedGroupList(isPlayer, "", sender, GROUP_MANAGER.getWorldsHolder().getWorldData(args[0]));
                }
                else {
                    sender.sendMessage(ChatColor.RED + "No such world exists (" + args[0] + ").");
                }
                return true;
            } else if (args.length == 2) {
                if(GROUP_MANAGER.getWorldsHolder().isWorldKnown(args[0])) {
                    WorldDataHolder dataHolder = GROUP_MANAGER.getWorldsHolder().getWorldData(args[0]);
                    if(dataHolder.getGroups().containsKey(args[1].toLowerCase())) {
                        startAsyncFileTask(isPlayer, sender, dataHolder.getGroup(args[1]));
                    }
                    else {
                        sender.sendMessage(ChatColor.RED + "No such group exists (" + args[1] + ").");
                    }
                    return true;
                }
                else {
                    sender.sendMessage(ChatColor.RED + "No such world exists (" + args[0] + ").");
                }
            } else if (args.length == 3 && "allgroups".equalsIgnoreCase(args[0])) {
                for(WorldDataHolder worldDataHolder : GROUP_MANAGER.getWorldsHolder().allWorldsDataList()) {
                    for(Group group : worldDataHolder.getGroupList()) {
                        startAsyncFileTask(isPlayer, sender, group);
                    }
                }
                return true;
            }
        }
        return false;
    }

    public void formattedGroupList(final boolean isPlayer, String prefix, CommandSender sender, WorldDataHolder dataHolder) {
        Collection<Group> groupList = getGroups(dataHolder);
        if(!groupList.isEmpty()) {
            sender.sendMessage(prefix + ChatColor.YELLOW + dataHolder.getName().toUpperCase() + ":");
            for (Group group : groupList) {
                if (isPlayer) {
                    makeFancy(group, prefix, dataHolder.getName()).send((Player) sender);
                } else {
                    sender.sendMessage(prefix + "- " + ChatColor.ITALIC + group.getName());
                }
            }
        }
    }

    private Collection<Group> getGroups(WorldDataHolder world) {
        return world.getGroupList();
    }

    private List<User> getMembers(Group group) {
        List<User> members = Lists.newArrayList();
        for(User user : group.getDataSource().getUserList()) {
            if(user.getGroup().equals(group)) {
                members.add(user);
            }
        }
        return members;
    }

    private FancyMessage makeFancy(Group group, String prefix, String worldName) {
        return new FancyMessage(prefix + "- ").
                color(ChatColor.WHITE).
                then(group.getName()).
                style(ChatColor.ITALIC).
                tooltip("Members: " + getMembers(group).size()).
                command("/groupmembers " + worldName + " " + group.getName());
    }

    private void startAsyncFileTask(final boolean isPlayer, final CommandSender sender, final Group group) {
        sender.sendMessage(ChatColor.YELLOW + "Writing \"" + group.getName() + "\" group file...");
        Bukkit.getScheduler().scheduleAsyncDelayedTask(this, new Runnable() {
            @Override
            public void run() {
                int members = writeToFile(group);
                if(members > -1) {
                    if (!isPlayer || ((Player) sender).isOnline()) {
                        sender.sendMessage(ChatColor.YELLOW.toString() + members + " players written to " + group.getName().toLowerCase() + ".yml");
                    }
                }
                else {
                    if (isPlayer) {
                        if (((Player) sender).isOnline()) {
                            sender.sendMessage(ChatColor.RED.toString() + "Check the console for errors, couldn't write to the \"" + group.getName() + "\" group list.");
                        }
                    }
                    else {
                        sender.sendMessage(ChatColor.RED.toString() + "ERR: Couldn't write to the \"" + group.getName() + "\" group list.");
                    }
                }
            }
        });
    }

    private int writeToFile(Group group) {
        List<String> names = Lists.newArrayList();
        List<String> uuids = Lists.newArrayList();
        List<String> error = Lists.newArrayList();
        // Sort the data
        for(User user : getMembers(group)) {
            if(!names.contains(user.getLastName())) {
                names.add(user.getLastName());
            } else {
                error.add("DUPLICATE NAME: " + user.getName());
            }
            if(!user.getUUID().equals(user.getLastName())) {
                uuids.add(user.getUUID());
            } else {
                error.add("MISSING UUID: " + user.getName());
            }
        }
        try {
            // ALWAYS A NEW FILE
            File file = new File(getDataFolder().getPath() + "/" + group.getName().toLowerCase() + ".yml");
            if(file.exists()) {
                file.delete();
            }

            // CREATE
            file.createNewFile();
            FileConfiguration memberFile = YamlConfiguration.loadConfiguration(file);

            // DATA
            int size = names.size() > uuids.size() ? names.size() : uuids.size();
            memberFile.set("group-name", group.getName());
            memberFile.set("group-world", group.getDataSource().getName());
            memberFile.set("group-size", size);
            memberFile.set("member-names", names);
            memberFile.set("member-ids", uuids);
            if(!error.isEmpty()) {
                memberFile.set("errors", error);
            }

            // SAVE
            memberFile.save(file);

            // RETURN
            return size;
        } catch (IOException oops) {
            getLogger().severe("---------- GMB BEGIN ----------");
            getLogger().severe("There was a problem when creating a new file:");
            oops.printStackTrace();
            getLogger().severe("----------- GMB END -----------");
        }
        return -1;
    }
}
