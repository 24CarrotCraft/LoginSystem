package io.civex.LoginSystem;

import com.google.common.collect.HashBiMap;
import io.civex.LoginSystem.Commands.LoginQueueCommand;
import io.civex.LoginSystem.Listeners.Join;
import io.civex.LoginSystem.Listeners.Login;
import io.civex.LoginSystem.Listeners.Logout;
import io.civex.LoginSystem.Listeners.ServerPing;
import io.civex.LoginSystem.Utils.LoginTimeRunnable;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Created by Ryan on 5/15/2017.
 * Test line to try to fix a local github ssl error where my stuff is out of sync.
 */
public class LoginQueue extends JavaPlugin
{
    private static FileConfiguration config;

    private HashBiMap<UUID, Integer> loginQueue;
    private HashMap<UUID, String> uuidToName;
    private HashMap<String, UUID> addressToUuid;
    private ArrayList<UUID> onTheClock;
    private int highestQueuePos;
    private HashMap<UUID, Long> loginTime;
    private List<Long> averageTime;


    public boolean loginQueueProgressing = true;

    @Override
    public void onEnable()
    {
        saveDefaultConfig();
        reloadConfig();

        loginQueue = HashBiMap.create();
        uuidToName = new HashMap<>();
        addressToUuid = new HashMap<>();
        onTheClock = new ArrayList<>();
        highestQueuePos = 0;
        loginTime = new HashMap<>();
        averageTime = new ArrayList<>();

        regStuff();

    }

    @Override
    public void onDisable()
    {

    }

    private void loadConfig() {
        config = Bukkit.getPluginManager().getPlugin("LoginQueue").getConfig();
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        loadConfig();
    }

    private void regStuff()
    {
        getServer().getPluginManager().registerEvents(new Join(this), this);
        getServer().getPluginManager().registerEvents(new Logout(this), this);
        getServer().getPluginManager().registerEvents(new Login(this), this);
        getServer().getPluginManager().registerEvents(new ServerPing(this), this);

        getServer().getPluginCommand("queue").setExecutor(new LoginQueueCommand(this));
    }

    public String getNameFromUUID(UUID p)
    {
        return uuidToName.get(p);
    }

    public UUID getUUIDFromIP(String ip) {
        return addressToUuid.get(ip);
    }

    public void addAverageTime(UUID uuid, Long time) {
        if (averageTime.size() > 10) {
            averageTime.remove(0);
        }
        Long queueTime = loginTime.get(uuid);
        if (queueTime != null) {
            averageTime.add(time - queueTime);
        }
    }

    public String getAverageTime() {
        long total = 0L;
        for (long time : averageTime) {
            total += time;
        }
        int average = averageTime.size() > 0 ? ((int) (total/averageTime.size())) / 1000 : 0;
        return getTime(average);
    }

    public synchronized int getPositionInQueue(UUID p)
    {
        if (loginQueue.containsKey(p))
        {
            return loginQueue.get(p);
        }
        else
        {
            return 0;
        }
    }

    public synchronized void addUserToLoginQueue(Player player, String address)
    {
        UUID p = player.getUniqueId();
        String name = player.getName();
        if (!loginQueue.containsKey(p))
        {
            loginQueue.put(p, ++highestQueuePos);
        }

        if (!uuidToName.containsKey(p))
        {
            uuidToName.put(p, name);
        }

        addressToUuid.put(address, p);
        loginTime.put(p, new Date().getTime());
    }

    public synchronized UUID getUserInPosition(int pos)
    {
        if (loginQueue.inverse().containsKey(pos))
        {
            return loginQueue.inverse().get(pos);
        }
        else
        {
            return null;
        }

    }

