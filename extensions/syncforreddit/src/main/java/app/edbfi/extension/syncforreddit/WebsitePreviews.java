// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.extension.syncforreddit;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Website previews, a Sync Ultra feature: the image a page names for link previews ({@code og:image}), which the app
 * loaded from its developer's image server ({@link ImageServer}). The phone now reads it from the page itself.
 */
public final class WebsitePreviews {
    /** How much of a page to read, at most: the preview image is named in its head. */
    static final int MAX_HEAD_BYTES = 256 * 1024;
    /** How long reading a page may take in all, on top of the client's per-read timeout. */
    static final long PAGE_DEADLINE_MS = 10_000;

    // The meta tags that name a page's preview image, best first.
    private static final String[] IMAGE_TAGS = {"og:image", "og:image:secure_url", "og:image:url", "twitter:image", "twitter:image:src"};
    private static final Pattern META = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9A-Fa-f]{1,6}|[A-Za-z]{2,6});");
    private static final Map<String, String> NAMED = new HashMap<>();
    private static final Pattern IPV4 = Pattern.compile("[0-9]{1,3}(\\.[0-9]{1,3}){3}");

    static {
        NAMED.put("amp", "&");
        NAMED.put("lt", "<");
        NAMED.put("gt", ">");
        NAMED.put("quot", "\"");
        NAMED.put("apos", "'");
        NAMED.put("nbsp", " ");
    }

    /** The head of a page as fetched, after redirects. */
    static final class Page {
        final String url;
        final String contentType;
        final byte[] head;

        Page(String url, String contentType, byte[] head) {
            this.url = url;
            this.contentType = contentType;
            this.head = head;
        }
    }

    /** Fetches the head of a page, refusing private addresses; the image client in the app, a fake in tests. */
    interface Pages {
        Page fetch(String link) throws IOException;
    }

    /** Resolves a host name; the image client's own DNS in the app, so its DNS-over-HTTPS setting holds. */
    interface Lookup {
        List<InetAddress> lookup(String host) throws IOException;
    }

    /** A request this refuses: an answer, which the failure cache remembers, unlike a lost connection. */
    static final class Refused extends IOException {
        Refused(String why) {
            super(why);
        }
    }

    private WebsitePreviews() {}

    /** Turned on by the "Enable Sync Ultra" patch. */
    public static boolean isEnabled() {
        return false;
    }

    /** Whether the link is a website preview's: an https link that isn't Tenor's (Fix Tenor previews handles those). */
    static boolean isWebsite(String link) {
        return handles(link) && !Tenor.handles(link);
    }

    /** Whether the link is one whose page this fetches: https only, as the app's own check requires. */
    static boolean handles(String link) {
        URI uri = uri(link);
        return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getRawUserInfo() == null;
    }

    /**
     * The https image the page at the link names for previews. Throws an IOException when the page can't be read, and
     * another exception when it was read and has none.
     */
    static String image(String link, Pages pages) throws IOException {
        if (!handles(link)) throw new IllegalArgumentException("Not an https link: " + link);
        Page page = pages.fetch(link);
        if (!isHtml(page.contentType)) throw new IllegalStateException("Not a web page (" + page.contentType + "): " + link);
        String image = imageIn(new String(page.head, StandardCharsets.UTF_8), page.url);
        if (image == null) throw new IllegalStateException("No preview image on " + link);
        return image;
    }

    static boolean isHtml(String contentType) {
        if (contentType == null) return false;
        String type = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return type.equals("text/html") || type.equals("application/xhtml+xml");
    }

    /** The preview image the page's meta tags name, resolved against the page's URL and made https, or null. */
    static String imageIn(String html, String pageUrl) {
        Map<String, String> found = new HashMap<>();
        Matcher meta = META.matcher(html);
        while (meta.find()) {
            String key = null;
            String content = null;
            Matcher attribute = ATTRIBUTE.matcher(meta.group());
            while (attribute.find()) {
                String name = attribute.group(1).toLowerCase(Locale.ROOT);
                String value = attribute.group(2) != null ? attribute.group(2)
                        : attribute.group(3) != null ? attribute.group(3) : attribute.group(4);
                if (name.equals("property") || name.equals("name")) {
                    if (key == null) key = value.trim().toLowerCase(Locale.ROOT);
                } else if (name.equals("content")) {
                    content = value;
                }
            }
            // A page may name several images: the first of each kind is the main one.
            if (key != null && content != null && !content.trim().isEmpty() && !found.containsKey(key)) {
                found.put(key, content);
            }
        }
        for (String tag : IMAGE_TAGS) {
            String image = absolute(unescape(found.get(tag)), pageUrl);
            if (image != null) return image;
        }
        return null;
    }

    /** The URL resolved against the page's, with http made https, or null unless that is an https URL with a host. */
    static String absolute(String url, String pageUrl) {
        if (url == null) return null;
        URI base = uri(pageUrl);
        URI resolved = uri(url.trim().replace(" ", "%20"));
        if (base == null || resolved == null) return null;
        try {
            resolved = base.resolve(resolved);
        } catch (IllegalArgumentException error) {
            return null;
        }
        String scheme = resolved.getScheme() == null ? "" : resolved.getScheme().toLowerCase(Locale.ROOT);
        if (resolved.getHost() == null || resolved.getRawUserInfo() != null) return null;
        String rest = resolved.toString().substring(scheme.length());
        // Browsers load an http image on an https page over https too.
        if (scheme.equals("http") || scheme.equals("https")) return "https" + rest;
        return null;
    }

    /** The text with HTML character references replaced by the characters they stand for. */
    static String unescape(String text) {
        if (text == null || text.indexOf('&') < 0) return text;
        Matcher entity = ENTITY.matcher(text);
        StringBuffer out = new StringBuffer();
        while (entity.find()) {
            String name = entity.group(1);
            String replacement;
            if (name.charAt(0) == '#') {
                boolean hex = name.length() > 1 && (name.charAt(1) == 'x' || name.charAt(1) == 'X');
                int code = Integer.parseInt(name.substring(hex ? 2 : 1), hex ? 16 : 10);
                replacement = Character.isValidCodePoint(code) ? new String(Character.toChars(code)) : entity.group();
            } else {
                replacement = NAMED.get(name.toLowerCase(Locale.ROOT));
                if (replacement == null) replacement = entity.group();
            }
            entity.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        entity.appendTail(out);
        return out.toString();
    }

    /**
     * Reads the page up to its {@code </head>}, at most {@link #MAX_HEAD_BYTES}, and gives up once the deadline passes.
     */
    static byte[] readHead(InputStream in, int maxBytes, long deadlineMs) throws IOException {
        byte[] head = new byte[maxBytes];
        int size = 0;
        while (size < maxBytes) {
            if (System.currentTimeMillis() > deadlineMs) throw new IOException("The page took too long to load");
            int read = in.read(head, size, maxBytes - size);
            if (read == -1) break;
            int from = Math.max(0, size - 6);
            size += read;
            if (indexOfHeadEnd(head, from, size) >= 0) break;
        }
        return Arrays.copyOf(head, size);
    }

    private static int indexOfHeadEnd(byte[] bytes, int from, int size) {
        byte[] end = {'<', '/', 'h', 'e', 'a', 'd'};
        outer:
        for (int i = from; i <= size - end.length; i++) {
            for (int j = 0; j < end.length; j++) {
                int b = bytes[i + j];
                if (b >= 'A' && b <= 'Z') b += 'a' - 'A';
                if (b != end[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    /**
     * Whether the address is on the public internet. Previews must never make the phone send a request to itself, its
     * network or its carrier's: loopback, private, link-local, CGNAT, unique local, multicast and reserved addresses.
     */
    static boolean isPublic(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) return isPublicIpv4(bytes, 0);
        if (!(address instanceof Inet6Address) || bytes.length != 16) return false;
        int first = bytes[0] & 0xff;
        // Unique local fc00::/7.
        if ((first & 0xfe) == 0xfc) return false;
        // IPv4-compatible ::a.b.c.d and IPv4-mapped ::ffff:a.b.c.d (Java turns most of these into Inet4Address).
        if (allZero(bytes, 0, 10) && ((bytes[10] == 0 && bytes[11] == 0) || (bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff))) {
            return isPublicIpv4(bytes, 12);
        }
        // NAT64 64:ff9b::/96 and 6to4 2002::/16 lead to the IPv4 address they hold.
        if (bytes[0] == 0 && bytes[1] == 0x64 && bytes[2] == (byte) 0xff && bytes[3] == (byte) 0x9b && allZero(bytes, 4, 12)) {
            return isPublicIpv4(bytes, 12);
        }
        if (bytes[0] == 0x20 && bytes[1] == 0x02) return isPublicIpv4(bytes, 2);
        return true;
    }

    /**
     * Refuses a host, before anything connects to it, unless every address it has is public. `host` is canonical, as
     * OkHttp gives it: an address is never looked up, so a name can't stand for one.
     */
    static void checkHost(String host, Lookup lookup) throws IOException {
        if (host == null || host.isEmpty()) throw new Refused("No host");
        List<InetAddress> addresses;
        if (host.indexOf(':') >= 0 || IPV4.matcher(host).matches()) {
            addresses = Collections.singletonList(InetAddress.getByName(host));
        } else {
            addresses = lookup.lookup(host);
        }
        if (addresses == null || addresses.isEmpty()) throw new UnknownHostException(host);
        for (InetAddress address : addresses) {
            if (!isPublic(address)) throw new Refused("Not a public address (" + address + "): " + host);
        }
    }

    private static boolean isPublicIpv4(byte[] bytes, int at) {
        int a = bytes[at] & 0xff;
        int b = bytes[at + 1] & 0xff;
        int c = bytes[at + 2] & 0xff;
        return !(a == 0 || a == 10 || a == 127 || a >= 224 // this network, private, loopback, multicast and reserved
                || (a == 100 && b >= 64 && b <= 127) // CGNAT
                || (a == 169 && b == 254) // link-local
                || (a == 172 && b >= 16 && b <= 31) // private
                || (a == 192 && b == 168) // private
                || (a == 192 && b == 0 && c == 0) // IETF protocol assignments
                || (a == 198 && (b == 18 || b == 19))); // benchmarking
    }

    private static boolean allZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) return false;
        }
        return true;
    }

    private static URI uri(String link) {
        if (link == null) return null;
        try {
            return new URI(link);
        } catch (Exception error) {
            return null;
        }
    }
}
