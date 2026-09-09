import fun.bm.wind.config.WindGlobalConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class WindGlobalConfigCheck {
    static void put(Path root, String file, String contents) throws Exception {
        Path path = root.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("wind-config-check-");
        try {
            String global = "Wind/wind_global_config.toml";
            put(root, global, "# preserved\n[function.tpsbar]\nenabled = false\n[compat-config]\ndiagnostics = false\n");
            put(root, "luminol_config/luminol_global_config.toml", "[function.tpsbar]\nenabled = false\n[unknown]\nvalue = [1, 2]\n");
            put(root, "lophine_config/lophine_global_config.toml", "[function.language]\nlang = 'de_de'\n");
            put(root, "lophine_config/lophine_carpet_config.toml", "[function.language]\nlang = 'carpet'\n");
            put(root, "wind_config/wind_compat_config.toml", "[compat-config]\ndiagnostics = true\n");
            byte[] before = Files.readAllBytes(root.resolve(global));
            try {
                WindGlobalConfig.open(root, "wind");
                throw new AssertionError("conflict accepted");
            } catch (IllegalStateException expected) {
                assert expected.getMessage().contains("diagnostics") : expected;
            }
            assert Arrays.equals(before, Files.readAllBytes(root.resolve(global)));
            put(root, "wind_config/wind_compat_config.toml", "[compat-config]\ndiagnostics = false\n");
            var luminol = WindGlobalConfig.open(root, "luminol");
            var lophine = WindGlobalConfig.open(root, "lophine");
            var carpet = WindGlobalConfig.open(root, "lophine_carpet");
            var wind = WindGlobalConfig.open(root, "wind");
            assert Boolean.FALSE.equals(luminol.get("function.tpsbar.enabled"));
            assert luminol.get("unknown.value").equals(List.of(1, 2));
            assert lophine.get("function.language.lang").equals("de_de");
            assert carpet.get("function.language.lang").equals("carpet");
            assert Boolean.FALSE.equals(wind.get("compat-config.diagnostics"));
            assert Arrays.equals(before, Files.readAllBytes(root.resolve(global + ".pre-merge")));
            CompletableFuture.allOf(List.of(luminol, lophine, carpet, wind).stream().map(view -> CompletableFuture.runAsync(() -> {
                for (int i = 0; i < 20; i++) { view.set("check", i); view.save(); }
            })).toArray(CompletableFuture[]::new)).join();
            for (String name : List.of("luminol", "lophine", "lophine_carpet", "wind")) {
                assert WindGlobalConfig.open(root, name).get("check").equals(19);
            }
            carpet.clear(); carpet.save();
            lophine.load();
            assert lophine.get("function.language.lang").equals("de_de");
            put(root, "wind_config/wind_compat_config.toml", "invalid legacy TOML ignored after migration");
            assert Boolean.FALSE.equals(WindGlobalConfig.open(root, "wind").get("compat-config.diagnostics"));
            try (var raw = CommentedFileConfig.of(root.resolve(global))) {
                raw.load();
                assert raw.getComment("luminol.function.tpsbar").contains("preserved");
            }
            Path fresh = root.resolve("fresh");
            assert WindGlobalConfig.open(fresh, "wind").isEmpty();
            assert !Files.exists(fresh.resolve("wind_config"));
            String previous = "wind_config/wind_global_config.toml";
            for (boolean unified : List.of(false, true)) {
                Path migration = root.resolve("migration-" + unified);
                String source = unified
                        ? "wind-config-format = 1\n[luminol]\n[lophine]\ncustom = 42\n[lophine_carpet]\n[wind]\ncustom = 9\n"
                        : "[custom]\nvalue = 42\n";
                put(migration, previous, source);
                put(migration, previous + ".pre-merge", "existing backup");
                if (unified) put(migration, "lophine_config/lophine_global_config.toml", "malformed stale legacy [");
                var migrated = WindGlobalConfig.open(migration, unified ? "lophine" : "luminol");
                assert migrated.get(unified ? "custom" : "custom.value").equals(42);
                assert Files.readString(migration.resolve(previous)).equals(source);
                assert Files.readString(migration.resolve(previous + ".pre-merge")).equals("existing backup");
                put(migration, previous, "malformed ignored after migration [");
                assert WindGlobalConfig.open(migration, unified ? "lophine" : "luminol").get(unified ? "custom" : "custom.value").equals(42);
            }
            Path conflict = root.resolve("directory-conflict");
            String source = "wind-config-format = 1\n[luminol]\ncustom = 1\n[lophine]\n[lophine_carpet]\n[wind]\n";
            put(conflict, previous, source);
            put(conflict, global, source.replace("custom = 1", "custom = 2"));
            String destination = Files.readString(conflict.resolve(global));
            try {
                WindGlobalConfig.open(conflict, "wind");
                throw new AssertionError("directory conflict accepted");
            } catch (IllegalStateException expected) {
                assert expected.getMessage().contains("custom");
            }
            assert Files.readString(conflict.resolve(global)).equals(destination);
            assert Files.readString(conflict.resolve(previous)).equals(source);
            put(conflict, previous, "malformed [");
            try {
                WindGlobalConfig.open(conflict, "wind");
                throw new AssertionError("malformed source accepted");
            } catch (RuntimeException expected) { }
            assert Files.readString(conflict.resolve(global)).equals(destination);
            System.out.println("Wind config regression checks passed");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
