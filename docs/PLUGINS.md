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
      "fileName": "my-plugin.jar"
    }
  ]
}
```

Downloads are rejected unless their SHA-256 matches. Remote addresses must use HTTPS
(plain HTTP is accepted only for localhost).

To ship a default repository, add `plugin.repository=https://...` to `jbytemod.properties`
(comma separated for several).
