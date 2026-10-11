package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.PlayerNote;
import io.github.aethelreborn.vsm.storage.StorageProvider;
import io.github.aethelreborn.vsm.util.IdGenerator;
import io.github.aethelreborn.vsm.util.TextUtil;
import java.util.List;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Private staff notes (spec 8.3). */
public final class NoteService {

    private final StorageProvider storage;
    private final MessageService messages;
    private final AuditService audit;
    private final VsmConfigBridge limits;

    /** Small holder so this service does not need the whole configuration object. */
    public interface VsmConfigBridge {
        int maxTextLength();
    }

    public NoteService(StorageProvider storage, MessageService messages, AuditService audit, VsmConfigBridge limits) {
        this.storage = storage;
        this.messages = messages;
        this.audit = audit;
        this.limits = limits;
    }

    public PlayerNote add(CommandSender sender, UUID actorUuid, String actorName,
                          UUID targetUuid, String targetName, String text, boolean pinned) {
        String clean = TextUtil.sanitise(text, limits.maxTextLength());
        PlayerNote note = PlayerNote.of(targetUuid, targetName, actorUuid, actorName, clean);
        note.id(IdGenerator.generate("NT", 6));
        note.pinned(pinned);
        storage.saveNote(note);
        audit.log(ActionSource.COMMAND, actorUuid, actorName, "NOTE_ADD", targetUuid, targetName, note.id());
        return note;
    }

    public boolean remove(CommandSender sender, UUID actorUuid, String actorName, String id) {
        storage.deleteNote(id);
        audit.log(ActionSource.COMMAND, actorUuid, actorName, "NOTE_REMOVE", null, id, id);
        return true;
    }

    public List<PlayerNote> notes(UUID target) {
        return storage.notes(target).join();
    }

    public List<PlayerNote> pinned(UUID target) {
        return storage.pinnedNotes(target).join();
    }

    /** Shows pinned notes to online staff when a player joins (spec 9.5 join messages). */
    public void showPinnedNotes(Player joining) {
        List<PlayerNote> notes = pinned(joining.getUniqueId());
        if (notes.isEmpty()) {
            return;
        }
        for (Player staff : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (io.github.aethelreborn.vsm.VsmRuntime.get() == null) {
                return;
            }
            if (!io.github.aethelreborn.vsm.VsmRuntime.get().permissionService()
                    .has(staff, io.github.aethelreborn.vsm.config.PermissionNodes.NOTES_VIEW)) {
                continue;
            }
            messages.send(staff, "note-pinned-alert",
                    "player", joining.getName(),
                    "count", Integer.toString(notes.size()),
                    "text", notes.get(0).text());
        }
    }
}