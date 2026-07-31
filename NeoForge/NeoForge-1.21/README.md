# JustEnoughAccidents - NeoForge 1.21

Creates MineBackup accident-site snapshots when a singleplayer or LAN world enters a dangerous state.

## Version Information

- **Minecraft**: 1.21
- **NeoForge**: 21.0.167
- **Java**: 21
- **MineBackup**: 3.2.0+
- **Parchment**: 2024.11.10

## Build

```bash
./gradlew build
```

## Adaptation Notes

This version was adapted from Fabric-26.1 to NeoForge-1.21 with the following changes:

### API Replacements

1. **Mod Initialization**
   - Fabric: `ModInitializer` interface
   - NeoForge: `@Mod` annotation with `IEventBus` constructor parameter

2. **Lifecycle Events**
   - Fabric: `ServerLifecycleEvents.SERVER_STARTING/STOPPING/STOPPED`
   - NeoForge: `ServerStartingEvent`, `ServerStoppingEvent`, `ServerStoppedEvent`

3. **Tick Events**
   - Fabric: `ServerTickEvents.END_SERVER_TICK`
   - NeoForge: `ServerTickEvent.Post`

4. **Event Bus**
   - Fabric: Static event registration via Fabric API
   - NeoForge: `NeoForge.EVENT_BUS` (game bus) vs mod bus separation

5. **Config Directory**
   - Fabric: `FabricLoader.getInstance().getConfigDir()`
   - NeoForge: `FMLPaths.CONFIGDIR.get()`

6. **Metadata**
   - Fabric: `fabric.mod.json`
   - NeoForge: `neoforge.mods.toml` in templates directory

7. **Java Version**
   - Fabric-26.1: Java 25
   - NeoForge-1.21: Java 21 (downgrade)

8. **Mappings**
   - Fabric-26.1: Mojang mappings
   - NeoForge-1.21: Parchment mappings (enhanced Mojang)

### Files Modified

- `JustEnoughAccidents.java`: Changed from `ModInitializer` to `@Mod` with `IEventBus` constructor
- `JeaRuntime.java`: Replaced Fabric lifecycle/tick events with NeoForge events
- `JeaConfigManager.java`: Changed `FabricLoader` to `FMLPaths`
- `just_enough_accidents.mixins.json`: Changed Java compatibility level from 25 to 21, added refmap

### Files Unchanged

All other Java files remain compatible:
- Incident detection and coordination logic
- Player danger scanner
- Scoreboard trigger
- Backup strategy
- Notification system
- Configuration data model
- Mixin implementation

## License

MIT
