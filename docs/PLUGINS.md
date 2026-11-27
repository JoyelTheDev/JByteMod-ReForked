# Plugins

## Installing

Open **Plugins > Manage plugins...** for installed plugins (enable, disable, remove, install from file)
or **Plugins > Browse plugins...** for repositories. Changes apply immediately, with no restart.

Plugins run with the same access as JByteMod. Only install plugins you trust.

## Writing a plugin

Extend `de.xbrowniecodez.jbytemod.plugin.Plugin` and put the jar in the plugins folder. Optional manifest attributes:

```
JByteMod-Plugin-Class: com.example.MyPlugin
JByteMod-Plugin-Id: my-plugin
JByteMod-Plugin-Description: One line shown in the manager
```

Without `JByteMod-Plugin-Class`, every class in the jar is checked for a `Plugin` subclass.
The id links an installed plugin to its repository entry, so updates replace it in place.
`shutdown()` is called when a plugin is disabled, updated or removed; close any windows or threads there.

## Plugin API

Override any of these. All are optional except the four abstract methods.

| Method | When it runs |
| --- | --- |
| `init()` | The plugin starts (also when re-enabled or updated) |
| `shutdown()` | The plugin is disabled, updated or removed |
| `loadFile(classes)` | A file is loaded, and once when the plugin starts while a file is open |
| `classSelected(node)` | A class is selected in the tree |
| `methodSelected(owner, method)` | A method is selected |
| `fileSaved(file)` | A file was saved successfully |

Helpers available inside the plugin (from `init()` onward):

- `log(message)` writes to the JByteMod log, prefixed with the plugin id.
- `toast(message)` shows a notification.
- `setting(key, default)` and `saveSetting(key, value)` keep small values between sessions. Values can contain any text.
- `dataFolder()` returns a private folder for your own files.
- `applicationVersion()` returns the running JByteMod version.
- `getCurrentFile()`, `getSelectedNode()`, `getSelectedMethod()`, `updateTree()`, `getTree()`, `getMenu()` as before.

Event handlers run on the interface thread. If one throws, the other plugins still run, the error is counted
and shown in the manager, and your plugin keeps running. Handlers that take over 250 ms are flagged because
they freeze the window. Do slow work on your own thread.

## Signing a plugin

Signing lets users trust a publisher once and be protected on later updates: an update must be signed by the
same key as the version they have, and an unsigned or differently signed update is refused.

```
java -cp JByteMod-ReForked.jar dev.joyel.plugins.PluginSigner keygen mykey
java -cp JByteMod-ReForked.jar dev.joyel.plugins.PluginSigner sign my-plugin-1.2.0.jar mykey my-plugin 1.2.0
```

`keygen` creates `mykey.private` (keep it secret, never publish it) and `mykey.public`, and prints your publisher
fingerprint. `sign` prints the `sha256`, `publisherKey` and `signature` lines to paste into the plugin's entry
in `plugins.json`. The signature covers the plugin id, the version and the file hash, so it cannot be moved to
another plugin or replayed on an older build. Sign again for every release.

To check a signature: `... PluginSigner verify my-plugin.jar mykey.public <signature> my-plugin 1.2.0`.

## Hosting a repository

A repository is an HTTPS address (or a local folder) that serves `plugins.json`. A GitHub repository
`https://github.com/owner/repo` is read from its `plugins.json` on `main` or `master`.

```json
{
  "schemaVersion": 1,
  "name": "My plugins",
  "plugins": [
    {
      "id": "my-plugin",
      "name": "My Plugin",
      "version": "1.2.0",
      "author": "Me",
      "description": "What it does",
      "minimumJByteModVersion": "2.9.0",
      "website": "https://example.org/my-plugin",
      "downloadUrl": "https://example.org/my-plugin-1.2.0.jar",
      "sha256": "64 hex characters",
      "fileName": "my-plugin.jar",
      "publisherKey": "optional, from PluginSigner",
      "signature": "optional, from PluginSigner"
    }
  ]
}
```

Downloads are rejected unless their SHA-256 matches. Remote addresses must use HTTPS
(plain HTTP is accepted only for localhost).

To ship a default repository, set `plugin.repository=https://...` in `jbytemod.properties`
(comma separated for several). JByteMod checks its repositories for plugin updates at startup,
shows a count next to the Plugins menu, and shows each new update once as a notification.
Users can switch this off in the Plugin manager.
