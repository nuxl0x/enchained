package nux.enchained.client.skin;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lazy-downloading skin cache that hits Mojang's session server by UUID and
 * resolves the actual skin PNG URL from the "textures" property.
 *
 * Flow:
 *   1) https://sessionserver.mojang.com/session/minecraft/profile/<uuid>
 *   2) JSON -> properties[0].value (base64)
 *   3) base64 JSON -> textures.SKIN.url
 *   4) Download that PNG and register as a dynamic texture.
 *
 * While a skin is loading or if anything fails, we fall back to DefaultSkinHelper.
 */
public final class EnchainedPlayerSkins {

    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();

    // Small daemon pool for HTTP downloads to avoid blocking render thread
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Enchained-Skin-Downloader");
        t.setDaemon(true);
        return t;
    });

    private EnchainedPlayerSkins() {}

    /**
     * Returns a texture Identifier for the player's skin.
     * If not downloaded yet, schedules an async download and returns default skin for now.
     */
    public static Identifier getOrRequestFace(GameProfile profile) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            UUID id = profile.getId() != null ? profile.getId() : UUID.randomUUID();
            return DefaultSkinHelper.getTexture(id);
        }

        UUID uuid = profile.getId();
        String name = profile.getName();

        // Prefer UUID as the key (more stable); fallback to name if needed
        String key;
        if (uuid != null) {
            key = "uuid:" + uuid.toString();
        } else if (name != null) {
            key = "name:" + name.toLowerCase();
        } else {
            UUID random = UUID.randomUUID();
            return DefaultSkinHelper.getTexture(random);
        }

        Identifier cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        // Not cached yet -> start async download once
        IN_FLIGHT.computeIfAbsent(key, k -> {
            EXECUTOR.submit(() -> downloadAndRegisterSkin(key, profile));
            return Boolean.TRUE;
        });

        // While loading or if it fails, just show default Steve/Alex
        UUID defId = uuid != null ? uuid : UUID.randomUUID();
        return DefaultSkinHelper.getTexture(defId);
    }

    /**
     * Worker method: calls session server, parses textures property, downloads PNG,
     * and registers a dynamic texture.
     */
    private static void downloadAndRegisterSkin(String key, GameProfile profile) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            IN_FLIGHT.remove(key);
            return;
        }

        UUID uuid = profile.getId();
        if (uuid == null) {
            IN_FLIGHT.remove(key);
            return;
        }

        HttpURLConnection conn = null;
        InputStream in = null;

        try {
            String uuidNoDashes = uuid.toString().replace("-", "");
            String profileUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidNoDashes;

            URL url = new URL(profileUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return;
            }

            in = conn.getInputStream();
            String jsonText = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            String skinUrl = extractSkinUrlFromSessionJson(jsonText);
            if (skinUrl == null || skinUrl.isEmpty()) {
                return;
            }

            // Now download the actual PNG from skinUrl (still on worker thread)
            NativeImage image = downloadNativeImage(skinUrl);
            if (image == null) {
                return;
            }

            // *** IMPORTANT PART: hop back to client thread to register the texture ***
            client.execute(() -> {
                // double-check client still alive
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null) return;

                // Build a safe dynamic texture name: only [a-z0-9/._-]
                // Use UUID without dashes so it's compact and path-safe.
                String uuidNoDashesInner = uuid.toString().replace("-", "");
                String texName = "enchained/skin_" + uuidNoDashesInner.toLowerCase();

                NativeImageBackedTexture tex = new NativeImageBackedTexture(image);
                Identifier id = mc.getTextureManager().registerDynamicTexture(texName, tex);

                CACHE.put(key, id);
            });

        } catch (Exception e) {
            // ignore; fallback to default skin in getOrRequestFace
        } finally {
            IN_FLIGHT.remove(key);
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Parses the sessionserver JSON and returns the textures.SKIN.url string,
     * or null if not found.
     */
    private static String extractSkinUrlFromSessionJson(String jsonText) {
        JsonElement rootEl = JsonParser.parseString(jsonText);
        if (!rootEl.isJsonObject()) return null;

        JsonObject root = rootEl.getAsJsonObject();
        JsonArray props = root.getAsJsonArray("properties");
        if (props == null || props.size() == 0) return null;

        for (JsonElement el : props) {
            if (!el.isJsonObject()) continue;
            JsonObject prop = el.getAsJsonObject();
            String name = prop.has("name") ? prop.get("name").getAsString() : null;
            if (!"textures".equals(name)) continue;

            String value = prop.has("value") ? prop.get("value").getAsString() : null;
            if (value == null || value.isEmpty()) continue;

            // value is a base64-encoded JSON blob
            byte[] decoded = Base64.getDecoder().decode(value);
            String texJsonText = new String(decoded, StandardCharsets.UTF_8);

            JsonElement texRootEl = JsonParser.parseString(texJsonText);
            if (!texRootEl.isJsonObject()) continue;

            JsonObject texRoot = texRootEl.getAsJsonObject();
            JsonObject textures = texRoot.getAsJsonObject("textures");
            if (textures == null) continue;

            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null) continue;

            if (skin.has("url")) {
                return skin.get("url").getAsString();
            }
        }

        return null;
    }

    /**
     * Downloads a PNG from the given URL into a NativeImage.
     */
    private static NativeImage downloadNativeImage(String urlStr) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return null;
            }

            in = conn.getInputStream();
            return NativeImage.read(in);
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}