// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import android.util.Log;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;

/**
 * Runs first in Volley's {@code BasicNetwork.performRequest}, on Volley's network threads. A handler a patch turned
 * on may answer a request itself; on null, or on any error, the app sends its own request as before.
 */
public final class VolleyHook {
    static final String TAG = "edbfi";

    private VolleyHook() {}

    public static NetworkResponse perform(Request<?> request) {
        try {
            String url = request.getUrl();
            if (Redgifs.isEnabled() && Redgifs.handles(url)) return Redgifs.perform(url);
        } catch (Throwable error) {
            Log.w(TAG, "Falling back to the app's request", error);
        }
        return null;
    }

    static NetworkResponse response(Http.Reply reply) {
        return new NetworkResponse(reply.status, reply.body, reply.headers, false, 0);
    }
}
