// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class OtherDiscussionsTest {
    @Test
    public void searchesForTheEncodedLink() {
        assertEquals(
                "search######url:https%3A%2F%2Fwww.kyivpost.com%2Fpost%2F89012",
                OtherDiscussions.asSearch("seen###https%3A%2F%2Fwww.kyivpost.com%2Fpost%2F89012"));
    }

    @Test
    public void keepsTheLinksCaseAndEncoding() {
        assertEquals(
                "search######url:https%3A%2F%2Fi.imgur.com%2FAbC1234.png%3Fa%3D1%26b%3D2",
                OtherDiscussions.asSearch("Seen###https%3A%2F%2Fi.imgur.com%2FAbC1234.png%3Fa%3D1%26b%3D2"));
    }

    @Test
    public void leavesOtherListingsAlone() {
        assertEquals("frontpage", OtherDiscussions.asSearch("frontpage"));
        assertEquals("search###pics###cats", OtherDiscussions.asSearch("search###pics###cats"));
        assertEquals("r/seen###x", OtherDiscussions.asSearch("r/seen###x"));
        assertEquals("seen###", OtherDiscussions.asSearch("seen###"));
        assertNull(OtherDiscussions.asSearch(null));
    }
}
