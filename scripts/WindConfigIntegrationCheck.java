import java.nio.file.*;
import java.util.Arrays;
import me.earthme.luminol.config.ConfigManager;
import dev.kaiijumc.kaiiju.KaiijuEntityLimits;

public class WindConfigIntegrationCheck {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        ConfigManager.initConfigs();
        var lophine = ConfigManager.getConfigs("lophine");
        assert !lophine.getAllConfigPaths("").isEmpty() : "Lophine modules missing";
        var luminol = ConfigManager.getConfigs("luminol").getFileInstance();
        Path legacy = Path.of("luminol_config/kaiiju_entity_limits.yml");
        Path global = Path.of("Wind/wind_global_config.toml");
        if (args.length == 0) {
            KaiijuEntityLimits.init(); // Actual ON_LOADED target, without starting a world or accepting EULA.
            assert !Files.exists(legacy.getParent());
            assert !Files.exists(Path.of("wind_config"));
            assert luminol.getNioPath().equals(global);
            assert !KaiijuEntityLimits.enabled;
            assert KaiijuEntityLimits.entityLimitsConfig.getInt("axolotl.limit") == 1000;
        } else {
            Files.createDirectories(legacy.getParent());
            Files.writeString(legacy, "enabled: false\naxolotl:\n  limit: 37\n  removal: 91\nunknown:\n  limit: 42\n");
            byte[] original = Files.readAllBytes(legacy);
            luminol.set("optimizations.kaiiju_entity_limiter.axolotl.limit", 38);
            luminol.save();
            byte[] before = Files.readAllBytes(global);
            try {
                KaiijuEntityLimits.init();
                throw new AssertionError("conflict accepted");
            } catch (IllegalStateException expected) { }
            assert Arrays.equals(before, Files.readAllBytes(global));
            Files.writeString(legacy, "broken: [");
            try {
                KaiijuEntityLimits.init();
                throw new AssertionError("malformed YAML accepted");
            } catch (IllegalStateException expected) { }
            assert Arrays.equals(before, Files.readAllBytes(global));
            Files.write(legacy, original);
            luminol.remove("optimizations.kaiiju_entity_limiter.axolotl.limit");
            KaiijuEntityLimits.init();
            assert KaiijuEntityLimits.entityLimitsConfig.getInt("axolotl.limit") == 37;
            assert KaiijuEntityLimits.entityLimitsConfig.getInt("unknown.limit") == 42;
            assert Arrays.equals(original, Files.readAllBytes(legacy));
            Files.writeString(legacy, "broken: [");
            KaiijuEntityLimits.init(); // Successful migration never reimports legacy YAML.
            assert KaiijuEntityLimits.entityLimitsConfig.getInt("axolotl.limit") == 37;
        }
        ConfigManager.getConfigs("luminol").clean();
        assert Boolean.TRUE.equals(luminol.get("wind-entity-limits-migrated"));
        lophine.saveConfigs();
        luminol.load();
        assert luminol.contains("optimizations.kaiiju_entity_limiter.axolotl.limit");
        System.out.println("Actual config modules and Kaiiju integration passed");
        System.exit(0);
    }
}
