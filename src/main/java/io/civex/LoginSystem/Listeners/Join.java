package io.civex.LoginSystem.Listeners;

import io.civex.LoginSystem.LoginQueue;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Date;

/**
 * Created by Ryan on 5/16/2017.
 */
public class Join implements Listener
{
    private LoginQueue plugin;

    public Join(LoginQueue pl)
    {
        this.plugin = pl;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        Player p = event.getPlayer();

        int beforeSize = plugin.getHighestQueuePos();

        int queuePos = plugin.getPositionInQueue(p.getUniqueId());

        if (queuePos > 0)
        {
            plugin.addAverageTime(p.getUniqueId(), new Date().getTime());
            plugin.removeUserAtPos(queuePos);
        }

        plugin.checkIfUsersShouldBeOnClock(0);

        if (plugin.getConfig().getBoolean("discord.events.queue-empty", false) && beforeSize != 0 && plugin.getHighestQueuePos() == 0) {
            LoginQueue.discordWebhook("The queue is empty!");
        }
    }
}
