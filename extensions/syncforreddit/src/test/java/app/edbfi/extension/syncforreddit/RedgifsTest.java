// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RedgifsTest {
    @Test
    public void readsTheGifIdFromTheAppsUrl() {
        assertEquals("trainedmodestseagull",
                Redgifs.gifId("https://api.redgifs.com/v2/gifs/trainedmodestseagull?user-addr=1.2.3.4&user-agent=x"));
        assertEquals("abc", Redgifs.gifId("https://api.redgifs.com/v2/gifs/abc"));
        assertEquals("bothprivatepigeon", Redgifs.gifId("https://api.redgifs.com/v2/gifs/BothPrivatePigeon?user-addr=x"));
    }

    @Test
    public void rejectsOtherPaths() {
        assertNull(Redgifs.gifId("https://api.redgifs.com/v2/gifs/"));
        assertNull(Redgifs.gifId("https://api.redgifs.com/v2/gifs/../auth?x"));
        assertNull(Redgifs.gifId("https://api.redgifs.com/info"));
    }

    @Test
    public void ownsOnlyTheAppsRedgifsRequests() {
        assertTrue(Redgifs.handles("https://api.redgifs.com/v2/oauth/client"));
        assertTrue(Redgifs.handles("https://api.redgifs.com/info"));
        assertTrue(Redgifs.handles("https://api.redgifs.com/v2/gifs/abc?user-addr=x"));
        assertFalse(Redgifs.handles("https://media.redgifs.com/Abc.mp4"));
        assertFalse(Redgifs.handles("https://oauth.reddit.com/r/pics"));
    }
}
