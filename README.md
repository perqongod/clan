# Clan

Minecraft clan plugin.

## Requirements

- Java 17 (JDK)
- Maven, or use the Maven Wrapper included in this repo

## Build

This repository uses the bundled `lib/bukkit-stubs.jar` for the Bukkit/Paper/PlaceholderAPI
compile-time APIs so the project can be built without external Maven repositories.

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
