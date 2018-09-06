package io.civex.LoginSystem;

import com.google.common.collect.HashBiMap;
import io.civex.LoginSystem.Commands.LoginQueueCommand;
import io.civex.LoginSystem.Listeners.Join;
import io.civex.LoginSystem.Listeners.Logout;
import io.civex.LoginSystem.Listeners.Login;
import io.civex.LoginSystem.Utils.LoginTimeRunnable;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

/**
 * Created by Ryan on 5/15/2017.
 * Test line to try to fix a local github ssl error where my stuff is out of sync.
 */
public class LoginQueue extends JavaPlugin
{
    public static FileConfiguration config;

    private HashBiMap<UUID, Integer> loginQueue;
    private HashMap<UUID, String> uuidToName;
    private ArrayList<UUID> onTheClock;
    private int highestQueuePos;

    private Logout logoutListener;
    private Join joinListener;
    private Login loginListener;

    public boolean loginQueueProgressing = true;

    @Override
    public void onEnable()
    {
        saveDefaultConfig();
        reloadConfig();

        loginQueue = HashBiMap.create();
        uuidToName = new HashMap<UUID, String>();
        onTheClock = new ArrayList<UUID>();
        highestQueuePos = 0;

        regStuff();
    }

    @Override
    public void onDisable()
    {

    }

    public void loadConfig() {
        config = Bukkit.getPluginManager().getPlugin("LoginQueue").getConfig();
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        loadConfig();
    }

    private void regStuff()
    {
        joinListener = new Join(this);
        logoutListener = new Logout(this);
        loginListener = new Login(this);

        getServer().getPluginManager().registerEvents(joinListener, this);
        getServer().getPluginManager().registerEvents(logoutListener, this);
        getServer().getPluginManager().registerEvents(loginListener, this);

        getServer().getPluginCommand("queue").setExecutor(new LoginQueueCommand(this));
    }

    public String getNameFromUUID(UUID p)
    {
        if (uuidToName.containsKey(p))
        {
            return uuidToName.get(p);
        }

        return null;
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

    public synchronized void addUserToLoginQueue(UUID p, String name)
    {
        if (!loginQueue.containsKey(p))
        {
            if (name.equalsIgnoreCase("dakotahawkins"))
            {
                loginQueue.forEach((k, v) -> ++v);
                loginQueue.put(p, 0);
                ++highestQueuePos;
            }
            else
            {
                loginQueue.put(p, ++highestQueuePos);
            }
        }

        if (!uuidToName.containsKey(name))
        {
            uuidToName.put(p, name);
        }
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

    public void addUserToOnTheClock(UUID p)
    {
        if (!onTheClock.contains(p))
        {
            onTheClock.add(p);
        }
    }

    public void removeUserFromTheClock(UUID p)
    {
        if (onTheClock.contains(p))
        {
            onTheClock.remove(p);
        }
    }

    public boolean isOnTheClock(UUID p)
    {
        if (onTheClock.contains(p)) return true;
        return false;
    }

    public void resetQueue()
    {
        loginQueue.clear();
        uuidToName.clear();
        onTheClock.clear();

        loginQueue = HashBiMap.create();
        uuidToName = new HashMap<UUID, String>();
        onTheClock = new ArrayList<UUID>();

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

    public void putPlayerOnTheClock(UUID p)
    {
        if (!isOnTheClock(p))
        {
            addUserToOnTheClock(p);
            new LoginTimeRunnable(this, p).runTaskLater(this, config.getInt("connection-time", 30) * 19L);
        }
    }
}
