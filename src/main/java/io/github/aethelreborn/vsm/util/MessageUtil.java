package io.github.aethelreborn.vsm.util;

import io.github.aethelreborn.vsm.config.Messages;
import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Thin delivery layer around {@link Messages}: send, broadcast and staff notification helpers. */
public final class MessageUtil {

    private final Messages messages;

    public MessageUtil(Messages messages) {
        this.messages = messages;
    }

    public Messages messages() {
        return messages;
    }

    /** Sends a prefixed message with ordered key/value placeholders. */
    public void send(CommandSender to, String key, Object... placeholders) {
        if (to == null) {
            return;
        }
        to.sendMessage(messages.prefixed(key, Messages.placeholders(placeholders)));
    }

    /** Sends without the plugin prefix. */
    public void sendRaw(CommandSender to, String key, Object... placeholders) {
        if (to == null) {
            return;
        }
        to.sendMessage(messages.get(key, Messages.placeholders(placeholders)));
    }

    public void sendPrefix(Component component, CommandSender to) {
        if (to != null) {
            to.sendMessage(component);
        }
    }

    public Component component(String key, Object... placeholders) {
        return messages.prefixed(key, Messages.placeholders(placeholders));
    }

    public Component raw(String key, Object... placeholders) {
        return messages.get(key, Messages.placeholders(placeholders));
    }

    public Component screen(String key, Object... placeholders) {
        return messages.screen(key, Messages.placeholders(placeholders));
    }

    public Component screen(String key, Map<String, String> placeholders) {
        return messages.screen(key, placeholders);
    }

    /** Sends only to players, used for broadcasts. */
    public void broadcast(Iterable<? extends Player> players, Component component) {
        for (Player player : players) {
            player.sendMessage(component);
        }
    }

    /** Audience helper for plugins that talk to our {@code Audience}s. */
    public void send(Audience audience, Component component) {
        if (audience != null) {
            audience.sendMessage(component);
        }
    }
}