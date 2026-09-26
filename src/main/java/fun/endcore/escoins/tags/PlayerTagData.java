package fun.endcore.escoins.tags;

import fun.endcore.escoins.cosmetics.OwnershipType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory representation of a player's owned tags and currently active tag.
 * Thread-safe with automatic expiration checks for temporary tags.
 */
public class PlayerTagData {
    private final UUID uuid;
    private final Map<String, TagEntry> ownedTags = new ConcurrentHashMap<>();
    private String activeTag;

    public PlayerTagData(UUID uuid) {
        this.uuid = uuid;
    }

    public PlayerTagData(UUID uuid, Collection<String> tags, String activeTag) {
        this.uuid = uuid;
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && !tag.trim().isEmpty()) {
                    String clean = tag.trim().toUpperCase();
                    this.ownedTags.put(clean, new TagEntry(clean));
                }
            }
        }
        if (activeTag != null && !activeTag.trim().isEmpty()) {
            this.activeTag = activeTag.trim().toUpperCase();
        }
    }

    public PlayerTagData(UUID uuid, Map<String, TagEntry> tags, String activeTag) {
        this.uuid = uuid;
        if (tags != null) {
            for (Map.Entry<String, TagEntry> entry : tags.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isExpired()) {
                    this.ownedTags.put(entry.getKey().toUpperCase(), entry.getValue());
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

    /**
     * Gets all non-expired owned tag IDs.
     */
    public synchronized Set<String> getOwnedTags() {
        purgeExpired();
        return Collections.unmodifiableSet(new TreeSet<>(ownedTags.keySet()));
    }

    /**
     * Gets all non-expired TagEntry objects.
     */
    public synchronized Map<String, TagEntry> getOwnedTagEntries() {
        purgeExpired();
        return Collections.unmodifiableMap(new HashMap<>(ownedTags));
    }

    public synchronized TagEntry getTagEntry(String tagId) {
        if (tagId == null) return null;
        String upper = tagId.trim().toUpperCase();
        TagEntry entry = ownedTags.get(upper);
        if (entry != null && entry.isExpired()) {
            ownedTags.remove(upper);
            if (upper.equalsIgnoreCase(activeTag)) {
                activeTag = null;
            }
            return null;
        }
        return entry;
    }

    public synchronized boolean hasTag(String tagId) {
        return getTagEntry(tagId) != null;
    }

    public synchronized void addTag(String tagId) {
        if (tagId != null && !tagId.trim().isEmpty()) {
            String upper = tagId.trim().toUpperCase();
            ownedTags.put(upper, new TagEntry(upper));
        }
    }

    public synchronized void addTag(TagEntry entry) {
        if (entry != null && entry.tagId() != null && !entry.tagId().isEmpty()) {
            if (!entry.isExpired()) {
                ownedTags.put(entry.tagId().toUpperCase(), entry);
            }
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
        if (activeTag == null) return null;
        // Verify active tag has not expired
        if (!hasTag(activeTag)) {
            activeTag = null;
            return null;
        }
        return activeTag;
    }

    public synchronized void setActiveTag(String activeTag) {
        if (activeTag == null || activeTag.trim().isEmpty()) {
            this.activeTag = null;
        } else {
            String upper = activeTag.trim().toUpperCase();
            if (hasTag(upper)) {
                this.activeTag = upper;
            } else {
                this.activeTag = null;
            }
        }
    }

    private void purgeExpired() {
        Iterator<Map.Entry<String, TagEntry>> it = ownedTags.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TagEntry> e = it.next();
            if (e.getValue().isExpired()) {
                if (e.getKey().equalsIgnoreCase(activeTag)) {
                    activeTag = null;
                }
                it.remove();
            }
        }
    }
}
