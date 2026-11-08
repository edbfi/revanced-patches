// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShareLinksTest {
    @Test
    public void matchesShareLinks() {
        assertTrue(ShareLinks.isShareLink("https://www.reddit.com/r/pics/s/AbC123xyz"));
        assertTrue(ShareLinks.isShareLink("https://reddit.com/r/pics/s/AbC123xyz/"));
        assertTrue(ShareLinks.isShareLink("http://m.reddit.com/u/someone/s/AbC123xyz?utm_source=share"));
        assertTrue(ShareLinks.isShareLink("https://WWW.REDDIT.COM/r/pics/s/AbC123xyz"));
    }

    @Test
    public void ignoresOtherLinks() {
        assertFalse(ShareLinks.isShareLink("https://www.reddit.com/r/pics/comments/abc123/title/"));
        assertFalse(ShareLinks.isShareLink("https://www.reddit.com/r/s/s/"));
        assertFalse(ShareLinks.isShareLink("https://example.com/r/pics/s/AbC123xyz"));
        assertFalse(ShareLinks.isShareLink("https://notreddit.com/r/pics/s/AbC123xyz"));
        assertFalse(ShareLinks.isShareLink("https://www.reddit.com/r/pics/s/AbC123xyz/extra"));
    }

    @Test
    public void dropsShareAndCampaignParameters() {
        assertEquals(
                "https://www.reddit.com/r/pics/comments/abc123/title/",
                ShareLinks.withoutTracking("https://www.reddit.com/r/pics/comments/abc123/title/"
                        + "?share_id=XyZ&utm_content=1&utm_medium=android_app&utm_name=androidcss&utm_source=share&utm_term=1"));
        assertEquals(
                "https://www.reddit.com/r/pics/comments/abc123/_/def456/?context=3#top",
                ShareLinks.withoutTracking("https://www.reddit.com/r/pics/comments/abc123/_/def456/?context=3&share_id=XyZ#top"));
        assertEquals("https://www.reddit.com/r/pics/", ShareLinks.withoutTracking("https://www.reddit.com/r/pics/"));
    }
}
