> [!WARNING]
> ### ⚠️ Critical: Server Shutdown & Linear / b_linear Region Formats
>
> If you are using any Linear region format (**Linear v1, v2, v3** or **b_linear**), **NEVER force-kill (`kill -9`, SIGKILL, panel crash/stop buttons) the server process!**
>
> Always shut down or restart cleanly using `/stop` or `/restart`.
>
> **Known risks and limitations with Linear formats:**
> - **Catastrophic Chunk / Region Corruption:** Linear formats buffer and compress chunks into shared Zstandard/LZ4 data blocks rather than separate Anvil `.mca` sector slots. Abruptly terminating the process while a flush or write-ahead-log (WAL) sync is in progress can corrupt entire region files (up to 1,024 chunks at once).
> - **Data Loss on Hard Crashes:** If the host machine loses power, suffers an OOM-killer termination, or is abruptly stopped without executing the shutdown hooks, unwritten in-memory chunk buffers cannot be recovered.
> - **Incompatibility & Tooling Issues:** Linear v3 and non-standard specifications are not supported by external tools (such as map renderers like Dynmap/BlueMap, world converters, or NBT editors). Always keep reliable backups before migrating.

<div align="center">

# Wind

*Wind is a Lophine fork focused on plugin compatibility, performance, and configurable vanilla features.*

![Created At](https://img.shields.io/github/created-at/Flugobjekt/Wind?style=for-the-badge&color=blue)
[![License](https://img.shields.io/github/license/Flugobjekt/Wind?style=for-the-badge&color=green)](LICENSE.md)
[![Issues](https://img.shields.io/github/issues/Flugobjekt/Wind?style=for-the-badge&color=orange)](https://github.com/Flugobjekt/Wind/issues)

![Commit Activity](https://img.shields.io/github/commit-activity/w/Flugobjekt/Wind?style=for-the-badge&color=purple)
![CodeFactor Grade](https://img.shields.io/codefactor/grade/github/Flugobjekt/Wind?style=for-the-badge&color=yellow)
![GitHub all releases](https://img.shields.io/github/downloads/Flugobjekt/Wind/total?style=for-the-badge&color=red)

![Repo contributors](https://img.shields.io/github/contributors/Flugobjekt/Wind?style=for-the-badge&color=brightgreen)

</div>

---

## ✨ Core Features

- 🔧 **Configurable Vanilla Features** - Flexibly adjust game mechanics to suit different server needs
- 📊 **Tpsbar Support** - Real-time TPS status display
- 🐛 **Folia Bug Fixes** - Targeted fixes for known Folia issues
- 💾 **Multiple World Format Support** - Support for linear and b_linear (linear reimplementation) world formats
- 🔬 **Redstone Enhancement** - More redstone functionality on Folia (use Fabric for complete redstone features)
- 🛠️ **More Useful Functions** - Continuously adding useful server features

### Additional Launch Parameters

 - morninggloryclip.useMojangSource - Use Mojang's source for Minecraft Server
 - morninggloryclip.enable.mixin - Enable mixin support for Leaves Plugin

## 📥 Download

### Stable Releases
All release versions can be found on the [Releases](https://github.com/Flugobjekt/Wind/releases) page.

### Development Builds
If you want to experience the latest features, you can build it yourself following the steps below.

### Build Steps

```bash
# Clone the project
git clone https://github.com/Flugobjekt/Wind.git
cd Wind

# Apply patches and build Paperclip JAR
./gradlew applyAllPatches && ./gradlew createPaperclipJar
```

After building, you can find the generated JAR file in the `wind-server/build/libs` directory.

## 🔌 API Usage

### Gradle Configuration

In this project, we don't plan to add extra APIs, it's just to fix the existing paper/spigot/bukkit API, you can use our upstream API

```kotlin

repositories {
    maven {
        url = "https://repo.bacteriawa.com/repository/maven-public/"
    }
}

dependencies {
    compileOnly("fun.bm.lophine:lophine-api:26.2.build.+")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
```

### Maven Configuration

```xml
<repositories>
    <repository>
        <id>repository</id>
        <url>https://repo.bacteriawa.com/repository/maven-public/</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>fun.bm.lophine</groupId>
        <artifactId>lophine-api</artifactId>
        <version>[26.2.build,)</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

## 💬 Community & Support

> If you're interested in this project or have any questions, feel free to ask us.

### Join Our Community

- **QQ Group**: [1020403749](https://qm.qq.com/cgi-bin/qm/qr?k=y_MA9UaN7PM9e9J1LIs9Eea3LK8C0h6J&jump_from=webapi&authKey=ap5f8MlbeezXYtnmpnT5ZOFljDuOyV6OAb2PIcViQ+Ilr60Ycq63FDDTsJOZDYtj)
- **Discord**: [Join Here](https://discord.gg/UXSgPZczcy)

### Get Help

- 📋 [Submit Issues](https://github.com/Flugobjekt/Wind/issues)
- 💬 [GitHub Discussions](https://github.com/Flugobjekt/Wind/discussions)
- 📖 [Project Documentation](./docs/)

## 🐛 Bug Reports

When you encounter any issues, please ask us and we'll do our best to resolve them. Please remember to:

- 📝 **Describe the problem clearly** - Provide detailed information about the specific issue
- 📋 **Provide complete logs** - Include error logs and relevant configuration information
- 🔍 **Environment details** - Specify server version, plugin list, and other environment details
- 🔄 **Reproduction steps** - If possible, provide specific steps to reproduce the issue

## 🤝 Contributing

We welcome community contributions! For detailed contribution guidelines, please see:

- 📖 [Contributing Guide](./docs/CONTRIBUTING.md)

## 📊 Project Statistics

### BStats Data

![bStats](https://bstats.org/signatures/server-implementation/Wind.svg "bStats")

## ⭐ Give Us a Star!

> Every free ⭐Star you give is the motivation for our every step forward.
