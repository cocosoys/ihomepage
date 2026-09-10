package com.github.cocosoys.mc.ihomepages.action.config;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.ihomepages.action.exec.EconomyEffectExecutor;
import com.github.cocosoys.mc.ihomepages.action.model.WebAction;
import com.github.cocosoys.mc.ihomepages.action.model.WebActionEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * actions.yml 配置校验器：逐动作、逐字段校验，错误定位到 {@code actions.<id>.<字段路径>}。
 *
 * <p>加载策略由配置顶层 {@code strict} 决定：true=任一错误全部拒绝（沿用旧配置）；false（默认）=
 * 错误动作跳过并返回告警，其余动作正常加载。</p>
 *
 * <p>错误串经 {@link I18n#t} 翻译（key 见 language/*.yml），未命中回退代码内模板。</p>
 */
public class ActionConfigValidator {

    /** 合法动作 ID 正则：小写字母/数字/下划线/连字符，1-32 位。 */
    public static final String ID_PATTERN = "^[a-z0-9_-]{1,32}$";

    /** 合法离线策略值。 */
    private static final List<String> OFFLINE_MODES = java.util.Arrays.asList("run", "queue", "reject");

    /** 合法 target 声明值。 */
    private static final List<String> TARGETS = java.util.Arrays.asList("console", "account", "entity");

    /**
     * 校验单个动作。
     *
     * @param id  动作 ID（配置键）
     * @param act 动作定义
     * @return 校验错误列表（空 = 通过）
     */
    public List<String> validate(String id, WebAction act) {
        List<String> errors = new ArrayList<>();
        if (id == null || !id.matches(ID_PATTERN)) {
            errors.add(I18n.t("action.validate.id-invalid",
                    "actions.{0}.id 不合法（仅小写字母/数字/下划线/连字符，1-32 位）", id));
        }
        if (act == null) {
            errors.add(I18n.t("action.validate.empty", "actions.{0} 为空", id));
            return errors;
        }
        if (act.getName() == null || act.getName().trim().isEmpty()) {
            errors.add(I18n.t("action.validate.name-empty", "actions.{0}.name 为空", id));
        }
        if (act.getPrice() < 0) {
            errors.add(I18n.t("action.validate.price-negative", "actions.{0}.price 不能为负数", id));
        }
        if (act.getOffline() == null || !OFFLINE_MODES.contains(act.getOffline().toLowerCase())) {
            errors.add(I18n.t("action.validate.offline-invalid",
                    "actions.{0}.offline 非法值 \"{1}\"，可选: run / queue / reject",
                    id, act.getOffline()));
        }
        if (act.getCooldownSeconds() < 0) {
            errors.add(I18n.t("action.validate.cooldown-negative",
                    "actions.{0}.cooldown-seconds 不能为负数", id));
        }
        if (act.getClaimPeriod() != null && !act.getClaimPeriod().trim().isEmpty()) {
            String cp = act.getClaimPeriod().trim().toLowerCase();
            if (!java.util.Arrays.asList("once", "daily", "weekly").contains(cp)) {
                errors.add(I18n.t("action.validate.claim-period-invalid",
                        "actions.{0}.claim-period 非法值 \"{1}\"，可选: once / daily / weekly",
                        id, act.getClaimPeriod()));
            }
        }
        if (act.getClaimWindowStart() != null && !act.getClaimWindowStart().trim().isEmpty()
                && !isTimeFormat(act.getClaimWindowStart().trim())) {
            errors.add(I18n.t("action.validate.window-start-format",
                    "actions.{0}.claim-window-start 格式应为 yyyy-MM-dd HH:mm:ss: \"{1}\"",
                    id, act.getClaimWindowStart()));
        }
        if (act.getClaimWindowEnd() != null && !act.getClaimWindowEnd().trim().isEmpty()
                && !isTimeFormat(act.getClaimWindowEnd().trim())) {
            errors.add(I18n.t("action.validate.window-end-format",
                    "actions.{0}.claim-window-end 格式应为 yyyy-MM-dd HH:mm:ss: \"{1}\"",
                    id, act.getClaimWindowEnd()));
        }
        if (act.getEffects() == null || act.getEffects().isEmpty()) {
            errors.add(I18n.t("action.validate.effects-empty",
                    "actions.{0}.effects 为空（至少需要一个效果）", id));
        } else {
            for (int i = 0; i < act.getEffects().size(); i++) {
                validateEffect(id, i, act.getEffects().get(i), errors);
            }
        }
        if (act.getDisplay() != null) {
            if (act.getDisplay().getSort() < 0) {
                errors.add(I18n.t("action.validate.sort-negative",
                        "actions.{0}.display.sort 不能为负数", id));
            }
        }
        return errors;
    }

    private void validateEffect(String id, int index, WebActionEffect effect, List<String> errors) {
        String path = "actions." + id + ".effects[" + index + "]";
        if (effect == null) {
            errors.add(I18n.t("action.validate.effect-empty", "{0} 为空", path));
            return;
        }
        String type = effect.getType();
        if (type == null || type.trim().isEmpty()) {
            errors.add(I18n.t("action.validate.effect-type-empty", "{0}.type 为空", path));
            return;
        }
        type = type.trim().toLowerCase();
        boolean known = "command".equals(type) || EconomyEffectExecutor.supports(type);
        if (!known) {
            errors.add(I18n.t("action.validate.effect-type-unknown",
                    "{0}.type \"{1}\" 未注册（可用: command / economy.take / economy.give）",
                    path, type));
            return;
        }
        if ("command".equals(type)) {
            if (effect.getCommand() == null || effect.getCommand().trim().isEmpty()) {
                errors.add(I18n.t("action.validate.effect-command-empty",
                        "{0}.command 为空（type=command 时必须配置指令模板）", path));
            }
        }
        if (effect.getTarget() != null && !effect.getTarget().trim().isEmpty()
                && !TARGETS.contains(effect.getTarget().trim().toLowerCase())) {
            errors.add(I18n.t("action.validate.effect-target-invalid",
                    "{0}.target 非法值 \"{1}\"，可选: console / account / entity",
                    path, effect.getTarget()));
        }
        if ("economy.give".equals(type) && effect.getAmount() <= 0) {
            errors.add(I18n.t("action.validate.effect-amount-invalid",
                    "{0}.amount 必须大于 0（economy.give 需要配置发放金额）", path));
        }
    }

    /** 时间格式校验：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd。 */
    private static boolean isTimeFormat(String s) {
        try {
            new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
            return true;
        } catch (java.text.ParseException e) {
            try {
                new java.text.SimpleDateFormat("yyyy-MM-dd").parse(s);
                return true;
            } catch (java.text.ParseException e2) {
                return false;
            }
        }
    }
}
