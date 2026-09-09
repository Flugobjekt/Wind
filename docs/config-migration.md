# Unified Wind configuration

All built-in modules use `Wind/wind_global_config.toml` (case-sensitive), in four tables:
`luminol`, `lophine`, `lophine_carpet`, `wind`. For example, the former
`[compat-config]` is now `[wind.compat-config]`. The file contains
`wind-config-format = 1` and `wind-directory-migrated = true` at its root;
do not remove these migration markers.

Existing config instance names, relative lookup keys, commands (including
`windconfig` and `windcompatconfig`), module discovery and translation prefixes
remain unchanged. Reload and clean operate on their own namespace only.
Status bars still default to enabled; the server mod name still defaults to Wind.
Configured values override those defaults.

## First startup

Wind merges the old `wind_config/wind_global_config.toml` into the canonical
`Wind/wind_global_config.toml` before loading modules. Both unified format and
pre-unification format are supported at either path. Unequal duplicates between
the two files abort migration. Source files and their existing backups are retained.

If either global file is already unified, previously imported legacy TOML files
are ignored, preventing stale settings from being resurrected. Otherwise Wind
also imports:

- `luminol_config/luminol_global_config.toml`
- `lophine_config/lophine_global_config.toml`
- `lophine_config/lophine_carpet_config.toml`
- `wind_config/wind_compat_config.toml`
- The existing `wind_config/wind_global_config.toml`: `compat-config`,
  `event-config`, `waypoint` belong to Wind; other old root keys belong to Luminol.

Unknown keys and comments are retained. Identical duplicates are accepted;
unequal duplicates or table/value collisions abort startup with the source and
key. Reconcile that key in the named legacy file and old global file, then
restart. No configuration file is overwritten on a merge conflict or parse error.

Before replacing an existing canonical global file, Wind copies it byte-for-byte to
`wind_global_config.toml.pre-merge`. An existing backup prevents migration rather
than being overwritten. Legacy files remain untouched. After successful migration,
only the global file is active; legacy files are not reimported on restart.

Writes are serialized across namespace instances, read the current global file,
replace only their own table, then synchronously write a temporary file and
atomically replace the global file. Atomic-move support is required; failures
propagate rather than falling back to an unsafe overwrite. Do not run multiple
servers against the same config directory or edit the file while it is saving.

## Kaiiju entity limits

`luminol_config/kaiiju_entity_limits.yml` is imported once into
`[luminol.optimizations.kaiiju_entity_limiter]` when Kaiiju initializes.
The original YAML remains untouched. Conflicting values, malformed YAML and
read failures abort initialization without saving defaults over existing settings.
The namespace marker `wind-entity-limits-migrated = true` prevents reimport;
config clean preserves both marker and entity settings. Fresh installations no
longer create `luminol_config`. Existing directories are deliberately not deleted.

Lophine settings are nested tables such as `[lophine.function.language]`, not
necessarily a standalone `[lophine]` header. A bounded pre-EULA launch loads these
modules but does not run the Kaiiju `ON_LOADED` hook.

## Regression check

Run `python3 scripts/check-wind-config.py` after Gradle has cached NightConfig
3.8.4. Uses `.jdk` by default, or `JAVA_HOME`; no test framework required.

After building Paperclip, run `python3 scripts/check-wind-config-integration.py`.
It launches the packaged server in ignored `run/config-regression`, stops at the
normal EULA gate, verifies all four populated namespaces, then runs actual module
loading and Kaiiju initialization in isolated temporary directories. No EULA is
accepted. Initial launcher setup may download server dependencies.
