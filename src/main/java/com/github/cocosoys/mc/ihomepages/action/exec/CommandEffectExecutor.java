package com.github.cocosoys.mc.ihomepages.action.exec;

import com.github.cocosoys.mc.ihomepages.action.model.ActionContext;
import com.github.cocosoys.mc.ihomepages.action.model.WebActionEffect;

/**
 * 内置效果：{@code command} —— 执行服务器指令（经 CommandRunner 分类与离线分流）。
 *
 * <p>支持 {@code target:} 显式声明类别（console/account/entity），不填由 CommandClassifier 自动识别。</p>
 */
public class CommandEffectExecutor implements EffectExecutor {

    private final CommandRunner runner;

    public CommandEffectExecutor(CommandRunner runner) {
        this.runner = runner;
    }

    @Override
    public String type() {
        return "command";
    }

    @Override
    public boolean execute(WebActionEffect effect, ActionContext ctx) throws ActionException {
        CommandClass declared = parseTarget(effect.getTarget());
        CommandRunner.RunMode mode = runner.run(effect.getCommand(), ctx,
                ctx.getAction().getOffline(), declared);
        switch (mode) {
            case EXECUTED:
                return true;
            case QUEUED:
                return false; // 已入队，待上线补执行
            default:
                throw new ActionException("rejected-offline",
                        "该动作需要玩家在线才能完成（离线策略为拒绝）");
        }
    }

    /** 解析 target 声明（console/account/entity；null/空 → 自动分类）。 */
    private static CommandClass parseTarget(String target) {
        if (target == null || target.trim().isEmpty()) {
            return null;
        }
        String t = target.trim().toLowerCase();
        if ("console".equals(t)) {
            return CommandClass.CONSOLE;
        }
        if ("account".equals(t)) {
            return CommandClass.ACCOUNT;
        }
        if ("entity".equals(t)) {
            return CommandClass.ENTITY;
        }
        return null;
    }
}
