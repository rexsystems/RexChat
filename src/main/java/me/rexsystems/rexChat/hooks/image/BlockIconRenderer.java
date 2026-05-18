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

    // Affine transforms that project a 16×16 face texture onto its iso position.
    // Java AffineTransform args: (m00, m10, m01, m11, m02, m12).
    private static final AffineTransform TOP_FACE   = new AffineTransform( 1.0, 0.5, -1.0, 0.5, 16.0, 0.0);
    private static final AffineTransform LEFT_FACE  = new AffineTransform( 1.0, 0.5,  0.0, 1.0,  0.0, 8.0);
    private static final AffineTransform RIGHT_FACE = new AffineTransform(-1.0, 0.5,  0.0, 1.0, 32.0, 8.0);

    // Side face shading matches MC's inventory GUI lighting.
    private static final float LEFT_SHADE  = 0.85f;
    private static final float RIGHT_SHADE = 0.7f;

    private final ItemTextureCache textures;
    private final ConcurrentMap<Material, BufferedImage> cache = new ConcurrentHashMap<>();

    public BlockIconRenderer(ItemTextureCache textures) {
        this.textures = textures;
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

        Faces faces = resolveFaces(mat);
        if (faces == null) {
            cache.put(mat, NONE);
            return null;
        }

        BufferedImage out = compose(faces);
        cache.put(mat, out);
        return out;
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
            if (top != null && side != null) return new Faces(top, side, side);
        }

        // 2. Common pattern: <name>_top + <name>_side (grass_block, sand-stone, etc.)
        BufferedImage top  = textures.getRaw("block/" + name + "_top");
        BufferedImage side = textures.getRaw("block/" + name + "_side");
        if (top != null && side != null) return new Faces(top, side, side);

        // 3. Single texture for all faces (dirt, stone, oak_planks, cobblestone, …)
        BufferedImage all = textures.getRaw("block/" + name);
        if (all != null) return new Faces(all, all, all);

        // 4. Last-ditch: a few hand-rolled aliases for blocks with weird names.
        BufferedImage[] aliased = aliasedFaces(name);
        if (aliased != null) return new Faces(aliased[0], aliased[1], aliased[2]);

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
            default:
                return null;
        }
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
