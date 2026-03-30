# Clan

Minecraft clan plugin.

## Requirements

- Java 17 (JDK)
- Maven, or use the Maven Wrapper included in this repo

## Build

This repository uses the bundled `lib/bukkit-stubs.jar` along with a small local Maven
repository in `lib/m2` for the Bukkit/Paper/PlaceholderAPI compile-time APIs, so the
project can be built without downloading those APIs from external Maven repositories.

If `mvn` is not recognized on your system, use the Maven Wrapper instead:

### Windows (PowerShell)

```powershell
.\mvnw.cmd clean package
```

### macOS/Linux

```bash
./mvnw clean package
```

### With Maven installed

```bash
mvn clean package
```
