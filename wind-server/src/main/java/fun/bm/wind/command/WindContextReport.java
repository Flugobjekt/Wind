package fun.bm.wind.command;

import com.mojang.brigadier.StringReader;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Wind: builds rich Adventure Component diagnostic reports for {@code /wind context} and {@code /wind worlds}.
 *
 * <p>Uses the standard Folia/Wind hex color palette with structured bullets, status badges,
 * and explanatory hover tooltips so administrators and developers can clearly understand the multithreaded
 * execution context, regioniser ownership, and selector resolution semantics.
 */
public final class WindContextReport {

    public static final TextColor HEADER = TextColor.color(79, 164, 240);       // #4FA4F0 (Bright sky blue)
    public static final TextColor PRIMARY = TextColor.color(48, 145, 237);      // #3091ED (Medium blue)
    public static final TextColor SECONDARY = TextColor.color(104, 177, 240);   // #68B1F0 (Light blue)
    public static final TextColor INFORMATION = TextColor.color(180, 220, 255); // #B4DCFF (Soft pale blue)
    public static final TextColor LIST = TextColor.color(33, 97, 188);          // #2161BC (Royal blue bullet)
    public static final TextColor MUTED = TextColor.color(120, 140, 160);       // #788CA0 (Muted gray-blue for pointers)
    public static final TextColor SUCCESS = TextColor.color(85, 255, 85);       // #55FF55 (Green)
    public static final TextColor WARNING = TextColor.color(255, 255, 85);      // #FFFF55 (Yellow)
    public static final TextColor ERROR = TextColor.color(255, 85, 85);         // #FF5555 (Red)

    private WindContextReport() {
    }

    private static String id(final Object o) {
        return o == null ? "null" : "0x" + Integer.toHexString(System.identityHashCode(o));
    }

    private static String pos(final Vec3 v) {
        return v == null ? "null" : String.format("%.2f, %.2f, %.2f", v.x, v.y, v.z);
    }

    private static String worldOf(final ServerLevel level) {
        return level == null ? "null" : level.getWorld().getName();
    }

    private static Component section(final String title, final String tooltip) {
        final TextComponent.Builder builder = Component.text();
        builder.append(Component.text("== ", SECONDARY));
        builder.append(Component.text(title, HEADER, TextDecoration.BOLD));
        builder.append(Component.text(" ==", SECONDARY));
        if (tooltip != null && !tooltip.isEmpty()) {
            builder.hoverEvent(HoverEvent.showText(Component.text(tooltip, SECONDARY)));
        }
        return builder.build();
    }

    private static Component bullet(final String label, final Component value, final String tooltip) {
        final TextComponent.Builder builder = Component.text();
        builder.append(Component.text(" - ", LIST, TextDecoration.BOLD));
        builder.append(Component.text(label + ": ", PRIMARY));
        builder.append(value);
        if (tooltip != null && !tooltip.isEmpty()) {
            builder.hoverEvent(HoverEvent.showText(Component.text(tooltip, SECONDARY)));
        }
        return builder.build();
    }

    private static Component subBullet(final String label, final Component value, final String tooltip) {
        final TextComponent.Builder builder = Component.text();
        builder.append(Component.text("   - ", SECONDARY));
        builder.append(Component.text(label + ": ", PRIMARY));
        builder.append(value);
        if (tooltip != null && !tooltip.isEmpty()) {
            builder.hoverEvent(HoverEvent.showText(Component.text(tooltip, SECONDARY)));
        }
        return builder.build();
    }

