-- ============================================================================
-- iHomePages · 网页动作系统初始化建表脚本
-- 适用: MySQL 5.x / 8.x / MariaDB（SQLite 亦兼容，见下方说明）
-- ----------------------------------------------------------------------------
-- 【重要说明】
-- 1) 【初始化 = 最新版本完整结构】本脚本始终维护为当前 schemaVersion 的最终形态
--    （含各版本迁移新增的列 / 索引）；全新安装直接使用本脚本。插件运行时若表
--    不存在也会自动 CREATE TABLE IF NOT EXISTS（AutoOps.ensureTables + 容忍式
--    补列），二者不冲突，可重复执行（IF NOT EXISTS 幂等）。
-- 2) 列名规则: 实体字段驼峰转小写下划线（cooldownSeconds → cooldown_seconds），
--    与 YAML 端 data/<表名>.yml 的键名完全一致（双端同构）。
-- 3) 类型映射: String → VARCHAR(255)；主键 String → VARCHAR(64)
--    （utf8mb4 下 VARCHAR(255) 主键索引超长 1020B > 1000B）；
--    int → INT；boolean → TINYINT；double → DOUBLE；
--    自增主键（@TableId(type = IdType.AUTO)，如 homepage_action_effect.id）→
--    BIGINT AUTO_INCREMENT PRIMARY KEY（YAML 端自动取最大数字键 +1，双端同为数字键）。
-- 4) 表名前缀: 若配置了 storage.backends.mysql.table-prefix（默认空），
--    请将下方所有表名统一加上该前缀（如 homepage_action → <前缀>homepage_action）。
-- 5) SQLite 兼容: SQLite 不支持 COMMENT 子句（类型宽松），如需在 SQLite 手工执行，
--    请删除各列的 COMMENT 子句；插件运行时会自动建表，不依赖本脚本。
-- 6) 版本迁移（sql/migrations/V<n>/<表名>.sql，仅 MySQL）：仅当存在旧数据
--    且 meta.schema_version < 声明版本时增量执行 V(cur+1)..V(schemaVersion)；
--    全新安装不执行（初始化已含最新结构）。
-- 7) MySQL 建议库字符集 utf8mb4:
--    CREATE DATABASE IF NOT EXISTS minecraft DEFAULT CHARACTER SET utf8mb4;
--    执行时请使用: mysql --default-character-set=utf8mb4 -u<用户> -p < 库名 < init.sql
-- ============================================================================

-- ---------- 网页动作主表 ----------
-- WebAction: 对应 actions.yml 中 actions.<id>；display_* 为展示配置拆列；
--            effects 存子表 homepage_action_effect（action_id 关联）。
CREATE TABLE IF NOT EXISTS `homepage_action` (
  `id`                VARCHAR(64)  PRIMARY KEY COMMENT '动作 ID（≤32，前端按钮与代码调用引用）',
  `name`              VARCHAR(255)          COMMENT '动作显示名（前端按钮/卡片标题）',
  `price`             DOUBLE                COMMENT '价格（服务端扣款额；0 = 免费）',
  `offline`           VARCHAR(255)          COMMENT '离线策略: run / queue（默认）/ reject',
  `cooldown_seconds`  INT                   COMMENT '冷却秒数（同一玩家两次执行最小间隔；0 = 不限）',
  `balance_check`     TINYINT               COMMENT '扣款前是否预检余额（需要 Vault）',
  `claim_period`      VARCHAR(255)          COMMENT '领取周期去重: once / daily / weekly（空 = 不限）',
  `claim_window_start` VARCHAR(255)         COMMENT '领取时间窗口开始（yyyy-MM-dd HH:mm:ss；空 = 不限制）',
  `claim_window_end`  VARCHAR(255)          COMMENT '领取时间窗口结束（yyyy-MM-dd HH:mm:ss；空 = 不限制）',
  `display_icon`      VARCHAR(255)          COMMENT '展示图标（display.icon 拆列）',
  `display_desc`      VARCHAR(255)          COMMENT '展示描述（display.desc 拆列）',
  `display_group`     VARCHAR(255)          COMMENT '展示分组（display.group 拆列）',
  `display_sort`      INT                   COMMENT '展示排序（display.sort 拆列）',
  `display_hidden`    TINYINT               COMMENT '是否隐藏（display.hidden 拆列；true 仅代码调用）'
);

