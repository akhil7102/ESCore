package fun.endcore.escoins.update;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UpdateParserTest {

    @Test
    void testParseXenForoResourceUpdateWithUpdateId() {
        String html = """
                <!DOCTYPE html>
                <html>
                <body>
                  <div class="block-container">
                    <article class="message message--resourceUpdate">
                      <h2 class="resourceUpdate-title">
                        <a href="/resources/core-smp.125982/update?update=48921">CoreSMP v1.1 - World Generation Fixed</a>
                      </h2>
                      <div class="message-attribution">
                        <time class="u-dt" title="Sep 13, 2026 at 4:30 PM" datetime="2026-09-13T16:30:00+00:00">Sep 13, 2026</time>
                      </div>
                      <div class="bbWrapper">
                        Fixed custom world generation tree decay and improved chunk loading performance.
                      </div>
                    </article>
                  </div>
                </body>
                </html>
                """;

        Optional<UpdateInfo> opt = UpdateParser.parse(html, "https://builtbybit.com/resources/core-smp.125982/updates");
        assertTrue(opt.isPresent());

        UpdateInfo info = opt.get();
        assertEquals("update-48921", info.id());
        assertEquals("CoreSMP v1.1 - World Generation Fixed", info.title());
        assertEquals("Sep 13, 2026 at 4:30 PM", info.date());
        assertEquals("https://builtbybit.com/resources/core-smp.125982/update?update=48921", info.url());
        assertTrue(info.changelog().contains("tree decay"));
        assertNotNull(info.changelogHash());
        assertFalse(info.changelogHash().isBlank());
    }

    @Test
    void testParseWithTitleAndDateFallback() {
        String html = """
                <div>
                  <div class="resourceUpdate">
                    <h2 class="resourceUpdate-title">
                      <a href="/resources/core-smp.125982/updates">CoreSMP v1.2 - Combat Balance</a>
                    </h2>
                    <time>Oct 01, 2026</time>
                    <div class="message-body">Balanced weapons and armor stats.</div>
                  </div>
                </div>
                """;

        Optional<UpdateInfo> opt = UpdateParser.parse(html, "https://builtbybit.com/resources/core-smp.125982/updates");
        assertTrue(opt.isPresent());

        UpdateInfo info = opt.get();
        assertEquals("coresmp-v1-2-combat-balance_oct-01-2026", info.id());
        assertEquals("CoreSMP v1.2 - Combat Balance", info.title());
        assertEquals("Oct 01, 2026", info.date());
    }

    @Test
    void testParseMalformedAndEmptyHtml() {
        assertTrue(UpdateParser.parse(null, null).isEmpty());
        assertTrue(UpdateParser.parse("", "").isEmpty());
        assertTrue(UpdateParser.parse("   ", "https://builtbybit.com").isEmpty());
        assertTrue(UpdateParser.parse("<html><body><div>No update here</div></body></html>", "https://builtbybit.com").isEmpty());
    }

    @Test
    void testDetermineStableIdPriority() {
        // Priority 1: URL query update=ID
        String id1 = UpdateParser.determineStableId("https://builtbybit.com/resources/125/update?update=9999", "Title", "Date", "Changelog");
        assertEquals("update-9999", id1);

        // Priority 1: URL path /updates/ID
        String id1b = UpdateParser.determineStableId("https://builtbybit.com/resources/125/updates/7777", "Title", "Date", "Changelog");
        assertEquals("update-7777", id1b);

        // Priority 2: Title and Date slug
        String id2 = UpdateParser.determineStableId("https://builtbybit.com/resources/125/updates", "CoreSMP v1.3", "Nov 12, 2026", "Content");
        assertEquals("coresmp-v1-3_nov-12-2026", id2);

        // Priority 3: Changelog content hash
        String id3 = UpdateParser.determineStableId("", "", "", "Important security fix applied!");
        assertNotNull(id3);
        assertEquals(64, id3.length()); // SHA-256 hex string
    }
}
