package fr.frabous.stellar.common;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public final class StellarCommand extends CommandBase {
    @Override public String getName() { return "stellar"; }
    @Override public String getUsage(ICommandSender sender) { return "/stellar <give|status|cancel>"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 1 && "give".equals(args[0])) {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            ItemStack stack = new ItemStack(StellarRegistry.REMOTE);
            if (!player.inventory.addItemStackToInventory(stack)) player.dropItem(stack, false);
        } else if (args.length == 1 && "cancel".equals(args[0])) {
            LaunchController.INSTANCE.cancel(sender.getEntityWorld());
            sender.sendMessage(new TextComponentString("Beyond The Space launches canceled in this dimension."));
        } else if (args.length == 1 && "status".equals(args[0])) {
            sender.sendMessage(new TextComponentString(LaunchController.INSTANCE.status()));
        } else throw new CommandException(getUsage(sender));
    }

    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, Arrays.asList("give", "status", "cancel"));
        return Collections.emptyList();
    }
}
