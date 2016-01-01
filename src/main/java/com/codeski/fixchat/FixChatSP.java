package com.codeski.fixchat;

import java.util.ArrayList;
import java.util.HashMap;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.command.SendCommandEvent;
import org.spongepowered.api.event.entity.DamageEntityEvent;
import org.spongepowered.api.event.entity.DisplaceEntityEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.message.MessageChannelEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Scheduler;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.text.format.TextColors;

import com.codeski.fixchat.FixChat.Strings;

@Plugin(id = "FixChat", name = "FixChat", version = "@version@")
public class FixChatSP {
    private final ArrayList<Player> away = new ArrayList<Player>();
    private final MessageChannel broadcast = Sponge.getGame().getServer().getBroadcastChannel();
    private final HashMap<Player, Long> idle = new HashMap<Player, Long>();
    private final HashMap<Player, Long> knockback = new HashMap<Player, Long>();
    private final HashMap<Player, Player> reply = new HashMap<Player, Player>();
    private final Scheduler scheduler = Sponge.getGame().getScheduler();
    private final Task.Builder taskBuilder = scheduler.createTaskBuilder();

    @Listener
    public void onClientConnectDisconnect(ClientConnectionEvent.Disconnect event) {
        idle.remove(event.getTargetEntity());
        away.remove(event.getTargetEntity());
    }

    @Listener
    public void onClientConnectionJoin(final ClientConnectionEvent.Join event) {
        idle.put(event.getTargetEntity(), System.currentTimeMillis());
        taskBuilder.delayTicks(1).intervalTicks(0).execute(new Runnable() {
            @Override
            public void run() {
                if (event.getTargetEntity().hasPermission("minecraft.command.list"))
                    Sponge.getCommandManager().process(event.getTargetEntity(), "list");
                // TODO: Send them the message of the day.
            }
        }).submit(this);
    }

    @Listener
    public void onDamageEntity(DamageEntityEvent event) {
        if (event.getTargetEntity() instanceof Player)
            knockback.put((Player) event.getTargetEntity(), System.currentTimeMillis());
    }

    @Listener
    public void onDisplaceEntityMoveTargetPlayer(DisplaceEntityEvent.Move.TargetPlayer event) {
        idle.put(event.getTargetEntity(), System.currentTimeMillis());
        if (away.contains(event.getTargetEntity()) && (knockback.get(event.getTargetEntity()) == null || knockback.get(event.getTargetEntity()) < System.currentTimeMillis() - 3000)) {
            away.remove(event.getTargetEntity());
            broadcast.send(Text.builder(event.getTargetEntity().getName() + Strings.NOT_AWAY).color(TextColors.YELLOW).build());
        }
    }

    @Listener
    public void onGameStartedServer(GameStartedServerEvent event) {
        Sponge.getGame().getCommandManager().register(this, CommandSpec.builder()
                .description(Text.of("Send a private message to a player"))
                .permission("minecraft.command.tell")
                .arguments(
                        GenericArguments.onlyOne(GenericArguments.player(Text.of("player"))),
                        GenericArguments.remainingJoinedStrings(Text.of("message")))
                .executor(new CommandExecutor() {
                    @Override
                    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
                        Player to = args.<Player> getOne("player").get();
                        String message = args.<String> getOne("message").get();
                        Sponge.getCommandManager().process(src, "tell " + to.getName() + " " + message);
                        if (src instanceof Player) {
                            Player from = (Player) src;
                            reply.put(to, from);
                        }
                        return CommandResult.success();
                    }
                })
                .build(), "t", "whisper");
        Sponge.getGame().getCommandManager().register(this, CommandSpec.builder()
                .description(Text.of("Reply to the last player that sent you a private message"))
                .permission("minecraft.command.tell")
                .arguments(
                        GenericArguments.remainingJoinedStrings(Text.of("message")))
                .executor(new CommandExecutor() {
                    @Override
                    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
                        if (src instanceof Player) {
                            Player from = (Player) src;
                            Player to = reply.get(from);
                            if (to != null) {
                                String message = args.<String> getOne("message").get();
                                Sponge.getCommandManager().process(src, "tell " + to.getName() + " " + message);
                                reply.put(reply.get(from), from);
                            } else
                                src.sendMessage(Text.builder(Strings.NO_WHISPER_REPLY.toString()).color(TextColors.RED).build());
                        } else
                            src.sendMessage(Text.builder(Strings.NON_PLAYER_REPLY.toString()).color(TextColors.RED).build());
                        return CommandResult.success();
                    }
                })
                .build(), "r", "reply");
        Sponge.getGame().getCommandManager().register(this, CommandSpec.builder()
                .description(Text.of("Set the message of the day for the server."))
                .permission("fixchat.motd")
                .arguments(
                        GenericArguments.remainingJoinedStrings(Text.of("message")))
                .executor(new CommandExecutor() {
                    @Override
                    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
                        // TODO: Implement message of the day for Sponge servers.
                        src.sendMessage(Text.builder("Message of the day is not yet implemented for Sponge servers.").color(TextColors.RED).build());
                        return CommandResult.success();
                    }
                })
                .build(), "motd");
        taskBuilder.delayTicks(FixChat.INTERVAL).intervalTicks(FixChat.INTERVAL).execute(new Runnable() {
            @Override
            public void run() {
                for (Player p : Sponge.getGame().getServer().getOnlinePlayers())
                    if (idle.get(p) != null && !away.contains(p))
                        if (System.currentTimeMillis() - idle.get(p) > FixChat.AWAY) {
                            away.add(p);
                            broadcast.send(Text.builder(p.getName() + Strings.AWAY).color(TextColors.YELLOW).build());
                        }
            }
        }).submit(this);
    }

    @Listener
    public void onMessageChannelChat(MessageChannelEvent.Chat event) {
        Player p = event.getCause().<Player> get("Source", Player.class).get();
        idle.put(p, System.currentTimeMillis());
        if (away.contains(p)) {
            away.remove(p);
            broadcast.send(Text.builder(p.getName() + Strings.NOT_AWAY).color(TextColors.YELLOW).build());
        }
    }

    @Listener
    public void onSendCommand(SendCommandEvent event) {
        if ((event.getCommand().equalsIgnoreCase("msg") || event.getCommand().equalsIgnoreCase("tell") || event.getCommand().equalsIgnoreCase("w")) && event.getArguments().length() > 1) {
            Player from = event.getCause().<Player> get("Source", Player.class).get();
            String[] args = event.getArguments().split("\\s+");
            Player to = Sponge.getGame().getServer().getPlayer(args[0]).get();
            if (!from.equals(to))
                reply.put(to, from);
        }
    }
}
