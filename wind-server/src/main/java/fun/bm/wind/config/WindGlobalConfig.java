package fun.bm.wind.config;

import com.electronwill.nightconfig.core.*;
import com.electronwill.nightconfig.core.concurrent.ConcurrentCommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.utils.CommentedConfigWrapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** Namespace views retain the upstream config API without competing whole-file writers. */
public final class WindGlobalConfig extends CommentedConfigWrapper<CommentedConfig> implements CommentedFileConfig {
    private static final String MARKER = "wind-config-format";
    private static final List<String> NAMES = List.of("luminol", "lophine", "lophine_carpet", "wind");
    private final Path path;
    private final String namespace;

    private WindGlobalConfig(Path path, String namespace) {
        super(new com.electronwill.nightconfig.core.concurrent.SynchronizedConfig());
        this.path = path;
        this.namespace = namespace;
    }

    public static CommentedFileConfig open(String name) throws IOException {
        return open(Path.of("."), name);
    }

    public static synchronized CommentedFileConfig open(Path root, String name) throws IOException {
        if (!NAMES.contains(name)) throw new IllegalArgumentException("Unknown Wind config: " + name);
        Path path = root.resolve("Wind/wind_global_config.toml");
        Files.createDirectories(path.getParent());
        try (CommentedFileConfig merged = CommentedFileConfig.of(path)) {
            if (Files.exists(path)) merged.load();
            if (!merged.contains("wind-directory-migrated")) {
                Path previous = root.resolve("wind_config/wind_global_config.toml");
                boolean unified = merged.contains(MARKER);
                if (unified) validate(merged, path);
                CommentedConfig old = CommentedConfig.copy(merged);
                merged.clear();
                for (String namespace : NAMES) merged.set(namespace, merged.createSubConfig());
                importGlobal(merged, old, path);
                if (Files.exists(previous)) {
                    try (CommentedFileConfig source = CommentedFileConfig.of(previous)) {
                        source.load();
                        unified |= source.contains(MARKER);
                        importGlobal(merged, source, previous);
                    }
                }
                // Unified sources already consumed these files; never resurrect stale legacy values.
                if (!unified) {
                    importFile(merged, root.resolve("luminol_config/luminol_global_config.toml"), "luminol");
                    importFile(merged, root.resolve("lophine_config/lophine_global_config.toml"), "lophine");
                    importFile(merged, root.resolve("lophine_config/lophine_carpet_config.toml"), "lophine_carpet");
                    importFile(merged, root.resolve("wind_config/wind_compat_config.toml"), "wind");
                }
                merged.set(MARKER, 1);
                merged.set("wind-directory-migrated", true);
                if (Files.exists(path)) Files.copy(path, path.resolveSibling("wind_global_config.toml.pre-merge"));
                writeAtomically(merged, path);
            } else {
                if (!Boolean.TRUE.equals(merged.get("wind-directory-migrated"))) {
                    throw new IllegalStateException("Invalid wind-directory-migrated in " + path);
                }
                validate(merged, path);
            }
        }
        WindGlobalConfig view = new WindGlobalConfig(path, name);
        view.load();
        return view;
    }

    private static void importGlobal(CommentedConfig merged, CommentedConfig source, Path path) {
        if (source.contains(MARKER)) {
            validate(source, path);
            merge(merged, source, path.toString());
            return;
        }
        // Original Wind and pre-unification Luminol shared this filename.
        for (String key : source.valueMap().keySet()) {
            String namespace = Set.of("compat-config", "event-config", "waypoint").contains(key) ? "wind" : "luminol";
            CommentedConfig one = CommentedConfig.inMemory();
            one.set(List.of(key), source.get(List.of(key)));
            one.setComment(List.of(key), source.getComment(List.of(key)));
            merge(merged.get(namespace), one, path + ":" + namespace);
        }
    }

    private static void validate(CommentedConfig global, Path path) {
        if (!Objects.equals(global.get(MARKER), 1)) {
            throw new IllegalStateException("Unsupported Wind config format in " + path);
        }
        for (String name : NAMES) {
            if (!(global.get(name) instanceof CommentedConfig)) {
                throw new IllegalStateException("Missing or invalid config table: " + name + " in " + path);
            }
        }
    }

    private static void importFile(CommentedConfig merged, Path source, String namespace) {
        if (!Files.exists(source)) return;
        try (CommentedFileConfig old = CommentedFileConfig.of(source)) {
            old.load();
            merge(merged.get(namespace), old, source + ":" + namespace);
        }
    }

    static void merge(CommentedConfig target, CommentedConfig source, String location) {
        for (CommentedConfig.Entry entry : source.entrySet()) {
            List<String> key = List.of(entry.getKey());
            Object value = entry.getValue();
            if (!target.contains(key)) {
                if (value instanceof CommentedConfig nested) {
                    CommentedConfig copy = target.createSubConfig();
                    merge(copy, nested, location + "." + entry.getKey());
                    target.set(key, copy);
                } else {
                    target.set(key, value);
                }
                target.setComment(key, entry.getComment());
            } else if (target.get(key) instanceof CommentedConfig existing && value instanceof CommentedConfig incoming) {
                merge(existing, incoming, location + "." + entry.getKey());
            } else if (!Objects.equals(target.get(key), value)) {
                throw new IllegalStateException("Config migration conflict at " + location + "." + entry.getKey()
                        + "; reconcile this key in the legacy and global files, then restart. No config was overwritten.");
            }
            if (entry.getComment() != null && !Objects.equals(target.getComment(key), entry.getComment())) {
                String previous = target.getComment(key);
                target.setComment(key, previous == null ? entry.getComment() : previous + "\n" + entry.getComment());
            }
        }
    }

    private static void writeAtomically(CommentedConfig config, Path path) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), "wind-global-", ".toml");
        try {
            try (CommentedFileConfig output = CommentedFileConfig.builder(temporary).sync().build()) {
                merge(output, config, path.toString());
                output.save();
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override public void save() {
        synchronized (WindGlobalConfig.class) {
            try (CommentedFileConfig global = CommentedFileConfig.of(path)) {
                global.load();
                validate(global, path);
                global.set(namespace, config);
                writeAtomically(global, path);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot save " + path, e);
            }
        }
    }

    @Override public void load() {
        synchronized (WindGlobalConfig.class) {
            try (CommentedFileConfig global = CommentedFileConfig.of(path)) {
                global.load();
                validate(global, path);
                Object section = global.get(namespace);
                if (!(section instanceof CommentedConfig values)) {
                    throw new IllegalStateException("Missing or invalid config table: " + namespace + " in " + path);
                }
                config.clear();
                config.clearComments();
                merge(config, values, path + ":" + namespace);
            }
        }
    }

    @Override public void close() { }
    @Override public File getFile() { return path.toFile(); }
    @Override public Path getNioPath() { return path; }
    @Override public ConcurrentCommentedConfig createSubConfig() { return new com.electronwill.nightconfig.core.concurrent.SynchronizedConfig(config.configFormat(), HashMap::new); }
    @Override public <R> R bulkCommentedRead(Function<? super UnmodifiableCommentedConfig, R> action) { synchronized (WindGlobalConfig.class) { return action.apply(config); } }
    @Override public <R> R bulkCommentedUpdate(Function<? super CommentedConfig, R> action) { synchronized (WindGlobalConfig.class) { return action.apply(config); } }
}
