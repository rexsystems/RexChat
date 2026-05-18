package me.rexsystems.rexChat.hooks.image;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parsed Minecraft block model — the data straight out of
 * {@code assets/minecraft/models/block/<name>.json} plus everything inherited
 * from its {@code parent} chain, with all {@code #variable} texture references
 * resolved to literal texture paths.
 *
 * <p>This is a deliberately small, hand-rolled subset of the full vanilla
 * model schema: {@code parent}, {@code textures}, {@code elements}
 * (with {@code from}/{@code to}/{@code faces}), and {@code display.gui}
 * (rotation / translation / scale). It's enough to render the vast majority
 * of full-cube and column blocks at GUI fidelity. Stairs / fences / doors
 * use this same schema with multiple elements; we render each element as a
 * box and composite them together.
 */
public final class BlockModel {

    /** A single rectangular box inside a block model. Multi-element models
     *  (stairs, slabs, fences, …) have several of these. */
    public static final class Element {
        public final double[] from;          // {x, y, z} in 1/16-block units (0..16)
        public final double[] to;            // {x, y, z} in 1/16-block units (0..16)
        public final Map<String, Face> faces; // direction key → face data
        public final double[] rotationOrigin; // optional element-local rotation
        public final String  rotationAxis;
        public final double  rotationAngle;

        public Element(double[] from, double[] to, Map<String, Face> faces,
                       double[] rotationOrigin, String rotationAxis, double rotationAngle) {
            this.from = from; this.to = to; this.faces = faces;
            this.rotationOrigin = rotationOrigin;
            this.rotationAxis = rotationAxis;
            this.rotationAngle = rotationAngle;
        }
    }

    /** A single textured face on an element. */
    public static final class Face {
        /** Resolved literal texture path (e.g. {@code "block/dirt"}). */
        public final String texture;
        /** UV rectangle into the texture in 0..16 units, or {@code null} = derived from {@code from}/{@code to}. */
        public final double[] uv;
        /** UV rotation 0/90/180/270. */
        public final int rotation;
        /** Tint index (>=0 means apply biome tint colour 0/1/2/…). */
        public final int tintIndex;

        public Face(String texture, double[] uv, int rotation, int tintIndex) {
            this.texture = texture;
            this.uv = uv;
            this.rotation = rotation;
            this.tintIndex = tintIndex;
        }
    }

    /** {@code display.gui} transform: rotation+translation+scale applied to the
     *  whole model when rendered into an inventory slot. Defaults to identity
     *  (no rotation, scale 1). The vanilla {@code block/block.json} sets
     *  {@code [30, 225, 0]} / {@code 0.625} which gets inherited by
     *  full-cube models; cross / handheld / item models keep this identity
     *  default and render face-on. */
    public static final class GuiTransform {
        public double[] rotation    = { 0, 0, 0};
        public double[] translation = { 0, 0, 0};
        public double[] scale       = { 1, 1, 1};
    }

    /** Texture variable map (e.g. {@code "all" -> "block/dirt"}, {@code "side" -> "block/oak_log"}). */
    public final Map<String, String> textures = new LinkedHashMap<>();
    public final List<Element> elements = new ArrayList<>();
    public final GuiTransform guiTransform = new GuiTransform();

    // ---------- parsing ----------

    /**
     * Recursively load the model with the given key (e.g. {@code "block/dirt"})
     * and merge with its parent chain. Returns {@code null} if the model can't
     * be downloaded.
     *
     * <p>Texture variables are NOT resolved here — call {@link #resolveTextureVars()}
     * after the full chain has been merged.
     */
    public static BlockModel load(String modelKey, ItemTextureCache textures) {
        return loadInternal(modelKey, textures, 0);
    }

    private static BlockModel loadInternal(String modelKey, ItemTextureCache textures, int depth) {
        if (depth > 10) return null;
        JsonObject obj = textures.getJson("models/" + modelKey + ".json");
        if (obj == null) return null;

        BlockModel model;
        if (obj.has("parent")) {
            String parentKey = stripNamespace(obj.get("parent").getAsString());
            BlockModel parent = loadInternal(parentKey, textures, depth + 1);
            model = parent != null ? parent : new BlockModel();
        } else {
            model = new BlockModel();
        }

        // Merge textures (children override parents).
        if (obj.has("textures")) {
            JsonObject t = obj.getAsJsonObject("textures");
            for (Map.Entry<String, JsonElement> e : t.entrySet()) {
                model.textures.put(e.getKey(), e.getValue().getAsString());
            }
        }

        // If this layer defines elements, REPLACE the inherited list (vanilla semantics).
        if (obj.has("elements")) {
            model.elements.clear();
            JsonArray arr = obj.getAsJsonArray("elements");
            for (JsonElement el : arr) {
                Element parsed = parseElement(el.getAsJsonObject());
                if (parsed != null) model.elements.add(parsed);
            }
        }

        // display.gui — the inventory display transform.
        if (obj.has("display") && obj.getAsJsonObject("display").has("gui")) {
            JsonObject gui = obj.getAsJsonObject("display").getAsJsonObject("gui");
            applyTransform(gui, model.guiTransform);
        }

        return model;
    }

    /**
     * Walk the {@link #textures} map and replace every {@code #variable}
     * indirection with its literal target. Must be called once after the full
     * parent chain is loaded.
     */
    public void resolveTextureVars() {
        Map<String, String> resolved = new HashMap<>();
        for (Map.Entry<String, String> e : textures.entrySet()) {
            resolved.put(e.getKey(), resolveVar(e.getValue(), 0));
        }
        textures.clear();
        textures.putAll(resolved);
    }

    /**
     * Resolve one face's texture reference (which may be {@code "#side"} or a
     * literal {@code "block/oak_log"}) to a literal path under
     * {@code assets/minecraft/textures/}.
     */
    public String resolveFaceTexture(String ref) {
        return resolveVar(ref, 0);
    }

    private String resolveVar(String value, int depth) {
        if (depth > 10) return value;
        if (value == null) return null;
        if (value.startsWith("#")) {
            String key = value.substring(1);
            String mapped = textures.get(key);
            if (mapped == null) return value;
            return resolveVar(mapped, depth + 1);
        }
        return stripNamespace(value);
    }

    // ---------- helpers ----------

    private static String stripNamespace(String s) {
        if (s == null) return null;
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }

    private static Element parseElement(JsonObject obj) {
        double[] from = jsonArr(obj, "from", new double[]{0, 0, 0});
        double[] to   = jsonArr(obj, "to",   new double[]{16, 16, 16});

        Map<String, Face> faces = new LinkedHashMap<>();
        if (obj.has("faces")) {
            JsonObject fObj = obj.getAsJsonObject("faces");
            for (Map.Entry<String, JsonElement> e : fObj.entrySet()) {
                Face face = parseFace(e.getValue().getAsJsonObject());
                if (face != null) faces.put(e.getKey().toLowerCase(Locale.ROOT), face);
            }
        }

        double[] rotOrigin = null;
        String   rotAxis = null;
        double   rotAngle = 0;
        if (obj.has("rotation")) {
            JsonObject rot = obj.getAsJsonObject("rotation");
            rotOrigin = jsonArr(rot, "origin", new double[]{8, 8, 8});
            rotAxis   = rot.has("axis")  ? rot.get("axis").getAsString().toLowerCase(Locale.ROOT) : "y";
            rotAngle  = rot.has("angle") ? rot.get("angle").getAsDouble() : 0;
        }

        return new Element(from, to, faces, rotOrigin, rotAxis, rotAngle);
    }

    private static Face parseFace(JsonObject obj) {
        if (!obj.has("texture")) return null;
        String tex = obj.get("texture").getAsString();
        double[] uv = obj.has("uv") ? jsonArr(obj, "uv", null) : null;
        int rot = obj.has("rotation") ? obj.get("rotation").getAsInt() : 0;
        int tint = obj.has("tintindex") ? obj.get("tintindex").getAsInt() : -1;
        return new Face(tex, uv, rot, tint);
    }

    private static void applyTransform(JsonObject obj, GuiTransform t) {
        if (obj.has("rotation"))    t.rotation    = jsonArr(obj, "rotation",    t.rotation);
        if (obj.has("translation")) t.translation = jsonArr(obj, "translation", t.translation);
        if (obj.has("scale"))       t.scale       = jsonArr(obj, "scale",       t.scale);
    }

    private static double[] jsonArr(JsonObject obj, String key, double[] fallback) {
        if (obj == null || !obj.has(key)) return fallback;
        JsonArray arr = obj.getAsJsonArray(key);
        double[] out = new double[arr.size()];
        for (int i = 0; i < arr.size(); i++) out[i] = arr.get(i).getAsDouble();
        return out;
    }

    /** Convenience parser for raw JSON strings — used by tests. */
    static BlockModel parseJsonString(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        BlockModel m = new BlockModel();
        if (obj.has("textures")) {
            for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("textures").entrySet()) {
                m.textures.put(e.getKey(), e.getValue().getAsString());
            }
        }
        if (obj.has("elements")) {
            for (JsonElement el : obj.getAsJsonArray("elements")) {
                Element p = parseElement(el.getAsJsonObject());
                if (p != null) m.elements.add(p);
            }
        }
        if (obj.has("display") && obj.getAsJsonObject("display").has("gui")) {
            applyTransform(obj.getAsJsonObject("display").getAsJsonObject("gui"), m.guiTransform);
        }
        return m;
    }
}
