// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RedgifsTest {
    @Test
    public void readsTheGifIdFromTheAppsUrl() {
        assertEquals("trainedmodestseagull",
                Redgifs.gifId("https://api.redgifs.com/v2/gifs/trainedmodestseagull?user-addr=1.2.3.4&user-agent=x"));
        assertEquals("abc", Redgifs.gifId("https://api.redgifs.com/v2/gifs/abc"));
    }

    @Test
    public void rejectsOtherPaths() {
        assertNull(Redgifs.gifId("https://api.redgifs.com/v2/gifs/"));
        assertNull(Redgifs.gifId("https://api.redgifs.com/v2/gifs/../auth?x"));
        assertNull(Redgifs.gifId("https://api.redgifs.com/info"));
    }
}
