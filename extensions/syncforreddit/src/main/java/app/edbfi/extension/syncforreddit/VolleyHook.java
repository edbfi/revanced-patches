// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;

/**
 * Runs first in Volley's {@code BasicNetwork.performRequest}, on Volley's network threads. A handler a patch turned
 * on answers the requests it owns, failures included; for every other request it returns null and the app sends its
 * own request as before.
 */
public final class VolleyHook {
    static final String TAG = "edbfi";

    private VolleyHook() {}

    public static NetworkResponse perform(Request<?> request) {
        String url = request.getUrl();
        if (Redgifs.isEnabled() && Redgifs.handles(url)) return Redgifs.answer(url);
        return null;
    }

    static NetworkResponse response(Http.Reply reply) {
        return new NetworkResponse(reply.status, reply.body, reply.headers, false, 0);
    }
}
