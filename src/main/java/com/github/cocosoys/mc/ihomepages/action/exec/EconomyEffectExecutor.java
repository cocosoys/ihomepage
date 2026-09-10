package com.github.cocosoys.mc.ihomepages.action.exec;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.ihomepages.action.model.ActionContext;
import com.github.cocosoys.mc.ihomepages.action.model.WebActionEffect;

/**
 * 内置效果：{@code economy.take} / {@code economy.give} —— 货币操作。
 *
 * <p>实现方式：<b>走经济命令</b>（默认 {@code eco take/give {player} {amount}}），
 * 与「命令及时执行」统一模型一致，且不硬依赖 Vault API —— 服务器使用 Essentials/CMI 等
 * 提供 eco 命令的经济插件即可；可用 {@code effect.command} 覆盖为其他经济插件命令。</p>
 *
 * <p>金额口径：{@code economy.take} 默认扣 {@code price × 数量}（可被 effect.amount 覆盖）；
 * {@code economy.give} 必须配置 {@code effect.amount}（发放金额）。命令类别强制 ACCOUNT（离线可立即执行）。</p>
 */
public class EconomyEffectExecutor implements EffectExecutor {

    private static final String DEFAULT_TAKE = "eco take {player} {amount}";
    private static final String DEFAULT_GIVE = "eco give {player} {amount}";

    private final CommandRunner runner;

    public EconomyEffectExecutor(CommandRunner runner) {
        this.runner = runner;
    }

    @Override
    public String type() {
        return "economy"; // 占位 type（实际由 take/give 两个子类型分发，见 {@link #supports(String)}）
    }

    /** 本执行器支持的子类型。 */
    public static boolean supports(String type) {
        return "economy.take".equals(type) || "economy.give".equals(type);
    }

    @Override
    public boolean execute(WebActionEffect effect, ActionContext ctx) throws ActionException {
        String type = effect.getType() == null ? "" : effect.getType();
        boolean take = "economy.take".equals(type);
        boolean give = "economy.give".equals(type);
        if (!take && !give) {
            throw new ActionException("invalid-type",
                    I18n.t("action.exec.invalid-economy-type", "不支持的经济效果类型: {0}", type));
        }

        double amount;
        if (take) {
            // 默认扣 price × 数量；effect.amount > 0 时覆盖
            amount = effect.getAmount() > 0 ? effect.getAmount() : ctx.getAction().getPrice() * Math.max(1, ctx.getAmount());
        } else {
            amount = effect.getAmount();
            if (amount <= 0) {
                throw new ActionException("invalid-amount",
                        I18n.t("action.exec.invalid-amount",
                                "economy.give 效果必须配置 amount（发放金额）"));
            }
        }

        String template = effect.getCommand() != null && !effect.getCommand().trim().isEmpty()
                ? effect.getCommand().trim()
                : (take ? DEFAULT_TAKE : DEFAULT_GIVE);
        // 先填充金额/玩家名，再交 CommandRunner（ACCOUNT 类：离线也立即执行）
        String cmd = template
                .replace("{amount}", formatAmount(amount))
                .replace("{player}", ctx.getPlayerName());
        CommandRunner.RunMode mode = runner.run(cmd, ctx, ctx.getAction().getOffline(), CommandClass.ACCOUNT);
        if (mode == CommandRunner.RunMode.QUEUED) {
            return false; // 理论不会发生（ACCOUNT 强制立即），防御性保留
        }
        return true;
    }

    /** 金额格式化：整数省略小数（100 → "100"，100.5 → "100.5"）。 */
    private static String formatAmount(double amount) {
        long l = (long) amount;
        if (l == amount) {
            return String.valueOf(l);
        }
        return String.valueOf(amount);
    }
}
