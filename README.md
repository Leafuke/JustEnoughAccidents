# Just Enough Accidents（险兆备份）

JEA 是 MineBackup 的事故检测扩展。Fabric 26.2 会在单人、LAN 或 Dedicated Server 世界中发现若干高风险状态，并请求 MineBackup 与 FolderRewind 创建事故现场快照和安全锚点。

完整的检测器、计分板触发、配置和归档边界见 [FolderRewind Minecraft 文档中的 JEA 页面](https://folderrewind.top/docs/guides/minecraft/just-enough-accidents)。

## 要求

- Fabric 26.2 / JEA 0.3.0：MineBackup 3.3.0+
- 其他当前目标：MineBackup 3.1.0+
- FolderRewind 1.8.0+
- MineRewind 1.8.0+
- Fabric 26.1～26.1.2：Fabric Loader 0.18.4+、Fabric API、Java 25
- Fabric 26.2：Fabric Loader 0.19.3+、Fabric API、Java 25
- NeoForge 1.21～1.21.8：NeoForge 21.0.167+、Java 21
- Forge 1.20～1.20.4：Forge 46+、Java 17

## 功能范围

| 平台 | 安全锚点 | Dedicated Server |
| --- | --- | --- |
| Fabric 26.2 / JEA 0.3.0 | 支持 | 支持 |
| Fabric 26.1、NeoForge 1.21、Forge 1.20 | 保持 0.2.x 功能集 | 尚未移植 |

Fabric 26.2 的 `[JEA SAFE]` 是世界级安全锚点：至少一名 eligible 玩家在线、所有 eligible 玩家在 JEA 已知风险范围内持续稳定后，JEA 请求 MineBackup 创建的备份。它不是世界绝对安全的保证。

默认在连续安全 30 秒后创建或刷新锚点；上次已满足刷新需求满 30 分钟后，下一次满足 quiet 条件时才会再次请求。任意新的事故信号、已知危险状态或没有 eligible 玩家都会重置 quiet 计时。安全锚点与事故现场快照都共用 MineBackup 的 `KeepCount`，JEA 不管理独立保留配额、固定或保护槽位。

事故现场快照创建成功后，JEA 只会选择事故首次检测时间之前的 `[JEA SAFE]` 作为安全恢复目标；如果没有匹配锚点或查询失败，事故现场恢复目标仍然可用。

Dedicated Server 会检测所有 eligible 在线玩家并创建世界级快照。可用时，恢复按钮只会将 `/mb restore "<backup>"` 填入聊天输入框，仍需由用户发送并由 MineBackup 执行权限检查；Dedicated Restore 不可用时，JEA 继续检测和创建备份，但不显示恢复操作。

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
  "safeAnchor": {
    "enabled": true,
    "refreshMinutes": 30,
    "quietSeconds": 30
  },
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
- 集成服务器的世界所有者会收到可点击的还原文本，它调用 MineBackup 现有的 `/mb restore` 确认流程；Dedicated Server 只建议命令，不会自动执行。
- JEA 快照与普通 FolderRewind 备份共用同一个 `KeepCount`。频繁触发可能使较旧归档被正常清理；JEA 不提供独立配额、固定或保护槽位。

## 当前不包含

通用 severity、per-detector cooldown、自动还原、独立 retention quota、metadata API、其他 loader 的 0.3.0 移植，以及新的事故 detector 都不在本版本范围内。
