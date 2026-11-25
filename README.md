# EnsoX UI

Widget, layout and painting framework for Minecraft screens (Fabric and NeoForge).
A library mod (`ensox_ui`): it does nothing on its own. Used by LootBeamsUp and Smithery.
License: MIT.

Source: https://github.com/enso-x/ensox-ui. A full framework guide (widgets, layout,
input, assets) will move here from LootBeamsUp's internal docs.

## Layout

| Project | Contents |
|---|---|
| `common` | all library code (`com.ensox.base`, `com.ensox.ui`), tests, access transformer / widener |
| `fabric` | `fabric.mod.json`, client entry point |
| `neoforge` | `neoforge.mods.toml`, `@Mod` entry point |

Loader jars contain the common classes (multiloader pattern). The entry points only register
the resource reload of `AssetLibrary` (`AssetReloadListener`), so mods never forward reloads.

## Using it in a mod

Repository `https://enso-x.github.io/maven/`, group `com.ensox.ui`:

```groovy
// common project
compileOnly "com.ensox.ui:ensox-ui-common:1.0.1+26.3"
// Fabric
implementation "com.ensox.ui:ensox-ui-fabric:1.0.1+26.3"
include "com.ensox.ui:ensox-ui-fabric:1.0.1+26.3"
// NeoForge
implementation "com.ensox.ui:ensox-ui-neoforge:1.0.1+26.3"
jarJar "com.ensox.ui:ensox-ui-neoforge:1.0.1+26.3"
```

Declare the dependency in the mod metadata (`"ensox_ui": ">=1.0.0"` in `fabric.mod.json`,
a `[[dependencies]]` entry with `versionRange = "[1.0.0,2)"` in `neoforge.mods.toml`).

No global state belongs to one mod: tooltip style is per screen (`UiScreen#tooltipStyle()`),
and asset keys always name their namespace (`AssetKey.icon(namespace, name)`).

## Versions

`<semver>+<minecraft version>`, one branch per Minecraft line (`release/26.3`, ...).

Mods nest the library jar-in-jar and the loader keeps only the newest copy, so:
- **minor/patch**: only backward compatible changes (add, never remove or change signatures);
- **major**: anything breaking. Mods declare `[1.0.0,2)` and keep working with any 1.x.

The library is never obfuscated: other mods compile against its names.

## Build and publish

- `gradlew build` — jars of all three projects, `gradlew :common:test` — tests.
- `gradlew publishLibrary -PmavenDir=<enso-x/maven checkout>` (or `ENSOX_MAVEN_DIR`) — tests,
  then `ensox-ui-common`, `-fabric`, `-neoforge` with sources. Without a directory it
  publishes into `build/maven`.
- Developing together with a mod: the mod includes this build as a composite
  (`-PensoxUiProject=<dir>`).
