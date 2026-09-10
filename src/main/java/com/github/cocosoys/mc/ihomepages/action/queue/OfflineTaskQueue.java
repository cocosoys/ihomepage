package com.github.cocosoys.mc.ihomepages.action.queue;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.ihomepages.action.model.OfflineTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 离线任务队列服务：管理 {@code homepage_action_pending} 的入队、查询与上线补执行。
 *
 * <p>「及时性执行」的延迟通道：实体类命令在玩家离线时入队（命令快照已填充），
 * 玩家上线由 {@link #deliver(Player)} 逐条补执行并标记状态（一人可多条，UUID 主键互不覆盖）。</p>
 */
public class OfflineTaskQueue {

    private final JavaPlugin plugin;

    public OfflineTaskQueue(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 入队一条离线任务（insert，追加不覆盖）。 */
    public void enqueue(OfflineTask task) {
        if (task == null || task.getPlayerName() == null) {
            return;
        }
        try {
            YAML.Pojo.insert(task);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("离线任务入队失败: " + e.getMessage());
        }
    }

    /** 查询玩家名下全部待补发任务（status=pending，按文件写入顺序；玩家名忽略大小写，防会话名与实际名不一致）。 */
    public List<OfflineTask> pendingOf(String playerName) {
        List<OfflineTask> out = new ArrayList<>();
        if (playerName == null || playerName.isEmpty()) {
            return out;
        }
        try {
            List<OfflineTask> list = YAML.Pojo.select(OfflineTask.class);
            if (list != null) {
                for (OfflineTask t : list) {
                    if (t.getPlayerName() != null
                            && t.getPlayerName().equalsIgnoreCase(playerName)
                            && "pending".equals(t.getStatus())) {
                        out.add(t);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 玩家名下待补发任务数（供状态接口）。 */
    public int pendingCount(String playerName) {
        return pendingOf(playerName).size();
    }

    /** 更新任务状态并记录完成时间。 */
    public void mark(String id, String status) {
        if (id == null) {
            return;
        }
        try {
            OfflineTask t = YAML.Pojo.get(OfflineTask.class, id);
            if (t == null) {
                return;
            }
            t.setStatus(status);
            t.setDoneAt(String.valueOf(System.currentTimeMillis()));
            YAML.Pojo.updateById(t);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 补发玩家名下全部待执行任务（玩家上线时调用；内部保证主线程执行）。
     *
     * @return 成功补发的任务数
     */
    public int deliver(Player player) {
        if (player == null) {
            return 0;
        }
        List<OfflineTask> tasks = pendingOf(player.getName());
        if (tasks.isEmpty()) {
            return 0;
        }
        final int[] delivered = {0};
        Runnable work = () -> {
            for (OfflineTask t : tasks) {
                try {
                    String cmd = t.getCommand();
                    if (cmd != null && !cmd.trim().isEmpty()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.trim());
                    }
                    mark(t.getId(), "granted");
                    delivered[0]++;
                } catch (Throwable ex) {
                    mark(t.getId(), "failed");
                    plugin.getLogger().warning("离线任务补发失败 (" + t.getId() + "): " + ex.getMessage());
                }
            }
            if (delivered[0] > 0) {
                plugin.getLogger().info("已补发玩家 " + player.getName() + " 的离线任务 " + delivered[0] + " 条");
            }
        };
        if (Bukkit.isPrimaryThread()) {
            work.run();
        } else {
            CountDownLatch latch = new CountDownLatch(1);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    work.run();
                } finally {
                    latch.countDown();
                }
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return delivered[0];
    }
}
