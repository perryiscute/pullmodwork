package com.example.pullmod;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconstructed from the compiled 4.0.0 jar (no original source was provided) and
 * updated to build against Minecraft 1.21.11 / Fabric API 0.141.x.
 *
 * Behaviour, reconstructed from the bytecode:
 *  - Listens to incoming chat lines shaped like "<name> whispers: <text>" or
 *    "<name> whispers to you: <text>" (a /msg / /w whisper).
 *  - If the message body contains the configured trigger word (default "pull"),
 *    and, if set, comes from the configured sender, it holds the "use item" key
 *    for a couple of ticks - i.e. simulates a right-click.
 *  - /pullword <word>   - sets the trigger word
 *  - /pullfrom <player> - restrict to whispers from one player (no arg = anyone)
 *  - /pulltoggle        - enable/disable the mod
 *  - /pullstatus        - print current settings
 */
public class PullMod implements ClientModInitializer {

    // How many client ticks to hold the "use" key down for when triggered.
    private static final int USE_HOLD_TICKS = 2;

    private static final Pattern WHISPER_PATTERN =
            Pattern.compile("^(.+?) whispers(?: to you)?: (.+)$", Pattern.CASE_INSENSITIVE);

    private volatile boolean enabled = true;
    private volatile String triggerWord = "pull";
    private volatile String fromPlayer = ""; // empty = react to whispers from anyone

    private final AtomicInteger pendingUseTicks = new AtomicInteger(0);

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientReceiveMessageEvents.ALLOW_GAME.register(this::allowReceiveGameMessage);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            dispatcher.register(ClientCommandManager.literal("pullword")
                    .then(ClientCommandManager.argument("wort", StringArgumentType.word())
                            .executes(ctx -> {
                                triggerWord = StringArgumentType.getString(ctx, "wort").toLowerCase();
                                sendFeedback("Triggerwort gesetzt auf: " + triggerWord);
                                return 1;
                            })));

            dispatcher.register(ClientCommandManager.literal("pullfrom")
                    .then(ClientCommandManager.argument("spieler", StringArgumentType.word())
                            .executes(ctx -> {
                                fromPlayer = StringArgumentType.getString(ctx, "spieler");
                                sendFeedback("Reagiere nur noch auf private Nachrichten von: " + fromPlayer);
                                return 1;
                            }))
                    .executes(ctx -> {
                        fromPlayer = "";
                        sendFeedback("Reagiere jetzt auf private Nachrichten von jedem.");
                        return 1;
                    }));

            dispatcher.register(ClientCommandManager.literal("pulltoggle")
                    .executes(ctx -> {
                        enabled = !enabled;
                        sendFeedback(enabled ? "Mod aktiviert." : "Mod deaktiviert.");
                        return 1;
                    }));

            dispatcher.register(ClientCommandManager.literal("pullstatus")
                    .executes(ctx -> {
                        String from = fromPlayer.isEmpty() ? "jedem" : fromPlayer;
                        sendFeedback("Status: " + (enabled ? "aktiviert" : "deaktiviert")
                                + " | Wort: " + triggerWord
                                + " | Von: " + from
                                + " | Nur private Nachrichten (/msg)");
                        return 1;
                    }));
        });
    }

    private boolean allowReceiveGameMessage(Text message, boolean overlay) {
        checkWhisper(message.getString());
        return true; // never hide the message, we only observe it
    }

    private void checkWhisper(String rawText) {
        String text = rawText.trim();
        Matcher matcher = WHISPER_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return;
        }

        String sender = matcher.group(1);
        String content = matcher.group(2).toLowerCase();

        boolean wordMatches = !triggerWord.isEmpty() && content.contains(triggerWord.toLowerCase());
        boolean senderMatches = fromPlayer.isEmpty() || sender.equalsIgnoreCase(fromPlayer);

        if (enabled && wordMatches && senderMatches) {
            pendingUseTicks.set(USE_HOLD_TICKS);
        }
    }

    private void onEndTick(MinecraftClient client) {
        if (pendingUseTicks.get() > 0) {
            client.options.useKey.setPressed(true);
            pendingUseTicks.decrementAndGet();
        } else {
            client.options.useKey.setPressed(false);
        }
    }

    private void sendFeedback(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("[PullMod] " + message), false);
            }
        });
    }
}
