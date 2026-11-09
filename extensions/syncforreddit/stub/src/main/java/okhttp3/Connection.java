// SPDX-License-Identifier: AGPL-3.0-only
package okhttp3;

/** Compile-only stand-in for the OkHttp connection bundled in the app. */
public interface Connection {
    Route route();
}
