# JByteMod Remastered

[![Build Status](https://ci.mdma.dev/api/badges/apkreader/JByteMod-Remastered/status.svg)](https://ci.mdma.dev/apkreader/JByteMod-Remastered)
![GitHub Release](https://img.shields.io/github/v/release/apkreader/JByteMod-Remastered)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/681e07293b4c491fae53c3be6d8469fe)](https://app.codacy.com/gh/apkreader/JByteMod-Remastered/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
![GitHub Issues or Pull Requests](https://img.shields.io/github/issues/apkreader/JByteMod-Remastered)
![GitHub Issues or Pull Requests](https://img.shields.io/github/issues-pr/apkreader/JByteMod-Remastered)

**JByteMod Remastered** is an enhanced Java bytecode editor and analyzer built on top of the original JByteMod. It provides an intuitive GUI for loading, inspecting, editing, and saving `.jar`, `.class`, and `.apk` files, with deep integration for multiple decompilers, a control flow visualizer, obfuscation detection, deobfuscation utilities, a live-attach mode, and a plugin system for extensibility.

Current version: **2.9.1** — requires JDK 21.

---

## Features

- **Advanced Bytecode Editing** — view and directly modify Java bytecode instructions in an interactive list. Edit opcodes, operands, LDC constants, annotations, local variable tables, and try-catch blocks with dedicated editors.
- **Multiple Decompiler Backends** — seamlessly switch between six decompilers: CFR, Procyon, Vineflower (Fernflower), JD-Core, ASMifier, and Koffee.
- **Control Flow Visualization** — generate graphical control flow diagrams (CFGs) of any method. Save diagrams as images.
- **Obfuscation Detection** — analyze loaded classes for name obfuscation (non-ASCII names, keyword abuse, reserved filenames) and method-level obfuscation patterns (try-catch block obfuscation, POP2 abuse, invokedynamic tricks, string obfuscation). Detects signatures from Allatori, Stringer, and ZKM.
- **Deobfuscation Utilities** — run automated cleanup passes including constant folding, signature fixing, line number / local variable stripping, synthetic bridge removal, illegal varargs fixing, goto rearrangement, trap handler merging, unconditional switch removal, and illegal annotation removal.
- **Android APK Support** — load and decompile `.apk` files (via dex-translator). APK classes are converted to ASM `ClassNode`s for inspection with the same interface as JARs. (Editing and saving APKs is not yet supported.)
- **Live Attach & Retransform** — attach to a running JVM process using the Java Attach API and retransform loaded classes in real time, without restarting the target application.
- **Search & Replace** — search across the entire loaded JAR for LDC string constants, field/method references, and string patterns.
- **Constant Pool Editor** — browse and modify constant pool entries.
- **Plugin System** — extend the tool with custom plugins. Drop a `.jar` into the `plugins/` folder; it is loaded at startup with full access to the class tree, selected node, and menu bar.
- **Drag and Drop** — drag `.jar`, `.apk`, or `.class` files directly onto the window to open them.
- **Cross-Platform** — runs on Windows, macOS, and Linux.
- **Localization** — UI strings available in English, German, Spanish, Portuguese (Brazil), Russian, Simplified Chinese, Traditional Chinese, and Chinese.
- **Discord Rich Presence** — optionally shows what file you are editing in your Discord status.
- **Auto-Update Check** — checks the GitHub releases API on startup and notifies you of new versions.
- **Dark / Light Theme** — powered by DarkLaf (FlatLaf) with a custom RSyntaxTextArea theme.

---

## Requirements

- **JDK 21 or higher** (for the full feature set including APK support and live attach).
- A Java 8 compatible build is also available but does not support APK loading or some newer features.

---

## Installation & Usage

### Download

Download the latest release JAR from the [releases page](https://github.com/apkreader/JByteMod-Remastered/releases).

### Launch

```sh
java -jar JByteMod-Remastered.jar
```

#### Command-line options

| Flag | Long form | Description |
|------|-----------|-------------|
| `-f <path>` | `--file` | Open a `.jar` or `.class` file on startup |
| `-d <path>` | `--dir` | Set the working directory |
| `-c <name>` | `--config` | Specify the config file name |
| `-?` | `--help` | Print help and exit |

Example:

```sh
java -jar JByteMod-Remastered.jar -f MyApp.jar
```

### Opening files

Use `File > Open`, or drag and drop a `.jar`, `.apk`, or `.class` file onto the window.

---

## Interface overview

### Class tree (left panel)

Lists all packages, classes, and members from the loaded file. Click a method or field to load it in the editor. Right-click for context actions (copy name, add/remove members, etc.).

### Bytecode editor (center panel)

Displays the instruction list (`MyCodeList`) for the selected method. Double-click an instruction to open the `InsnEditDialogue` for editing. Supports all ASM instruction types including `LdcInsnNode`, `MethodInsnNode`, `FieldInsnNode`, `JumpInsnNode`, `TableSwitchInsnNode`, `LookupSwitchInsnNode`, and more.

Additional sub-panels:

- **LVP (Local Variable Parameters)** — view and edit the local variable table.
- **TCB (Try-Catch Blocks)** — view and manage exception handler ranges.
- **Address list** — shows bytecode offsets.

### Decompiler tab

Switch to the `Decompiler` tab to view decompiled Java source for the selected class. Change the active decompiler from the menu or settings. The editor uses RSyntaxTextArea with Java syntax highlighting.

### Control flow panel

In the `Analysis` menu, select a method to generate its control flow graph. The graph renders with jgraphx in a hierarchical layout. Use `Save` to export the diagram as an image.

### Info panel

Shows metadata about the selected class: version, access flags, superclass, interfaces, field count, and method count.

### Search

Use the toolbar search button or `Edit > Search` to search the loaded JAR for LDC strings, field/method references, or other patterns. Results appear in the search list.

---

## Decompiler backends

| Backend | Library | Notes |
|---------|---------|-------|
| CFR | cfr 0.152 | Excellent general-purpose decompiler |
| Procyon | procyon 0.6.0 | Handles some edge cases CFR misses |
| Vineflower | vineflower 1.12.0 | Fork of Fernflower (IntelliJ decompiler); good lambda support |
| JD-Core | jd-core 1.1.3 | Fast, lightweight |
| ASMifier | ASM util | Generates raw ASM API code instead of Java source |
| Koffee | koffee 1.0.2 | Kotlin DSL output |

---

## Obfuscation detection

The `ObfuscationAnalyzer` scans every class and method in the loaded JAR and reports:

**Name obfuscation types** (class, method, field names):

- Non-ASCII / non-printable characters
- Java reserved keywords used as identifiers
- Windows reserved filenames (`con`, `nul`, `aux`, `prn`)
- Single-character or very short names

**Method obfuscation types**:

- Try-catch block obfuscation (TCBO)
- POP2 abuse
- InvokeDynamic tricks
- String obfuscation (encrypted LDC constants)

Recognized obfuscator signatures include **Allatori**, **Stringer**, **ZKM5**, and **ZKM8**.

---

## Deobfuscation utilities

`DeobfusacteUtils` provides a set of automated transformation passes you can apply to a loaded JAR from the menu:

| Utility | Description |
|---------|-------------|
| `fixSignature` | Removes invalid generic signatures that cause decompiler failures |
| `removeLineNumber` | Strips `LineNumberNode` entries |
| `removeLocalVariable` | Strips `LocalVariableNode` tables |
| `removeSyntheticBridge` | Removes synthetic bridge methods |
| `removeIllegalVarargs` | Fixes incorrectly flagged varargs methods |
| `foldConstant` | Evaluates and inlines simple constant expressions |
| `rearrangeGoto` | Simplifies unconditional jump chains |
| `mergeTrapHandler` | Merges redundant exception handler ranges |
| `removeUnconditionalSwitch` | Simplifies switch statements with only one reachable case |
| `removeIllegalInvisibleAnnotations` | Strips malformed invisible annotations |

These are sourced from and compatible with the [java-deobfuscator](https://github.com/java-deobfuscator) and [Radon](https://github.com/ItzSomebody/Radon) projects.

---

## Android / APK support

JByteMod Remastered can open `.apk` files for inspection and decompilation. The APK pipeline:

1. The DEX bytecode in the APK is read using `dex-reader`.
2. `dex-translator` converts DEX classes to JVM bytecode.
3. `Dex2ASMVisitorFactory` bridges the translated bytecode into ASM `ClassNode` objects.
4. All standard editing panels (bytecode list, decompiler, CFG) work on the converted nodes.

> Saving/exporting modified APKs is not yet supported.

---

## Live attach & retransform

JByteMod Remastered can attach to a running JVM and retransform loaded classes without restarting the target:

1. Go to `File > Attach to JVM` (or the equivalent menu entry).
2. `AttachTask` uses `com.sun.tools.attach.VirtualMachine` to list running JVM processes.
3. Select a process; the tool injects an agent JAR and acquires an `Instrumentation` handle.
4. After editing a class in the bytecode editor, use `File > Retransform` to push the modified bytes into the running JVM via `RetransformTask`.

> Requires a JDK (not just a JRE) and that the target JVM was started with attach enabled.

---

## Plugin system

Plugins are `.jar` files placed in the `plugins/` subdirectory of the working directory. At startup, `PluginManager` scans the folder, loads each JAR into the system classloader via `URLClassLoader`, and instantiates any class that extends `Plugin`.

### Plugin API

Extend `de.xbrowniecodez.jbytemod.plugin.Plugin`:

```java
public class MyPlugin extends Plugin {

    public MyPlugin() {
        super("My Plugin", "1.0.0", "YourName");
    }

    @Override
    public void init() {
        // Called once when the plugin is loaded
    }

    @Override
    public void loadFile(Map<String, ClassNode> classes) {
        // Called whenever a new JAR/APK is opened
    }

    @Override
    public boolean isClickable() {
        return true; // Show a menu entry for this plugin
    }

    @Override
    public void menuClick() {
        // Called when the user clicks the plugin's menu entry
    }
}
```

#### Provided helpers (in Plugin base class)

| Method | Returns | Description |
|--------|---------|-------------|
| `getCurrentFile()` | `Map<String, ClassNode>` | All classes in the currently loaded file |
| `getSelectedNode()` | `ClassNode` | The class currently selected in the tree |
| `getSelectedMethod()` | `MethodNode` | The method currently selected in the tree |
| `getTree()` | `JTree` | The class tree component |
| `getMenu()` | `JMenuBar` | The application menu bar |
| `updateTree()` | `void` | Refreshes the class tree after modifying classes |

---

## Building from source

### Prerequisites

- JDK 21
- Maven 3.9+

### Build

```sh
mvn clean package
```

The output JAR (with all dependencies shaded) is placed in `target/`.

### CI/CD

The project uses two CI systems:

- **GitHub Actions** (`.github/workflows/master.yml`): builds on every push to `master` with JDK 21 (Microsoft distribution on Ubuntu 24), runs `mvn clean package`, generates a changelog from git history since the last tag, and publishes a new GitHub Release automatically.
- **Drone CI** (`.drone.yml`): builds using `maven:3.9.7-amazoncorretto-21`, authenticating against a private Nexus repository for snapshot dependencies (ASM 9.10-SNAPSHOT).

Dependabot is configured to check Maven dependencies daily.

---

## Key dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| ASM | 9.10-SNAPSHOT | Bytecode reading, writing, tree API |
| CFR | 0.152 | Decompiler backend |
| Procyon | 0.6.0 | Decompiler backend |
| Vineflower | 1.12.0 | Decompiler backend (Fernflower fork) |
| JD-Core | 1.1.3 | Decompiler backend |
| Koffee | 1.0.2 | Kotlin DSL decompiler backend |
| jgraphx | 4.2.2 | Control flow graph rendering |
| JFreeChart | 1.5.5 | Chart rendering |
| DarkLaf | 3.0.2 | Dark/light theme (FlatLaf-based) |
| RSyntaxTextArea | 3.5.1 | Syntax-highlighted source editor |
| DiscordIPC | 0.6.1 | Discord Rich Presence |
| dex-translator | 2.3 | DEX → JVM bytecode conversion (APK support) |
| Guava | 32.1.3-jre | Utilities |
| Gson | 2.11.0 | JSON parsing (update checker) |
| Lombok | latest | Boilerplate reduction |
| commons-io | 2.16.1 | File utilities |
| commons-cli | 1.9.0 | Command-line argument parsing |
| attach | 1.8 | JVM attach API |
| Ant | 1.10.14 | Build utilities |
| Kotlin stdlib | 1.9.21 | Required by Koffee |

---

## Project structure

```
src/main/java/
├── de/xbrowniecodez/jbytemod/          # Remastered layer (main application)
│   ├── Main.java                        # Entry point (singleton enum), CLI parsing
│   ├── JByteMod.java                   # Application JFrame, state owner
│   ├── asm/                             # Custom ClassReader / ClassWriter
│   ├── decompiler/                      # ASMifier, JD-Core, Vineflower backends
│   ├── discord/                         # Discord Rich Presence integration
│   ├── plugin/                          # Plugin & PluginManager
│   ├── ui/                              # MemoryBar, NotificationManager, lists
│   └── utils/                           # BytecodeUtils, ClassUtils, UpdateChecker
│
├── me/grax/jbytemod/                    # Original JByteMod layer
│   ├── JarArchive.java                  # Map<String, ClassNode> container
│   ├── analysis/                        # ObfuscationAnalyzer, InsnAnalyzer
│   ├── decompiler/                      # CFR, Procyon, Koffee backends; Decompilers enum
│   ├── logging/                         # Logging
│   ├── res/                             # Options, LanguageRes (i18n)
│   ├── ui/                              # ClassTree, MyCodeList, MyMenuBar, dialogues, graph
│   └── utils/                           # DeobfuscateUtils, InstrUtils, attach, task, asm
│
├── me/lpk/util/                         # Utility library (AccessHelper, JarUtils, OpUtils)
│
├── com/javadeobfuscator/deobfuscator/   # Embedded deobfuscator / frame-based analyzer
│   ├── analyzer/                        # MethodAnalyzer, ArgsAnalyzer, frame types
│   └── utils/                           # TransformerHelper, Utils, PrimitiveUtils
│
├── de/xbrowniecodez/android/asm/        # APK support: Dex2ASMVisitorFactory
│
└── org/objectweb/asm/                   # Locally patched ASM classes (ClassWriter, tree nodes)

src/main/resources/
├── locale/                              # i18n XML files (en, de, es, pt-br, ru, zh, zh-cn, zh-tr)
└── resources/                           # Icons, themes, opcode reference HTML, properties
```

---

## Localization

The UI is localized using XML resource files under `src/main/resources/locale/`. Supported locales:

| Code | Language |
|------|----------|
| `en` | English |
| `de` | German |
| `es` | Spanish |
| `pt-br` | Portuguese (Brazil) |
| `ru` | Russian |
| `zh` | Chinese |
| `zh-cn` | Simplified Chinese |
| `zh-tr` | Traditional Chinese |

---

## Contributing

Contributions are welcome! To contribute:

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -am 'Add some feature'`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request.

Please report bugs or suggest improvements on the [issue tracker](https://github.com/apkreader/JByteMod-Remastered/issues).

---

## License

JByteMod Remastered is licensed under the **MIT License**. See the `LICENSE` file for details.

Portions of this project are derived from or include code from:

- [java-deobfuscator](https://github.com/java-deobfuscator) — deobfuscation utilities and frame-based analyzer
- [Radon](https://github.com/ItzSomebody/Radon) — additional deobfuscation transforms
- [ObjectWeb ASM](https://asm.ow2.io/) — locally patched ClassWriter and tree node classes (BSD-3-Clause)

See `src/main/resources/resources/LICENSES` for third-party license texts.

---

## Acknowledgements

Thanks to all contributors, the original JByteMod authors, and the maintainers of the decompiler and bytecode analysis libraries that make this tool possible.
