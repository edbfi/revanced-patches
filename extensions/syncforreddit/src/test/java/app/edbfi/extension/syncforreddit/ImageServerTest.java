// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.json.JSONException;
import org.junit.Test;

public class ImageServerTest {
    private static final String ALBUM = "https://ap.syncforreddit.com/image?url=imgur-album-";

    private static Imgur.Api answering(int status, String body, List<String> asked) {
        return (method, path, requestBody, contentType) -> {
            asked.add(path);
            return new Http.Reply(status, body.getBytes(StandardCharsets.UTF_8), new HashMap<>());
        };
    }

    private static IOException failure(String url, boolean imgur, Imgur.Api api) {
        try {
            ImageServer.thumbnailUrl(url, imgur, api);
        } catch (IOException expected) {
            return expected;
        }
        fail("Expected an IOException for " + url);
        return null;
    }

    @Test
    public void loadsAnAlbumPreviewFromImgur() throws IOException {
        List<String> asked = new ArrayList<>();
        Imgur.Api api = answering(200, "{\"data\":{\"cover\":\"CoVeR12\"}}", asked);
        assertEquals("https://i.imgur.com/CoVeR12m.jpg", ImageServer.thumbnailUrl(ALBUM + "SrvAlb1", true, api));
        assertEquals(1, asked.size());
    }

    @Test
    public void failsOtherPreviewsWithoutAsking() {
        List<String> asked = new ArrayList<>();
        Imgur.Api api = answering(200, "{}", asked);
        failure("https://ap.syncforreddit.com/image?url=https://tenor.com/view/x-gif-1", true, api);
        failure("https://ap.syncforreddit.com/image?url=gfycat-x", true, api);
        assertTrue(asked.isEmpty());
    }

    @Test
    public void failsAlbumPreviewsWithoutFixImgur() {
        List<String> asked = new ArrayList<>();
        failure(ALBUM + "SrvAlb2", false, answering(200, "{\"data\":{\"cover\":\"CoVeR12\"}}", asked));
        assertTrue(asked.isEmpty());
    }

    @Test
    public void turnsEveryImgurFailureIntoAnIoException() {
        List<String> asked = new ArrayList<>();
        // A malformed answer would throw JSONException, a bad ID IllegalArgumentException, a missing album
        // IllegalStateException: any of them escaping the interceptor would crash the app.
        assertTrue(failure(ALBUM + "SrvAlb3", true, answering(200, "not json", asked)).getCause() instanceof JSONException);
        assertTrue(failure(ALBUM + "../x", true, answering(200, "{}", asked)).getCause() instanceof IllegalArgumentException);
        assertTrue(failure(ALBUM + "SrvAlb4", true, answering(404, "", asked)).getCause() instanceof IllegalStateException);
        assertTrue(failure(ALBUM + "SrvAlb5", true, (m, p, b, c) -> {
            throw new IOException("offline");
        }).getCause() instanceof IOException);
    }
}
