package fun.bm.wind.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Keeps Kaiiju's YAML lookup API, but never writes a separate YAML file. */
public final class WindEntityLimitsConfig {
    private static final String SECTION = "optimizations.kaiiju_entity_limiter";
    private static final String MARKER = "wind-entity-limits-migrated";

    public static YamlConfiguration load(CommentedFileConfig global) {
        try {
            CommentedConfig values = global.createSubConfig();
            Object existing = global.get(SECTION);
            if (existing != null) {
                if (!(existing instanceof CommentedConfig table)) {
                    throw new IllegalStateException("Invalid table: luminol." + SECTION);
                }
                WindGlobalConfig.merge(values, table, SECTION);
            }
            if (!global.contains(MARKER)) {
                Path legacy = Path.of("luminol_config/kaiiju_entity_limits.yml");
                if (Files.exists(legacy)) {
                    YamlConfiguration yaml = new YamlConfiguration();
                    yaml.load(legacy.toFile()); // Strict parsing: never replace unreadable settings with defaults.
                    CommentedConfig incoming = global.createSubConfig();
                    fromYaml(yaml, incoming);
                    WindGlobalConfig.merge(values, incoming, legacy + ":luminol." + SECTION);
                }
            } else if (!Boolean.TRUE.equals(global.get(MARKER))) {
                throw new IllegalStateException("Invalid " + MARKER);
            }
            values.add("enabled", false);
            values.add("axolotl.limit", 1000);
            values.add("axolotl.removal", 2000);
            YamlConfiguration result = new YamlConfiguration();
            toYaml(values, result);
            global.set(SECTION, values);
            global.set(MARKER, true);
            global.save();
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load Wind entity limits; reconcile legacy YAML and luminol."
                    + SECTION + " before restarting", e);
        }
    }

    private static void fromYaml(ConfigurationSection yaml, CommentedConfig target) {
        for (String key : yaml.getKeys(false)) {
            Object value = yaml.get(key);
            if (value instanceof ConfigurationSection section) {
                CommentedConfig child = target.createSubConfig();
                fromYaml(section, child);
                value = child;
            }
            target.set(List.of(key), value);
            List<String> comments = yaml.getComments(key);
            if (!comments.isEmpty()) target.setComment(List.of(key), String.join("\n", comments));
        }
    }

    private static void toYaml(CommentedConfig values, ConfigurationSection yaml) {
        for (CommentedConfig.Entry entry : values.entrySet()) {
            if (entry.getValue() instanceof CommentedConfig table) {
                toYaml(table, yaml.createSection(entry.getKey()));
            } else {
                yaml.set(entry.getKey(), entry.getValue());
            }
        }
    }
}
