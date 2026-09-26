package fun.endcore.escoins.tags;

import java.util.*;

/**
 * In-memory representation of a player's owned tags and currently active tag.
 */
public class PlayerTagData {
    private final UUID uuid;
    private final Set<String> ownedTags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private String activeTag;

    public PlayerTagData(UUID uuid) {
        this.uuid = uuid;
    }

    public PlayerTagData(UUID uuid, Collection<String> tags, String activeTag) {
        this.uuid = uuid;
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && !tag.trim().isEmpty()) {
                    this.ownedTags.add(tag.trim().toUpperCase());
                }
            }
        }
        if (activeTag != null && !activeTag.trim().isEmpty()) {
            this.activeTag = activeTag.trim().toUpperCase();
        }
    }

    public UUID getUuid() {
        return uuid;
    }

    public synchronized Set<String> getOwnedTags() {
        return Collections.unmodifiableSet(new TreeSet<>(ownedTags));
    }

    public synchronized boolean hasTag(String tagId) {
        if (tagId == null) return false;
        return ownedTags.contains(tagId.trim().toUpperCase());
    }

    public synchronized void addTag(String tagId) {
        if (tagId != null && !tagId.trim().isEmpty()) {
            ownedTags.add(tagId.trim().toUpperCase());
        }
    }

    public synchronized void removeTag(String tagId) {
        if (tagId != null) {
            String upper = tagId.trim().toUpperCase();
            ownedTags.remove(upper);
            if (upper.equalsIgnoreCase(activeTag)) {
                activeTag = null;
            }
        }
    }

    public synchronized void clearTags() {
        ownedTags.clear();
        activeTag = null;
    }

    public synchronized String getActiveTag() {
        return activeTag;
    }

    public synchronized void setActiveTag(String activeTag) {
        if (activeTag == null || activeTag.trim().isEmpty()) {
            this.activeTag = null;
        } else {
            this.activeTag = activeTag.trim().toUpperCase();
        }
    }
}
