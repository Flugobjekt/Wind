package fun.bm.wind.command.sub;

import fun.bm.wind.command.WindCommand;
import fun.bm.wind.command.WindContextReport;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.LiteralNode;

/**
 * Wind: {@code /wind worlds} - level and regioniser identity for every loaded world.
 *
 * <p>Read-only. The point of it is comparability: a world created or imported at runtime prints the
 * same shape of row as one loaded at startup, so "does this extra world have its own regioniser"
 * stops being an argument and becomes two identity hashes next to each other.
 */
public class WorldsCommand extends LiteralNode {

    public WorldsCommand() {
        super("worlds");
    }

    @Override
    public boolean requires(@NotNull CommandSourceStack source) {
        return WindCommand.hasPermission(source.getSender(), this.name);
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) {
        for (final Component line : WindContextReport.worlds()) {
            context.getSender().sendMessage(line);
        }
        return true;
    }
}
