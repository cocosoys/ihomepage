package com.github.cocosoys.mc.ihomepages.action.exec.impl;

import com.github.cocosoys.mc.ihomepages.action.exec.CommandRunner;
import com.github.cocosoys.mc.ihomepages.action.exec.EffectExecutor;
import com.github.cocosoys.mc.ihomepages.enums.CommandClass;
import com.github.cocosoys.mc.ihomepages.exception.ActionException;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.ihomepages.spring.entity.vo.ActionContext;
import com.github.cocosoys.mc.ihomepages.spring.entity.WebActionEffect;

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
    public boolean execute(WebActionEffect effect, ActionContext ctx) throws com.github.cocosoys.mc.ihomepages.exception.ActionException {
        com.github.cocosoys.mc.ihomepages.enums.CommandClass declared = parseTarget(effect.getTarget());
        CommandRunner.RunMode mode = runner.run(effect.getCommand(), ctx,
                ctx.getAction().getOffline(), declared);
        switch (mode) {
            case EXECUTED:
                return true;
            case QUEUED:
                return false; // 已入队，待上线补执行
            default:
                throw new ActionException("rejected-offline",
                        I18n.t("ihomepages.action.exec.need-online",
                                "该动作需要玩家在线才能完成（离线策略为拒绝）"));
        }
    }

    /** 解析 target 声明（console/account/entity；null/空 → 自动分类）。 */
    private static com.github.cocosoys.mc.ihomepages.enums.CommandClass parseTarget(String target) {
        if (target == null || target.trim().isEmpty()) {
            return null;
        }
        String t = target.trim().toLowerCase();
        if ("console".equals(t)) {
            return com.github.cocosoys.mc.ihomepages.enums.CommandClass.CONSOLE;
        }
        if ("account".equals(t)) {
            return com.github.cocosoys.mc.ihomepages.enums.CommandClass.ACCOUNT;
        }
        if ("entity".equals(t)) {
            return CommandClass.ENTITY;
        }
        return null;
    }
}
