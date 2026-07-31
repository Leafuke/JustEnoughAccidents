# Just Enough Accidents（险兆备份）

JEA 是 MineBackup 的事故检测扩展。它会在单人世界或 LAN 世界中发现若干高风险状态，并请求 MineBackup 与 FolderRewind 创建一次事故现场快照。

## 要求

- Minecraft 26.1～26.1.2
- Fabric Loader 0.18.4+
- Fabric API
- MineBackup 3.1.0+
- FolderRewind 1.8.0+
- MineRewind 1.8.0+
- Java 25

JEA 0.1.0 不支持专用服务器。专用服务器加载时只会记录一次禁用日志，不扫描玩家或发起备份。

## 首发检测器

- 预测致命摔落
- 氧气即将耗尽
- 进入岩浆且没有抗火效果
- 滑翔中的鞘翅剩余耐久过低
- 有效生命值（生命值加伤害吸收）过低
- 不死图腾成功触发
- 玩家附近有正在膨胀的苦力怕
- 附近 TNT 即将爆炸（不包括水下 TNT）
- 玩家的宠物生命值过低
- 数据包或命令方块发出的计分板请求

这些快照记录的是触发时的事故现场，不保证是事故发生前的绝对安全点。尤其是低生命和不死图腾检测，归档中可能已经包含伤害或图腾消耗。

## 配置

首次进入世界时生成 `config/just-enough-accidents.json`。配置只在服务器会话启动时读取；修改后需要退出并重新进入世界。

默认备份参数为：

```json
{
  "schemaVersion": 1,
  "enabled": true,
  "cooldownSeconds": 60,
  "backup": {
    "mode": "incremental",
    "compressionMethod": "zstd",
    "compressionLevel": 6
  },
  "detectors": {
    "fatalFall": { "enabled": true },
    "lowAir": {
      "enabled": true,
      "triggerAir": 60,
      "rearmAir": 200
    },
    "lava": { "enabled": true },
    "elytra": {
      "enabled": true,
      "remainingDurability": 10
    },
    "lowHealth": {
      "enabled": true,
      "effectiveHealth": 2.0
    },
    "totem": { "enabled": true },
    "creeper": {
      "enabled": true,
      "normalRadius": 6.0,
      "chargedRadius": 12.0
    },
    "tnt": {
      "enabled": true,
      "radius": 12.0,
      "maxFuseTicks": 40,
      "excludeUnderwater": true
    },
    "petDanger": {
      "enabled": true,
      "radius": 32.0,
      "healthThreshold": 0.25
    }
  },
  "scoreboard": { "enabled": true }
}
```

如果已有配置包含无效 JSON、错误枚举或越界数值，JEA 会保留原文件并禁用当前会话，不会静默改写配置。

## 计分板触发

JEA 不自动创建计分板目标。数据包或管理员可执行：

```mcfunction
scoreboard objectives add jea_request dummy
scoreboard players add #global jea_request 1
```

JEA 将所有大于等于 1 的值合并为一次请求，并在提交请求前把 `#global` 分数设为 0。即使请求因冷却或后端忙碌而未执行，分数也不会恢复或排队。

## 冷却、还原与备份保留

- 同一 Tick 内的多个险兆合并为一份世界快照。
- MineBackup 接受请求后进入默认 60 秒全局冷却；立即拒绝不会占用冷却。
- 正在执行的请求、冷却期间的请求和后端拒绝均不会排队重试。
- 归档创建成功后，世界所有者会收到可点击的还原文本，它调用 MineBackup 现有的 `/mb restore` 确认流程。
- JEA 快照与普通 FolderRewind 备份共用同一个 `KeepCount`。频繁触发可能使较旧归档被正常清理；0.1.0 不提供独立配额、固定或保护槽位。

## 当前不包含

定时安全检查点、固定最近安全点、致命伤害事件、普通燃烧、TNT/末地水晶/床/重生锚、宠物死亡、自动还原和专用服务器支持均不在 0.1.0 范围内。
