package com.github.cocosoys.mc.ihomepages.action.exec;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.cocosoys.mc.ihomepages.action.model.ActionContext;
import com.github.cocosoys.mc.ihomepages.action.model.OfflineTask;
import com.github.cocosoys.mc.ihomepages.action.queue.OfflineTaskQueue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * 命令执行器 —— 「及时性执行」核心：分类（C/A/E）+ 在线/离线分流（run / queue / reject）。
 *
 * <ul>
 *   <li>玩家在线 → 一律主线程立即执行；</li>
 *   <li>离线 + CONSOLE/ACCOUNT 类 → 主线程立即执行（账号/服务器操作不依赖实体）；</li>
 *   <li>离线 + ENTITY 类 → 按动作 offline 策略：run（声明安全，立即）/ queue（入队，上线补执行）/ reject（拒绝）；</li>
 * </ul>
 *
 * <p>入队的命令在入队时已完成占位符填充（快照独立于 actions.yml 后续修改），
 * 玩家上线由 {@link OfflineTaskQueue#deliver} 直接执行。</p>
 */
public class CommandRunner {

    /** 单条命令的执行结局。 */
    public enum RunMode {
        /** 已立即执行。 */
        EXECUTED,
        /** 已离线入队，待上线补执行。 */
        QUEUED,
        /** 离线策略拒绝，未执行。 */
        REJECTED
    }

    private final JavaPlugin plugin;
    private final CommandClassifier classifier;
    private final OfflineTaskQueue taskQueue;

    public CommandRunner(JavaPlugin plugin, CommandClassifier classifier, OfflineTaskQueue taskQueue) {
        this.plugin = plugin;
        this.classifier = classifier;
        this.taskQueue = taskQueue;
    }

    /**
     * 执行一条命令模板（按上下文分流）。
     *
     * @param commandTemplate 命令模板（支持 {player} / {amount}）
     * @param ctx             执行上下文
     * @param offlineMode     动作 offline 策略（run / queue / reject）
     * @param declaredClass   显式声明的类别（可为 null，自动分类）
     * @return 执行结局
     */
    public RunMode run(String commandTemplate, ActionContext ctx, String offlineMode, CommandClass declaredClass) {
        if (commandTemplate == null || commandTemplate.trim().isEmpty()) {
            return RunMode.EXECUTED; // 空命令视为无操作成功
        }
        String cmd = fill(commandTemplate, ctx);
        CommandClass clazz = declaredClass != null ? declaredClass : classifier.classify(commandTemplate);

        // 在线：一律立即执行
        if (ctx.isOnline()) {
            runOnPrimaryThread(cmd);
            return RunMode.EXECUTED;
        }
        // 离线 + CONSOLE/ACCOUNT：不依赖在线实体，立即执行
        if (clazz == CommandClass.CONSOLE || clazz == CommandClass.ACCOUNT) {
            runOnPrimaryThread(cmd);
            return RunMode.EXECUTED;
        }
        // 离线 + ENTITY：按策略
        String mode = offlineMode == null ? "queue" : offlineMode.toLowerCase();
        if ("run".equals(mode)) {
            runOnPrimaryThread(cmd);
            return RunMode.EXECUTED;
        }
        if ("reject".equals(mode)) {
            return RunMode.REJECTED;
        }
        // queue（默认）：入队，上线补执行
        taskQueue.enqueue(new OfflineTask(
                UUID.randomUUID().toString(),
                ctx.getPlayerName(),
                ctx.getPlayerUuid(),
                ctx.getAction().getId(),
                cmd,
                String.valueOf(System.currentTimeMillis()),
                null,
                "pending"));
        return RunMode.QUEUED;
    }

    /** 占位符填充：{player} → 玩家名，{amount} → 数量。 */
    private static String fill(String template, ActionContext ctx) {
        String s = template.replace("{player}", ctx.getPlayerName());
        return s.replace("{amount}", String.valueOf(ctx.getAmount()));
    }

    /** 确保命令在主线程执行（Bukkit.dispatchCommand 线程安全要求）。 */
    private void runOnPrimaryThread(String cmd) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
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
}