    public synchronized void removeUserAtPos(int personBeingRemoved)
    {
        HashBiMap<UUID, Integer> newMap = HashBiMap.create();
        UUID p = loginQueue.inverse().get(personBeingRemoved);
        loginQueue.inverse().remove(personBeingRemoved);
        uuidToName.remove(p);
        loginTime.remove(p);

        for (UUID player : loginQueue.keySet())
        {
            int pos = loginQueue.get(player);

            if (pos > personBeingRemoved)
            {
                pos--;
            }

            newMap.put(player, pos);
        }

        if (isOnTheClock(p))
        {
            removeUserFromTheClock(p);
        }

        highestQueuePos--;
        loginQueue = newMap;
    }

    public synchronized int getHighestQueuePos()
    {
        return highestQueuePos;
    }

    private void addUserToOnTheClock(UUID p)
    {
        if (!onTheClock.contains(p))
        {
            onTheClock.add(p);
            if (config.getBoolean("discord.events.player-on-clock", false)) {
                discordWebhook(getNameFromUUID(p) + " is on the clock!");
            }
        }
    }

    private void removeUserFromTheClock(UUID p)
    {
        onTheClock.remove(p);
    }

    public boolean isOnTheClock(UUID p)
    {
        return onTheClock.contains(p);
    }

    public void resetQueue()
    {
        loginQueue.clear();
        uuidToName.clear();
        onTheClock.clear();

        loginQueue = HashBiMap.create();
        uuidToName = new HashMap<>();
        onTheClock = new ArrayList<>();

        highestQueuePos = 0;
    }

    public void checkIfUsersShouldBeOnClock(int modifier)
    {

        int availableSlots = getServer().getMaxPlayers() - getServer().getOnlinePlayers().size() + modifier;

        if (availableSlots > 0)
        {
            for (int i = availableSlots; i > 0; i--)
            {
                if (getUserInPosition(i) != null)
                {
                    putPlayerOnTheClock(getUserInPosition(i));
                }
            }
        }
    }

    private void putPlayerOnTheClock(UUID p)
    {
        if (!isOnTheClock(p))
        {
            addUserToOnTheClock(p);
            new LoginTimeRunnable(this, p).runTaskLater(this, config.getInt("connection-time", 30) * 19L);
        }
    }

    // There's probably a way better ways to do this
    // It's just a method I had lying around from some other project. /shrug
    public static String getTime(int seconds)
    {
        if (seconds < 60) {
            return seconds + "s";
        }
        int minutes = seconds / 60;

        int s = 60 * minutes;

        int secondsLeft = seconds - s;
        if (minutes < 60)
        {
            if (secondsLeft > 0) {
                return String.valueOf(minutes + "m " + secondsLeft + "s");
            }
            return String.valueOf(minutes + "m");
        }
        if (minutes < 1440)
        {
            String time = "";

            int hours = minutes / 60;

            time = hours + "h";

            int inMins = 60 * hours;

            int leftOver = minutes - inMins;
            if (leftOver >= 1) {
                time = time + " " + leftOver + "m";
            }
            if (secondsLeft > 0) {
                time = time + " " + secondsLeft + "s";
            }
            return time;
        }
        String time = "";

        int days = minutes / 1440;

        time = days + "d";

        int inMins = 1440 * days;

        int leftOver = minutes - inMins;
        if (leftOver >= 1) {
            if (leftOver < 60)
            {
                time = time + " " + leftOver + "m";
            }
            else
            {
                int hours = leftOver / 60;

                time = time + " " + hours + "h";

                int hoursInMins = 60 * hours;

                int minsLeft = leftOver - hoursInMins;
                if (minsLeft >= 1) {
                    time = time + " " + minsLeft + "m";
                }
            }
        }
        if (secondsLeft > 0) {
            time = time + " " + secondsLeft + "s";
        }
        return time;
    }

    public static void discordWebhook(String content) {
        String webhook = config.getString("discord.webhook", "");
        if (!webhook.equals("")) {
            try {
                HttpClient client = HttpClients.createDefault();
                HttpPost post = new HttpPost(webhook);
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("username", "Login Queue"));
                params.add(new BasicNameValuePair("content", content));
                post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
                client.execute(post);
            } catch (Exception ignored) {/* Webhook failed */}
        }
    }
}
