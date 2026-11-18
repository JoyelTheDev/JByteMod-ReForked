<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/logo/logo-horizontal-dark.svg">
    <img alt="JByteMod ReForked" src="docs/logo/logo-horizontal-light.svg" width="480">
  </picture>
</h1>

[![Build](https://github.com/constnulled/JByteMod-ReForked/actions/workflows/release.yml/badge.svg)](https://github.com/constnulled/JByteMod-ReForked/releases/)
[![Version](https://img.shields.io/badge/version-2.9.4-blue)](https://github.com/constnulled/JByteMod-ReForked/releases)
[![Java](https://img.shields.io/badge/Java-25%2B-orange)](https://openjdk.org/projects/jdk/25/)
[![License](https://img.shields.io/badge/license-GPL--2.0-green)](LICENSE)

A feature-rich Java bytecode editor and analyzer with a graphical interface. Load, inspect, edit, and save `.jar`, `.class`, and `.apk` files — all without writing a decompiler or touching raw hex.

---

## Features

### Core Editing
- Edit bytecode instructions, LDC constants, annotations, local variables, and try-catch blocks
- Undo/redo support per method
- Drag-and-drop file loading

### Decompilers
Switch between six decompilers at any time:

| Decompiler   | Version |
|--------------|---------|
| CFR          | 0.152   |
| Procyon      | 0.6.0   |
| Vineflower   | 1.12.0  |
| JD-Core      | 1.1.3   |
| ASMifier     | –       |
| Koffee       | 1.0.2   |

### Analysis & Visualization
- **Control Flow Graph** — visualize branching and jump structure for any method
- **Cross-reference viewer** — find all usages of a class, field, or method across the loaded JAR
- **Class hierarchy viewer** — explore inheritance trees and interface implementations
- **Bytecode metrics panel** — per-method stats including instruction count, cyclomatic complexity, branch count, local variable count, max stack/locals, and an obfuscation score

### Deobfuscation
Automated cleanup passes available under the Tools menu:

- Fix invalid signatures
- Remove line number and local variable debug info
- Remove synthetic and bridge flags
- Remove illegal varargs
- Fold constant expressions
- Rearrange unconditional `goto` chains
- Merge redundant trap handlers
- Remove unreachable switch branches
- Strip illegal invisible annotations

Obfuscation pattern detection for **Allatori**, **Stringer**, and **ZKM**.

### Live JVM Debugging
- Attach to a running JVM process via the **Attach API** or **JDWP**
- Browse loaded classes in the attached process
- Retransform classes and push changes without restarting the target

### Bytecode Diff 
- Compare two versions of a method's bytecode side-by-side
- Build and export instruction-level patch files
- Apply patches to a loaded JAR

### Script Console
Run automation scripts directly against the loaded classes using:
- **Groovy** (full scripting with closures and GDK)
- **BeanShell** (lightweight Java-like scripting)

### Search
- Search for string constants (LDC) across all classes
- Search for field and method references

### Plugin System
Extend the editor by dropping a `.jar` into the `plugins/` folder. Plugins receive lifecycle hooks for file load and menu clicks.

### UI & Accessibility
- Four built-in themes: **Dark**, **Light**, **Monokai**, **Solarized Dark**
- Modern look and feel powered by FlatLaf: rounded controls, slim scrollbars, animated light/dark switching
- Custom theme editor
- Localization in **8 languages**: English, German, Spanish, Portuguese (BR), Russian, Simplified Chinese, Traditional Chinese, Chinese
- Interactive first-launch tutorial
- Discord Rich Presence
- Auto-update check on startup

---

## Installation

Download the newest build from the [Latest Build release](../../releases/tag/latest):

| Platform | Download |
|----------|----------|
| Windows | `.exe` installer or portable `.zip` |
| macOS | `.dmg` |
| Linux | `.deb` package or portable `.tar.gz` |
| Any OS | `.jar` (requires JDK 25) |

The installers and portable archives bundle their own Java runtime, so nothing else needs to be installed. They are not code-signed: Windows SmartScreen may show a warning (choose **More info → Run anyway**) and on macOS you may need to right-click the app and choose **Open** the first time. Checksums for every release are published in the `SHA256SUMS-*.txt` files.

---

## Requirements

- **JDK 25** or higher (for building, and for running the plain `.jar`; the installers do not need it)
- Maven 3.9+ (for building from source only)

---

## Usage

```sh
java -jar JByteMod-ReForked-<version>.jar
```

| Flag | Long form | Description |
|------|-----------|-------------|
| `-f <path>` | `--file` | Open a file on startup |
| `-d <path>` | `--dir` | Set the working directory |
| `-c <name>` | `--config` | Specify the config file name |
| `-?` | `--help` | Print help and exit |

Files can also be loaded via **File → Open** or by dragging them onto the window. Supported formats: `.jar`, `.class`, `.apk` (APK is read-only).

---

## Building from Source

```sh
git clone https://github.com/xbrowniecodez/JByteMod-ReForked.git
cd JByteMod-ReForked
mvn clean package
```

The shaded JAR is written to `target/`. The build requires network access to resolve snapshot dependencies from the OW2 and Sonatype repositories.

### Building installers

After `mvn clean package`, build a native installer for the operating system you are on:

```sh
bash packaging/build-installer.sh
```

Results are written to `target/installers/`. This needs a JDK 25 with `jpackage` on the `PATH`, plus the usual platform tooling: [Inno Setup 6](https://jrsoftware.org/isdl.php) on Windows (`.exe` installer and portable `.zip`), `fakeroot` on Linux (`.deb` and portable `.tar.gz`), and Xcode command line tools on macOS (`.dmg`). On Windows run the script from Git Bash.

Set `DRY_RUN=1` to print the `jpackage` commands without running them.

---

## Releasing

Releases are fully automatic and there is only ever one: **JByteMod ReForked Latest Build** (tag `latest`). Every push to `master` runs the **release** workflow, which:

1. Builds the jar with Maven, removes the previous latest release, and publishes a fresh one with release notes and the newest commits.
2. Builds the Windows, Linux and macOS installers in parallel and attaches each one to that release as soon as it is ready, together with a `SHA256SUMS-*.txt` checksum file per platform.

Nothing is uploaded as a workflow artifact; every file goes straight onto the release with `softprops/action-gh-release`. Because the release is replaced on every push, the Releases page always shows the newest files at the top and no per-build tags pile up.

To change the version number shown in the app and in the installer file names, run `bash packaging/bump-version.sh 2.9.5` and commit the result; the next push picks it up. The workflow can also be started by hand from the **Actions** tab. Pull requests and other branches run the **ci** workflow, which builds the project and smoke-tests packaging without publishing anything.

---

## Plugin API

Create a class that extends `de.xbrowniecodez.jbytemod.plugin.Plugin` and package it as a JAR:

```java
import de.xbrowniecodez.jbytemod.plugin.Plugin;
import org.objectweb.asm.tree.ClassNode;
import java.util.Map;

public class MyPlugin extends Plugin {

    public MyPlugin() {
        super("My Plugin", "1.0.0", "YourName");
    }

    @Override
    public void init() {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void loadFile(Map<String, ClassNode> classes) {
    }

    @Override
    public boolean isClickable() {
        return true;
    }

    @Override
    public void menuClick() {
    }
}
```

Convenience methods available inside a plugin:

| Method | Returns |
|--------|---------|
| `getCurrentFile()` | `Map<String, ClassNode>` — all loaded classes |
| `getSelectedNode()` | `ClassNode` — currently selected class |
| `getSelectedMethod()` | `MethodNode` — currently selected method |
| `updateTree()` | Refreshes the class tree after modifications |
| `getMenu()` | `JMenuBar` — the main menu bar |
| `getTree()` | `JTree` — the class tree component |

Place the compiled plugin JAR in the `plugins/` directory next to the main JAR. It will be loaded automatically at startup.

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes with a descriptive message
4. Push and open a Pull Request against `master`

Please keep changes focused; unrelated fixes should be separate PRs.

---

## Credits

JByteMod ReForked is built on top of and incorporates work from:

- [grax32/JByteMod](https://github.com/grax32/JByteMod-Beta) — original JByteMod by grax
- [java-deobfuscator](https://github.com/java-deobfuscator/deobfuscator) — deobfuscation analysis utilities
- [ItzSomebody/Radon](https://github.com/ItzSomebody/Radon) — obfuscation pattern detection
- [ObjectWeb ASM](https://asm.ow2.io/) — bytecode manipulation framework (custom patched version bundled)
- [Vineflower](https://github.com/Vineflower/vineflower), [CFR](https://github.com/leibnitz27/cfr), [Procyon](https://github.com/mstrobel/procyon), [JD-Core](https://github.com/java-decompiler/jd-core), [Koffee](https://github.com/char/koffee) — decompiler backends

---

## License

JByteMod ReForked is distributed under the **GNU General Public License v2.0 (GPL-2.0)**.
See the [LICENSE](LICENSE) file for the full text.