-- ---------- 网页动作效果子表 ----------
-- WebActionEffect: action_id 关联主表；sort 为动作内执行顺序（0 起）。
-- 主键为自增数字（@TableId(type = IdType.AUTO)）：SQL 端 AUTO_INCREMENT、YAML 端自动分配。
CREATE TABLE IF NOT EXISTS `homepage_action_effect` (
  `id`        BIGINT       AUTO_INCREMENT COMMENT '效果记录主键（自增数字 1、2、3…）',
  `action_id` VARCHAR(255)          COMMENT '所属动作 ID（关联 homepage_action.id）',
  `sort`      INT                   COMMENT '效果在动作内的顺序（0 起）',
  `type`      VARCHAR(255)          COMMENT '效果类型: command / economy.take / economy.give',
  `command`   VARCHAR(255)          COMMENT '指令模板（{player}/{amount} 占位符）',
  `target`    VARCHAR(255)          COMMENT '强制命令类别: console / account / entity（可空 = 自动识别）',
  `amount`    DOUBLE                COMMENT '金额（economy.give 必填；economy.take 缺省取动作 price）',
  PRIMARY KEY (`id`),
  KEY `idx_effect_action` (`action_id`)
);

-- ---------- 领取记录表（运行期数据） ----------
-- ActionClaimRecord: player_name + action_id 为去重查询键；周期内已领则拒绝。
-- 主键为自增数字（@TableId(type = IdType.AUTO)）：SQL 端 AUTO_INCREMENT、YAML 端自动分配。
CREATE TABLE IF NOT EXISTS `homepage_action_claim` (
  `id`          BIGINT       AUTO_INCREMENT COMMENT '领取记录主键（自增数字 1、2、3…）',
  `player_name` VARCHAR(255)          COMMENT '领取主体（玩家名，去重查询键之一）',
  `action_id`   VARCHAR(255)          COMMENT '动作 ID（去重查询键之一）',
  `claimed_at`  VARCHAR(255)          COMMENT '领取时刻（epoch 毫秒字符串）',
  `period`      VARCHAR(255)          COMMENT '领取时的周期模式: once / daily / weekly',
  PRIMARY KEY (`id`),
  KEY `idx_claim_player_action` (`player_name`, `action_id`)
);

-- ---------- 离线待执行任务表（运行期数据） ----------
-- OfflineTask: 玩家离线且动作 offline=queue 时入队，上线后按命令快照补执行。
-- 主键为自增数字（@TableId(type = IdType.AUTO)），支持一人多条互不覆盖。
CREATE TABLE IF NOT EXISTS `homepage_action_pending` (
  `id`          BIGINT       AUTO_INCREMENT COMMENT '任务主键（自增数字，支持一人多条）',
  `player_name` VARCHAR(255)          COMMENT '任务归属玩家名（补发匹配键）',
  `player_uuid` VARCHAR(255)          COMMENT '玩家 UUID（审计用途，不作匹配键）',
  `action_id`   VARCHAR(255)          COMMENT '触发任务的动作 ID',
  `command`     VARCHAR(255)          COMMENT '待执行命令快照（已填充 {player}/{amount}）',
  `queued_at`   VARCHAR(255)          COMMENT '入队时刻（epoch 毫秒字符串）',
  `done_at`     VARCHAR(255)          COMMENT '补发完成/失败时刻（epoch 毫秒字符串）',
  `status`      VARCHAR(255)          COMMENT '状态: pending（待补发）/ granted（已补发）/ failed（补发失败）',
  PRIMARY KEY (`id`),
  KEY `idx_pending_player` (`player_name`),
  KEY `idx_pending_status` (`status`)
);
