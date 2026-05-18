package me.rexsystems.rexChat.hooks.image;

import org.bukkit.Material;

import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Composes 2:1 axonometric ("isometric") cube icons for block materials —
 * the same look Minecraft itself uses to render blocks inside inventory
 * slots.
 *
 * <p>The output of {@link #iso(Material)} is a 32×32 ARGB image where the
 * cube's three visible faces are projected onto the canvas via affine
 * transforms:
 *
 * <pre>
 *           TOP        ← (16, 0)
 *         ╱     ╲
 *  TL  ╱           ╲  TR
 *  (0,8)             (32,8)
 *      ╲           ╱
 *        ╲       ╱
 *          CENTER
 *         (16,16)
 *        ╱       ╲
 *      ╱           ╲
 *  (0,24)         (32,24)
 *  BL  ╲           ╱  BR
 *         ╲     ╱
 *           BOT      ← (16, 32)
 * </pre>
 *
 * <p>Side faces are darkened slightly to mimic Minecraft's GUI lighting,
 * which makes the cube read as 3D even at 16×16 source resolution.
 *
 * <p>Face textures are resolved by convention from
 * {@link ItemTextureCache#getRaw(String)}. We try common naming patterns
 * (single texture, {@code _top + _side}, log/wood patterns); blocks whose
 * faces aren't easily resolved fall back to a flat texture render and the
 * caller decides what to do.
 */
public final class BlockIconRenderer {

    /** Output canvas size in 1× pixels (each MC pixel becomes 2 here). */
    public static final int OUT_SIZE = 32;

    /** Sentinel used to cache "no iso possible" results so we don't retry. */
    private static final BufferedImage NONE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    // The iso cube is rendered into a 28×28 inner box centred inside the 32×32
    // canvas so blocks have a 2-pixel breathing margin in the inventory slot,
    // matching the GUI display matrix scale (~0.875) Minecraft applies to
    // blocks in vanilla. Affine coefficients = base coefficients × 0.875 plus
    // a (+2, +2) translate.
    private static final AffineTransform TOP_FACE   = new AffineTransform( 0.875, 0.4375, -0.875, 0.4375, 16.0, 2.0);
    private static final AffineTransform LEFT_FACE  = new AffineTransform( 0.875, 0.4375,  0.0,   0.875,  2.0, 9.0);
    private static final AffineTransform RIGHT_FACE = new AffineTransform(-0.875, 0.4375,  0.0,   0.875, 30.0, 9.0);

    // Side face shading matches MC's inventory GUI lighting.
    private static final float LEFT_SHADE  = 0.85f;
    private static final float RIGHT_SHADE = 0.7f;

    private final ItemTextureCache textures;
    private final BlockModelRenderer modelRenderer;
    private final ConcurrentMap<Material, BufferedImage> cache = new ConcurrentHashMap<>();
    private java.util.function.Consumer<String> debug = msg -> {};

    public BlockIconRenderer(ItemTextureCache textures) {
        this.textures = textures;
        this.modelRenderer = new BlockModelRenderer(textures);
    }

    /** Wire up a debug-log sink so this renderer can report which strategy hit. */
    public void setDebug(java.util.function.Consumer<String> sink) {
        this.debug = sink == null ? msg -> {} : sink;
        this.modelRenderer.setDebug(sink);
    }

    /**
     * Get the iso cube image for a block material, or {@code null} if
     * either the material is not a block or no face textures could be
     * resolved on the CDN. Result is cached per material.
     */
    public BufferedImage iso(Material mat) {
        if (mat == null || !mat.isBlock()) return null;
        BufferedImage cached = cache.get(mat);
        if (cached != null) return cached == NONE ? null : cached;

        // 1) Try the proper model-based renderer first: parse the block's
        //    model JSON (with parent inheritance + texture variable resolution)
        //    and render it at vanilla GUI angles (30° pitch, 225° yaw,
        //    0.625× scale) with per-face shading. This handles non-cube
        //    blocks (stairs, slabs) and matches what MC itself shows in
        //    the inventory.
        BufferedImage modelOut = renderViaModel(mat);
        if (modelOut != null) {
            cache.put(mat, modelOut);
            return modelOut;
        }

        // 2) Fallback: the simplified 2:1 axonometric projection. Less
        //    accurate but always works for any block whose textures we
        //    can resolve via name conventions.
        Faces faces = resolveFaces(mat);
        if (faces == null) {
            debug.accept("block-iso: " + mat + " — no face textures resolved, falling back");
            cache.put(mat, NONE);
            return null;
        }

        BufferedImage out = compose(faces);
        cache.put(mat, out);
        return out;
    }

    /**
     * Try to render the block via its full model JSON. Returns {@code null}
     * if the model can't be loaded (network failure, corrupt JSON) or has
     * no resolvable textures, in which case the caller falls back to the
     * iso compositor.
     */
    private BufferedImage renderViaModel(Material mat) {
        try {
            String key = "block/" + mat.name().toLowerCase(java.util.Locale.ROOT);
            BlockModel model = BlockModel.load(key, textures);
            if (model == null) {
                debug.accept("block-model: " + mat + " — JSON not found at " + key);
                return null;
            }
            model.resolveTextureVars();
            BufferedImage img = modelRenderer.render(model);
            if (img != null) debug.accept("block-model: " + mat + " rendered via JSON pipeline");
            return img;
        } catch (Throwable t) {
            debug.accept("block-model: " + mat + " — failed " + t.getClass().getSimpleName() + " " + t.getMessage());
            return null;
        }
    }

    // ---------- face resolution ----------

    /** Bundle of three face textures (top, left-visible, right-visible). */
    private static final class Faces {
        final BufferedImage top;
        final BufferedImage left;
        final BufferedImage right;
        Faces(BufferedImage top, BufferedImage left, BufferedImage right) {
            this.top = top; this.left = left; this.right = right;
        }
    }

    /**
     * Try to find suitable per-face textures for the given block. Falls back
     * progressively from the more specific naming conventions to the catch-all
     * single texture.
     */
    private Faces resolveFaces(Material mat) {
        String name = mat.name().toLowerCase(Locale.ROOT);

        // 1. Logs / wood / stems / hyphae: <name>_top for top + <name> for sides.
        if (name.endsWith("_log") || name.endsWith("_wood")
                || name.endsWith("_stem") || name.endsWith("_hyphae")) {
            BufferedImage top  = textures.getRaw("block/" + name + "_top");
            BufferedImage side = textures.getRaw("block/" + name);
            if (top != null && side != null) {
                debug.accept("block-iso: " + mat + " resolved as log (_top + sides)");
                return new Faces(top, side, side);
            }
        }

        // 2. Common pattern: <name>_top + <name>_side (grass_block, sand-stone, etc.)
        BufferedImage top  = textures.getRaw("block/" + name + "_top");
        BufferedImage side = textures.getRaw("block/" + name + "_side");
        if (top != null && side != null) {
            debug.accept("block-iso: " + mat + " resolved as _top + _side");
            return new Faces(top, side, side);
        }

        // 3. Single texture for all faces (dirt, stone, oak_planks, cobblestone, …)
        BufferedImage all = textures.getRaw("block/" + name);
        if (all != null) {
            debug.accept("block-iso: " + mat + " resolved as single texture (block/" + name + ")");
            return new Faces(all, all, all);
        }

        // 4. Last-ditch: a few hand-rolled aliases for blocks with weird names.
        BufferedImage[] aliased = aliasedFaces(name);
        if (aliased != null) {
            debug.accept("block-iso: " + mat + " resolved via aliased rule");
            return new Faces(aliased[0], aliased[1], aliased[2]);
        }

        debug.accept("block-iso: " + mat + " has no resolver — no faces found");
        return null;
    }

    /** Return [top, left, right] for blocks whose textures don't follow the rules. */
    private BufferedImage[] aliasedFaces(String name) {
        switch (name) {
            case "grass_block": {
                BufferedImage t = textures.getRaw("block/grass_block_top");
                BufferedImage s = textures.getRaw("block/grass_block_side");
                if (t != null && s != null) return new BufferedImage[]{t, s, s};
                return null;
            }
            case "snow_block": {
                BufferedImage s = textures.getRaw("block/snow");
                if (s != null) return new BufferedImage[]{s, s, s};
                return null;
            }
            case "sand": {
                BufferedImage s = textures.getRaw("block/sand");
                if (s != null) return new BufferedImage[]{s, s, s};
                return null;
            }
            case "crafting_table": {
                BufferedImage t = textures.getRaw("block/crafting_table_top");
                BufferedImage f = textures.getRaw("block/crafting_table_front");
                BufferedImage s = textures.getRaw("block/crafting_table_side");
                if (t != null && f != null && s != null) return new BufferedImage[]{t, f, s};
                return null;
            }
            case "furnace":
            case "blast_furnace":
            case "smoker": {
                BufferedImage t = textures.getRaw("block/" + name + "_top");
                BufferedImage f = textures.getRaw("block/" + name + "_front");
                BufferedImage s = textures.getRaw("block/" + name + "_side");
                if (t != null && f != null && s != null) return new BufferedImage[]{t, f, s};
                return null;
            }
            // Chests live as entity textures (entity/chest/<variant>.png) with
            // the lid + body unfolded in a single 64×64 sheet. Build pseudo
            // 16×16 face textures by stitching the relevant rows together.
            case "chest":
                return chestFaces("normal");
            case "trapped_chest":
                return chestFaces("trapped");
            case "ender_chest":
                return chestFaces("ender");
            // Barrels render the actual top/side block textures, so the
            // standard <name>_top + <name>_side path catches them above.
            default:
                return null;
        }
    }

    /**
     * Build top + side face textures for a chest variant from its entity
     * texture (lid front + body front + lid top regions). The resulting
     * textures are 16×16 with a 1-px transparent border so they compose
     * naturally with the iso projection.
     */
    private BufferedImage[] chestFaces(String variant) {
        BufferedImage chest = textures.getRaw("entity/chest/" + variant);
        if (chest == null) return null;

        // Lid top: x=14, y=0, w=14, h=14.
        BufferedImage top = padded(safeSub(chest, 14, 0, 14, 14), 16, 16, 1, 1);

        // Side face = lid front (5 px tall) + body front (10 px tall) stacked.
        BufferedImage face = new BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = face.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            BufferedImage lidFront = safeSub(chest, 14, 14, 14, 5);
            if (lidFront != null) g.drawImage(lidFront, 1, 1, null);
            BufferedImage bodyFront = safeSub(chest, 14, 33, 14, 10);
            if (bodyFront != null) g.drawImage(bodyFront, 1, 6, null);
        } finally {
            g.dispose();
        }

        if (top == null || face == null) return null;
        return new BufferedImage[]{top, face, face};
    }

    private static BufferedImage safeSub(BufferedImage img, int x, int y, int w, int h) {
        try {
            if (img == null) return null;
            if (x < 0 || y < 0 || x + w > img.getWidth() || y + h > img.getHeight()) return null;
            return img.getSubimage(x, y, w, h);
        } catch (Throwable t) {
            return null;
        }
    }

    private static BufferedImage padded(BufferedImage src, int w, int h, int dx, int dy) {
        if (src == null) return null;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(src, dx, dy, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    // ---------- compositing ----------

    private static BufferedImage compose(Faces faces) {
        BufferedImage out = new BufferedImage(OUT_SIZE, OUT_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            // Order matters: paint back-to-front. Right and left faces sit
            // behind the top edge, so any one of them can go first; we draw
            // both side faces, then the top.
            drawFace(g, darken(faces.right, RIGHT_SHADE), RIGHT_FACE);
            drawFace(g, darken(faces.left,  LEFT_SHADE),  LEFT_FACE);
            drawFace(g, faces.top,                         TOP_FACE);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Paint {@code face} into {@code g} via {@code transform}. */
    private static void drawFace(Graphics2D g, BufferedImage face, AffineTransform transform) {
        if (face == null) return;
        AffineTransform prev = g.getTransform();
        Composite prevComposite = g.getComposite();
        try {
            AffineTransform full = new AffineTransform(prev);
            full.concatenate(transform);
            g.setTransform(full);
            g.drawImage(face, 0, 0, null);
        } finally {
            g.setTransform(prev);
            g.setComposite(prevComposite);
        }
    }

    /** Multiply each colour channel by {@code factor}; keep alpha untouched. */
    private static BufferedImage darken(BufferedImage src, float factor) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int a = (rgb >>> 24) & 0xFF;
                if (a == 0) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                int r = clamp((int) (((rgb >> 16) & 0xFF) * factor));
                int g = clamp((int) (((rgb >>  8) & 0xFF) * factor));
                int b = clamp((int) (( rgb        & 0xFF) * factor));
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
