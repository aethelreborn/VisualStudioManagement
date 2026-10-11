package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.util.Json;
import io.github.aethelreborn.vsm.util.TextUtil;
import io.github.aethelreborn.vsm.util.TimeParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.Bukkit;

/**
 * Discord webhooks (spec 18.4). Sends are asynchronous with a hard timeout, mentions are stripped, raw IPs
 * are never included and the outbound rate is limited.
 */
public final class WebhookService {

    private static final long MIN_INTERVAL_MILLIS = 1_000L;
    private static final int MAX_COLOUR = 0xFFFFFF;

    private final VsmConfig config;
    private final Logger logger;
    private final HttpClient client;
    private volatile long lastSend;

    public WebhookService(VsmConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean enabled() {
        return config.discordEnabled() && config.discordUrl() != null && !config.discordUrl().isBlank();
    }

    /** Colour per punishment family, matching the spec's colour-coded embeds. */
    private int colourFor(PunishmentType type) {
        if (type == null) {
            return 0x95A5A6;
        }
        return switch (type) {
            case BAN, TEMP_BAN, IP_BAN, TEMP_IP_BAN, NAME_BAN -> 0xE74C3C;
            case MUTE, TEMP_MUTE, SHADOW_MUTE, IP_MUTE, TEMP_IP_MUTE -> 0xE67E22;
            case WARN, TEMP_WARN -> 0xF1C40F;
            case KICK -> 0x9B59B6;
        };
    }

    /** Sends a punishment embed when the event is enabled. */
    public void send(String event, Punishment punishment, String staffName) {
        if (!enabled() || !config.discordEvents().contains(event.toUpperCase(Locale.ROOT))) {
            return;
        }
        String reason = TextUtil.sanitiseForWebhook(punishment.reason());
        String duration = punishment.isPermanent()
                ? "Never"
                : TimeParser.formatRemaining(punishment.expiresAt(), System.currentTimeMillis());
        StringBuilder description = new StringBuilder();
        description.append("**Player:** ").append(safe(punishment.displayTarget())).append('\n');
        description.append("**Staff:** ").append(safe(staffName)).append('\n');
        description.append("**Reason:** ").append(safe(reason)).append('\n');
        description.append("**Expires:** ").append(safe(duration)).append('\n');
        description.append("**ID:** ").append(safe(punishment.id()));
        if (punishment.evidence() != null && !punishment.evidence().isBlank()) {
            description.append('\n').append("**Evidence:** ").append(safe(punishment.evidence()));
        }
        embed(event.replace('_', ' '), description.toString(), colourFor(punishment.type()));
    }

    public void sendRankChange(String target, String oldRank, String newRank, String actorName) {
        if (!enabled() || !config.discordEvents().contains("STAFF_RANK_CHANGE")) {
            return;
        }
        embed("Staff rank change",
                "**Player:** " + safe(target) + "\n**From:** " + safe(oldRank) + "\n**To:** " + safe(newRank)
                        + "\n**By:** " + safe(actorName),
                0x3498DB);
    }

    public void sendReport(String reporter, String target, String reason, long id) {
        if (!enabled() || !config.discordEvents().contains("REPORT_CREATED")) {
            return;
        }
        embed("New report",
                "**ID:** " + id + "\n**Reporter:** " + safe(reporter) + "\n**Target:** " + safe(target)
                        + "\n**Reason:** " + safe(reason),
                0xE67E22);
    }

    public void sendRateLimit(String staffName, String action) {
        if (!enabled() || !config.discordEvents().contains("RATE_LIMIT_HIT")) {
            return;
        }
        embed("Rate limit hit", "**Staff:** " + safe(staffName) + "\n**Action:** " + safe(action), 0xC0392B);
    }

    /** Posts an embed with a rate limit and a hard timeout. */
    public void embed(String title, String description, int colour) {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSend < MIN_INTERVAL_MILLIS) {
            try {
                Thread.sleep(MIN_INTERVAL_MILLIS - (now - lastSend));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        lastSend = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(io.github.aethelreborn.vsm.VsmRuntime.get(), () -> post(title, description, colour));
    }

    private void post(String title, String description, int colour) {
        try {
            String content = config.discordMentionRoleId() == null || config.discordMentionRoleId().isBlank()
                    ? "" : "<@&" + config.discordMentionRoleId() + ">";
            String payload = "{\"content\":" + Json.quote(content)
                    + ",\"embeds\":[{\"title\":" + Json.quote(TextUtil.sanitiseForWebhook(title))
                    + ",\"description\":" + Json.quote(TextUtil.sanitiseForWebhook(description))
                    + ",\"color\":" + (colour & MAX_COLOUR) + "}]}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.discordUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                logger.warning("Discord webhook returned HTTP " + response.statusCode());
            }
        } catch (IOException | InterruptedException ex) {
            logger.fine("Could not deliver a Discord webhook: " + ex.getMessage());
        } catch (RuntimeException ex) {
            logger.warning("Discord webhook failed: " + ex.getMessage());
        }
        if (Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
        }
    }

    private static String safe(String value) {
        return TextUtil.sanitiseForWebhook(value == null ? "unknown" : value);
    }
}