package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.render.items.core.Image;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Background fetch of a remote (http/https) Image source. A daemon thread fills
// Image.fetchedBytes + fetchDone; the render thread decodes the bytes, so Skija stays
// single-threaded and the render loop never blocks on the network.
final class ImageLoader {

    private ImageLoader() {}

    static boolean isRemote(String src) {
        return src.startsWith("http://") || src.startsWith("https://");
    }

    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "qml4j-image-fetch");
        t.setDaemon(true);
        return t;
    });

    static void fetch(Image node, String url) {
        POOL.submit(() -> {
            byte[] data;
            try {
                data = get(url, 5);
            } catch (Throwable ignore) {
                data = null;
            }
            node.fetchedBytes = data;
            node.fetchDone = true;
        });
    }

    // Honour the standard HTTPS_PROXY/HTTP_PROXY env vars (curl/wget convention) -- the
    // JVM ignores them by default, so a remote image would just time out behind a proxy.
    private static final Proxy PROXY = detectProxy();

    private static Proxy detectProxy() {
        String p = env("HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy");
        if (p == null) return Proxy.NO_PROXY;
        try {
            URL u = new URL(p);
            int port = u.getPort() < 0 ? 8080 : u.getPort();
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(u.getHost(), port));
        } catch (Exception e) {
            return Proxy.NO_PROXY;
        }
    }

    private static String env(String... names) {
        for (String n : names) {
            String v = System.getenv(n);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    // HttpURLConnection only auto-follows same-scheme redirects; GitHub raw -> CDN often
    // crosses http<->https, so follow Location manually with a hop limit.
    private static byte[] get(String url, int redirects) throws Exception {
        if (redirects < 0) return null;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection(PROXY);
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "qml4j");
        int code = conn.getResponseCode();
        if (code >= 300 && code < 400) {
            String loc = conn.getHeaderField("Location");
            return loc == null ? null : get(loc, redirects - 1);
        }
        if (code != 200) return null;
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
