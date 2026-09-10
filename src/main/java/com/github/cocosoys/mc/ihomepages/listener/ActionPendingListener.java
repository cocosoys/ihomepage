package com.github.cocosoys.mc.ihomepages.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.github.cocosoys.mc.ihomepages.action.queue.OfflineTaskQueue;

/**
 * 离线任务补发监听器：玩家上线（PlayerJoinEvent）时触发队列补发。
 *
 * <p>配合「网页命令及时执行」的 queue 策略：玩家离线领取的物品类奖励在此刻自动到账。</p>
 */
public class ActionPendingListener implements Listener {

    private final OfflineTaskQueue taskQueue;

    public ActionPendingListener(OfflineTaskQueue taskQueue) {
        this.taskQueue = taskQueue;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        taskQueue.deliver(event.getPlayer());
    }
}