    /**
     * Builds the diagnostic report for the execution context of the calling thread and command source.
     */
    public static List<Component> context(final CommandSourceStack source) {
        final List<Component> out = new ArrayList<>();
        final Thread thread = Thread.currentThread();
        final boolean isTickThread = ca.spottedleaf.moonrise.common.util.TickThread.isTickThread();
        final boolean isGlobalTick = RegionizedServer.isGlobalTickThread();

        // 1. Title Header
        out.add(Component.text()
                .append(Component.text("Wind Context Report", HEADER, TextDecoration.BOLD))
                .append(Component.text(" (Thread & Execution Diagnostics)", SECONDARY))
                .build());

        // 2. Section: Thread Environment
        out.add(section("Thread Environment", "Java thread role and scheduling state executing this command"));
        out.add(bullet("Thread Name", Component.text(thread.getName(), INFORMATION), "Java thread name of the caller"));

        final Component roleBadge;
        if (isTickThread) {
            if (isGlobalTick) {
                roleBadge = Component.text("[Global Region Thread]", WARNING, TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("Global tick thread: handles connections, weather, world borders, console/RCON commands, and global tasks; it owns no world chunks", SECONDARY)));
            } else {
                roleBadge = Component.text("[World Region Tick Thread]", SUCCESS, TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("World region tick thread: owns and ticks the chunks and entities in one world region", SECONDARY)));
            }
        } else {
            roleBadge = Component.text("[Off-Region / Async Thread]", MUTED)
                    .hoverEvent(HoverEvent.showText(Component.text("Async or off-region thread: owns no world region and cannot access chunks or entities synchronously", SECONDARY)));
        }
        out.add(bullet("Thread Role", roleBadge, "Role of this thread in Folia regionized threading"));

        final Component flags = Component.text()
                .append(Component.text("Tick Thread: ", PRIMARY))
                .append(isTickThread ? Component.text("[Yes]", SUCCESS) : Component.text("[No]", MUTED))
                .append(Component.text(" | Global Region: ", SECONDARY))
                .append(isGlobalTick ? Component.text("[Yes]", WARNING) : Component.text("[No]", MUTED))
                .build();
        out.add(bullet("Flags", flags, "Tick-thread and global-region flags"));

        // 3. Section: Current Tick Region
        out.add(section("Current Tick Region", "World region currently ticked by this thread"));
        final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region =
                TickRegionScheduler.getCurrentRegion();
        if (region == null) {
            out.add(bullet("Active Region", Component.text("None (This thread is not ticking a region)", MUTED),
                    "This thread is not bound to a world region; normal for console, RCON, or async calls"));
        } else {
            final ThreadedRegionizer<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> regioniser = region.regioniser;
            final ChunkPos centerChunk = region.getCenterChunk();
            final String centerCoord = centerChunk == null ? "unknown" : ((centerChunk.x() << 4) | 7) + ", " + ((centerChunk.z() << 4) | 7);
            final boolean isOwn = regioniser.world == regioniser.world.getWorld().getHandle();

            final Component regionInfo = Component.text()
                    .append(Component.text(worldOf(regioniser.world) + " (" + centerCoord + ")", INFORMATION))
                    .append(Component.text(" " + id(region), MUTED))
                    .build();
            out.add(bullet("Region Center", regionInfo, "Center coordinates and instance ID of the current region"));

            final Component regioniserInfo = Component.text()
                    .append(Component.text(worldOf(regioniser.world), INFORMATION))
                    .append(Component.text(" " + id(regioniser), MUTED))
                    .append(Component.text(" "))
                    .append(isOwn
                            ? Component.text("[Dedicated Regioniser]", SUCCESS)
                                    .hoverEvent(HoverEvent.showText(Component.text("This world has a dedicated ThreadedRegionizer and isolated scheduling", SECONDARY)))
                            : Component.text("[SHARED WITH OTHER WORLD]", ERROR, TextDecoration.BOLD)
                                    .hoverEvent(HoverEvent.showText(Component.text("Error: Regioniser is shared with another world!", ERROR))))
                    .build();
            out.add(bullet("Regioniser", regioniserInfo, "ThreadedRegionizer instance assigned to this world"));

            final RegionizedWorldData worldData = TickRegionScheduler.getCurrentRegionizedWorldData();
            if (worldData != null) {
                final Component worldDataInfo = Component.text()
                        .append(Component.text(worldOf(worldData.world), INFORMATION))
                        .append(Component.text(" " + id(worldData), MUTED))
                        .build();
                out.add(bullet("Region WorldData", worldDataInfo, "RegionizedWorldData view for this region"));
            }
        }

        // 4. Section: Command Source
        out.add(section("Command Source", "Command source object and anchor coordinates"));
        final Component senderInfo = Component.text()
                .append(Component.text(source.getBukkitSender().getName(), INFORMATION))
                .append(Component.text(" [" + source.getBukkitSender().getClass().getSimpleName() + "]", SECONDARY))
                .build();
        out.add(bullet("Sender", senderInfo, "Command sender entity or console interface"));

        final Component sourceWorldInfo = Component.text()
                .append(Component.text(worldOf(source.getLevel()), INFORMATION))
                .append(Component.text(" (Dim: " + source.getLevel().dimension().identifier() + ")", SECONDARY))
                .append(Component.text(" " + id(source.getLevel()), MUTED))
                .build();
        out.add(bullet("Source World", sourceWorldInfo, "World bound to the command source; selectors and relative coordinates use this world"));

        final Component posInfo = Component.text()
                .append(Component.text(pos(source.getPosition()), INFORMATION))
                .append(Component.text(" | Rotation: " + String.format("%.1f, %.1f", source.getRotation().x, source.getRotation().y), SECONDARY))
                .build();
        out.add(bullet("Source Position", posInfo, "Command source coordinates and rotation"));

        // 5. Section: Source Entity & Ownership
        out.add(section("Source Entity & Ownership", "Entity bound to the command and its region-thread ownership"));
        final Entity entity = source.getEntity();
        if (entity == null) {
            out.add(bullet("Entity", Component.text("None (Source is not an entity)", MUTED), "Command source is not an entity, such as console, RCON, or a function"));
        } else {
            final ServerLevel entityLevel = (ServerLevel) entity.level();
            final boolean isOwner = ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(entity);
            final boolean sameWorld = entityLevel == source.getLevel();

            final Component entityInfo = Component.text()
                    .append(Component.text(entity.getScoreboardName(), INFORMATION))
                    .append(Component.text(" [" + entity.getType().toShortString() + "]", SECONDARY))
                    .append(Component.text(" " + id(entity), MUTED))
                    .build();
            out.add(bullet("Entity", entityInfo, "Name and type of the entity bound to the command"));

            final Component entityLoc = Component.text()
                    .append(Component.text(worldOf(entityLevel), INFORMATION))
                    .append(Component.text(" (" + pos(entity.position()) + ")", SECONDARY))
                    .build();
            out.add(bullet("Entity Position", entityLoc, "Actual world and precise coordinates of the entity"));

            final Component ownership = isOwner
                    ? Component.text("[Owned by Thread (Safe Sync Access)]", SUCCESS, TextDecoration.BOLD)
                            .hoverEvent(HoverEvent.showText(Component.text("Current thread owns this entity region and may access the synchronous Bukkit API directly", SECONDARY)))
                    : Component.text("[Cross-Region / Not Owned (Requires Scheduler)]", WARNING, TextDecoration.BOLD)
                            .hoverEvent(HoverEvent.showText(Component.text("Current thread does not own this entity region; use entity.getScheduler() to dispatch access", SECONDARY)));
            out.add(bullet("Thread Ownership", ownership, "Whether the current thread owns the entity region"));

            final Component worldAlign = sameWorld
                    ? Component.text("[Same World as Source]", SUCCESS)
                    : Component.text("[World Mismatch with Source]", ERROR, TextDecoration.BOLD);
            out.add(bullet("World Alignment", worldAlign, "Whether the entity and command source are in the same world"));
        }

        // 6. Section: Target Selector Resolution
        out.add(section("Target Selector Resolution", "Players resolved by vanilla selectors from this source; explains cross-world @p results"));
        out.add(bullet("Selector @p (Nearest)", resolveSingle(source, "@p"), "Vanilla @p selects the nearest player server-wide by three-dimensional Euclidean distance"));
        out.add(bullet("Selector @p[distance=..1M]", resolveSingle(source, "@p[distance=..1000000]"), "@p result with an explicit distance constraint"));
        out.add(bullet("Selector @a (All)", resolveAll(source, "@a"), "All matching online players"));

        return out;
    }

    /**
     * Builds the diagnostic report for level and regioniser identity of every loaded world.
     */
    public static List<Component> worlds() {
        final List<Component> out = new ArrayList<>();
        final List<World> bukkitWorlds = Bukkit.getWorlds();

        long totalChunks = 0;
        int totalRegions = 0;

        for (final World bukkitWorld : bukkitWorlds) {
            final ServerLevel level = ((CraftWorld) bukkitWorld).getHandle();
            final List<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>> regions = new ArrayList<>();
            level.regioniser.computeForAllRegions(regions::add);
            totalRegions += regions.size();
            totalChunks += bukkitWorld.getLoadedChunks().length;
        }

        // 1. Title Header
        out.add(Component.text()
                .append(Component.text("Wind Loaded Worlds Report", HEADER, TextDecoration.BOLD))
                .append(Component.text(" (" + bukkitWorlds.size() + " loaded worlds)", SECONDARY))
                .build());

        // 2. Summary Row
        final Component summary = Component.text()
                .append(Component.text("Total Worlds: ", PRIMARY))
                .append(Component.text(bukkitWorlds.size(), INFORMATION))
                .append(Component.text(" | Online Players: ", SECONDARY))
                .append(Component.text(Bukkit.getOnlinePlayers().size(), INFORMATION))
                .append(Component.text(" | Active Regions: ", SECONDARY))
                .append(Component.text(totalRegions, INFORMATION))
                .append(Component.text(" | Loaded Chunks: ", SECONDARY))
                .append(Component.text(totalChunks, INFORMATION))
                .build();
        out.add(bullet("Summary", summary, "Server-wide overview of loaded worlds, online players, and regions"));

        // 3. Per-World Breakdown
        for (final World bukkitWorld : bukkitWorlds) {
            final ServerLevel level = ((CraftWorld) bukkitWorld).getHandle();
            final ThreadedRegionizer<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> regioniser = level.regioniser;
            final List<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>> regions = new ArrayList<>();
            regioniser.computeForAllRegions(regions::add);

            final boolean isOwn = regioniser.world == level;

            final Component worldHeader = Component.text()
                    .append(Component.text(" - World: ", LIST, TextDecoration.BOLD))
                    .append(Component.text(bukkitWorld.getName(), HEADER, TextDecoration.BOLD))
                    .append(Component.text(" (" + level.dimension().identifier() + ")", SECONDARY))
                    .append(Component.text(" " + id(level), MUTED))
                    .build();
            out.add(worldHeader);

            final Component regioniserInfo = Component.text()
                    .append(Component.text(id(regioniser), MUTED))
                    .append(Component.text(" "))
                    .append(isOwn
                            ? Component.text("[Dedicated Regioniser]", SUCCESS)
                                    .hoverEvent(HoverEvent.showText(Component.text("This world has a dedicated ThreadedRegionizer and isolated region scheduling", SECONDARY)))
                            : Component.text("[SHARED WITH " + worldOf(regioniser.world) + "]", ERROR, TextDecoration.BOLD)
                                    .hoverEvent(HoverEvent.showText(Component.text("Warning: this world shares a Regioniser with another world and may trigger cross-world thread assertions", ERROR))))
                    .build();
            out.add(subBullet("Regioniser", regioniserInfo, "ThreadedRegionizer instance and isolation check for this world"));

            final Component regionsInfo = Component.text()
                    .append(Component.text(regions.size(), INFORMATION))
                    .append(Component.text(" live regions", PRIMARY))
                    .append(Component.text(" (ID: " + id(level.tickRegions) + ")", MUTED))
                    .build();
            out.add(subBullet("Active Regions", regionsInfo, "Number of active regions ticked for players or chunk tickets in this world"));

            final Component chunksAndPlayers = Component.text()
                    .append(Component.text("Loaded Chunks: ", PRIMARY))
                    .append(Component.text(bukkitWorld.getLoadedChunks().length, INFORMATION))
                    .append(Component.text(" | Players: ", SECONDARY))
                    .append(Component.text(bukkitWorld.getPlayers().size(), INFORMATION))
                    .build();
            out.add(subBullet("Chunks & Players", chunksAndPlayers, "Loaded chunks and online players in this world"));
        }

        return out;
    }

    private static Component resolveSingle(final CommandSourceStack source, final String selector) {
        try {
            final EntitySelector parsed = new EntitySelectorParser(new StringReader(selector), true).parse();
            final ServerPlayer player = parsed.findSinglePlayer(source);
            return describe(player, source);
        } catch (final Exception e) {
            return Component.text("<" + e.getClass().getSimpleName() + ": " + e.getMessage() + ">", ERROR);
        }
    }

    private static Component resolveAll(final CommandSourceStack source, final String selector) {
        try {
            final EntitySelector parsed = new EntitySelectorParser(new StringReader(selector), true).parse();
            final List<ServerPlayer> players = parsed.findPlayers(source);
            if (players.isEmpty()) {
                return Component.text("<none>", MUTED);
            }
            final TextComponent.Builder builder = Component.text();
            for (int i = 0; i < players.size(); i++) {
                if (i > 0) {
                    builder.append(Component.text("; ", SECONDARY));
                }
                builder.append(describe(players.get(i), source));
            }
            return builder.build();
        } catch (final Exception e) {
            return Component.text("<" + e.getClass().getSimpleName() + ": " + e.getMessage() + ">", ERROR);
        }
    }

    private static Component describe(final ServerPlayer player, final CommandSourceStack source) {
        if (player == null) {
            return Component.text("<none>", MUTED);
        }
        final ServerLevel level = player.level();
        final double distance = Math.sqrt(player.position().distanceToSqr(source.getPosition()));
        final boolean sameWorld = level == source.getLevel();

        final TextComponent.Builder builder = Component.text();
        builder.append(Component.text(player.getScoreboardName(), INFORMATION, TextDecoration.BOLD));
        builder.append(Component.text(" in ", PRIMARY));
        builder.append(Component.text(worldOf(level), INFORMATION));
        builder.append(Component.text(" (" + pos(player.position()) + ")", SECONDARY));
        builder.append(Component.text(" | dist: ", PRIMARY));
        builder.append(Component.text(String.format("%.1f", distance), INFORMATION));
        builder.append(Component.text(" | ", SECONDARY));
        builder.append(sameWorld
                ? Component.text("[✓ Same World]", SUCCESS)
                : Component.text("[⚠ Cross-World]", WARNING)
                        .hoverEvent(HoverEvent.showText(Component.text("This player is in another world; vanilla @p compares Euclidean distance across worlds", WARNING))));
        return builder.build();
    }
}

