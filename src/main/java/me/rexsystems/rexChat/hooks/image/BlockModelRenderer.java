package me.rexsystems.rexChat.hooks.image;

import com.loohp.blockmodelrenderer.blending.BlendingModes;
import com.loohp.blockmodelrenderer.render.Hexahedron;
import com.loohp.blockmodelrenderer.render.Model;
import com.loohp.blockmodelrenderer.render.Point3D;
import com.loohp.blockmodelrenderer.render.Vector;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Renders a parsed {@link BlockModel} to a {@link BufferedImage} using the
 * {@code BlockModelRenderer} 3D software rasteriser, which gives identical
 * geometry / Z-buffering / lighting to vanilla Minecraft's GUI block view.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Each {@link BlockModel.Element} (a sub-cuboid) is converted to a
 *       {@link Hexahedron} via {@link Hexahedron#fromCorners} with its 6
 *       per-face textures pre-cropped + UV-rotated.</li>
 *   <li>All hexahedrons are wrapped in a {@link Model}, then the model is
 *       transformed by the {@code display.gui} matrix
 *       (centre on origin → scale → rotate XYZ → translate).</li>
 *   <li>Lighting: SIDE preset {@code (-0.5, 0.65, 0.9), ambient=0.1, max=1.0}
 *       (matches vanilla {@code block.json}'s {@code "gui_light": "side"}).</li>
 *   <li>Rasterised through an {@link AffineTransform} that maps
 *       1 model-pixel to {@code OUT_SIZE/16} screen pixels.</li>
 * </ol>
 */
public final class BlockModelRenderer {

    /** Output canvas size in pixels. 64 = 4× MC slot scale, fills the slot 1:1. */
    public static final int OUT_SIZE = 64;

    /** Vanilla {@code block.json}'s {@code gui_light:"side"} preset. */
    private static final Vector LIGHT_DIRECTION = new Vector(-0.5, 0.65, 0.9);
    private static final double LIGHT_AMBIENT   = 0.1;
    private static final double LIGHT_MAX       = 1.0;

    /** Shared CPU pool for parallel rasterisation. Daemon threads. */
    private static final ExecutorService RENDER_POOL = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()), new ThreadFactory() {
                private final AtomicInteger seq = new AtomicInteger();
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "RexChat-Render-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            });

    private final ItemTextureCache textures;
    private Consumer<String> debug = msg -> {};

    public BlockModelRenderer(ItemTextureCache textures) {
        this.textures = textures;
    }

    public void setDebug(Consumer<String> sink) {
        this.debug = sink == null ? msg -> {} : sink;
    }

    /**
     * Render the given parsed + texture-resolved model into a transparent
     * {@link #OUT_SIZE}×{@link #OUT_SIZE} ARGB image. Returns {@code null}
     * when the model has no elements.
     */
    public BufferedImage render(BlockModel model) {
        if (model == null || model.elements.isEmpty()) return null;

        List<Hexahedron> hexa = new ArrayList<>();
        for (BlockModel.Element el : model.elements) {
            Hexahedron h = buildHexahedron(el, model);
            if (h != null) hexa.add(h);
        }
        if (hexa.isEmpty()) return null;

        Model m = new Model(hexa);

        // ---- display.gui transform: centre cube → scale → rotate → translate ----
        m.translate(-8, -8, -8);
        m.scale(model.guiTransform.scale[0],
                model.guiTransform.scale[1],
                model.guiTransform.scale[2]);
        m.rotate(model.guiTransform.rotation[0],
                 model.guiTransform.rotation[1],
                 model.guiTransform.rotation[2],
                 false);
        m.translate(model.guiTransform.translation[0],
                    model.guiTransform.translation[1],
                    model.guiTransform.translation[2]);

        // ---- lighting ----
        m.updateLighting(LIGHT_DIRECTION.clone(), LIGHT_AMBIENT, LIGHT_MAX);

        // ---- rasterise ----
        BufferedImage canvas = new BufferedImage(OUT_SIZE, OUT_SIZE, BufferedImage.TYPE_INT_ARGB);
        AffineTransform baseTransform = AffineTransform.getTranslateInstance(
                OUT_SIZE / 2.0, OUT_SIZE / 2.0);
        baseTransform.concatenate(AffineTransform.getScaleInstance(
                OUT_SIZE / 16.0, OUT_SIZE / 16.0));
        try {
            m.render(canvas, true, baseTransform, BlendingModes.NORMAL, RENDER_POOL).join();
        } catch (Throwable t) {
            debug.accept("model: render failed " + t.getClass().getSimpleName() + " " + t.getMessage());
            return null;
        }
        return canvas;
    }

    /**
     * Convert one {@link BlockModel.Element} into a {@link Hexahedron}.
     * Direction order for {@link Hexahedron#fromCorners} is
     * {@code [up, down, north, east, south, west]}.
     */
    private Hexahedron buildHexahedron(BlockModel.Element el, BlockModel model) {
        Point3D p1 = new Point3D(el.from[0], el.from[1], el.from[2]);
        Point3D p2 = new Point3D(el.to[0],   el.to[1],   el.to[2]);

        BufferedImage[] images = new BufferedImage[6];
        String[] dirs = { "up", "down", "north", "east", "south", "west" };
        for (int i = 0; i < 6; i++) {
            BlockModel.Face face = el.faces.get(dirs[i]);
            if (face == null) continue;
            String texPath = model.resolveFaceTexture(face.texture);
            if (texPath == null || texPath.startsWith("#")) {
                debug.accept("model: unresolved texture " + face.texture + " on face " + dirs[i]);
                continue;
            }
            BufferedImage tex = textures.getRaw(texPath);
            if (tex == null) {
                debug.accept("model: texture missing - " + texPath);
                continue;
            }

            double[] uv = face.uv != null ? face.uv : defaultUv(dirs[i], el.from, el.to);
            BufferedImage region = cropUv(tex, uv, face.rotation);
            if (region != null) images[i] = region;
        }

        // Replace any unresolved face with a 1×1 transparent pixel so the
        // hexahedron still constructs cleanly.
        for (int i = 0; i < 6; i++) {
            if (images[i] == null) images[i] = TRANSPARENT_PIXEL;
        }
        Hexahedron h = Hexahedron.fromCorners(p1, p2, images);

        // Element-local rotation (stairs / fences / doors / etc.).
        if (el.rotationAxis != null && el.rotationAngle != 0 && el.rotationOrigin != null) {
            double rx = "x".equals(el.rotationAxis) ? el.rotationAngle : 0;
            double ry = "y".equals(el.rotationAxis) ? el.rotationAngle : 0;
            double rz = "z".equals(el.rotationAxis) ? el.rotationAngle : 0;
            h.translate(-el.rotationOrigin[0], -el.rotationOrigin[1], -el.rotationOrigin[2]);
            h.rotate(rx, ry, rz, false);
            h.translate(el.rotationOrigin[0], el.rotationOrigin[1], el.rotationOrigin[2]);
        }
        return h;
    }

    /**
     * Default UV when the face JSON doesn't specify one. Derived from
     * {@code from}/{@code to} so trimmed elements still pull the matching
     * sub-rectangle of the texture.
     */
    private static double[] defaultUv(String face, double[] from, double[] to) {
        double fx = from[0], fy = from[1], fz = from[2];
        double tx = to[0],   ty = to[1],   tz = to[2];
        switch (face) {
            case "down":  return new double[] {fx,       16 - tz, tx,       16 - fz};
            case "up":    return new double[] {fx,       fz,      tx,       tz};
            case "north": return new double[] {16 - tx,  16 - ty, 16 - fx,  16 - fy};
            case "south": return new double[] {fx,       16 - ty, tx,       16 - fy};
            case "west":  return new double[] {fz,       16 - ty, tz,       16 - fy};
            case "east":  return new double[] {16 - tz,  16 - ty, 16 - fz,  16 - fy};
            default:      return new double[] {0, 0, 16, 16};
        }
    }

    /**
     * Crop the given texture to the UV rectangle (in 16-unit texture space)
     * and rotate it by 0/90/180/270 degrees clockwise. Returns an ARGB image
     * because the BMR raster expects {@code DataBufferInt}.
     */
    private static BufferedImage cropUv(BufferedImage tex, double[] uv, int rotation) {
        if (tex == null || uv == null || uv.length < 4) return null;
        int texW = tex.getWidth();
        int texH = tex.getHeight();
        // Animated textures stack frames vertically, so map y by texW (square frame size).
        double sx = texW / 16.0;
        double sy = Math.min(texW, texH) / 16.0;

        int x1 = (int) Math.round(uv[0] * sx);
        int y1 = (int) Math.round(uv[1] * sy);
        int x2 = (int) Math.round(uv[2] * sx);
        int y2 = (int) Math.round(uv[3] * sy);
        int rx = Math.min(x1, x2), ry = Math.min(y1, y2);
        int rw = Math.abs(x2 - x1), rh = Math.abs(y2 - y1);
        if (rw <= 0 || rh <= 0) return null;
        rx = Math.max(0, Math.min(rx, texW - 1));
        ry = Math.max(0, Math.min(ry, texH - 1));
        rw = Math.min(rw, texW - rx);
        rh = Math.min(rh, texH - ry);
        if (rw <= 0 || rh <= 0) return null;

        BufferedImage sub = tex.getSubimage(rx, ry, rw, rh);
        BufferedImage copy = new BufferedImage(rw, rh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D cg = copy.createGraphics();
        cg.drawImage(sub, 0, 0, null);
        cg.dispose();

        if (rotation == 0) return copy;
        int r = ((rotation % 360) + 360) % 360;
        BufferedImage out;
        if (r == 90 || r == 270) out = new BufferedImage(rh, rw, BufferedImage.TYPE_INT_ARGB);
        else                     out = new BufferedImage(rw, rh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            AffineTransform t = new AffineTransform();
            switch (r) {
                case 90:  t.translate(rh, 0);  t.rotate(Math.PI / 2);  break;
                case 180: t.translate(rw, rh); t.rotate(Math.PI);      break;
                case 270: t.translate(0, rw);  t.rotate(-Math.PI / 2); break;
                default:
            }
            g.drawImage(copy, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static final BufferedImage TRANSPARENT_PIXEL = createTransparentPixel();

    private static BufferedImage createTransparentPixel() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0x00000000);
        return img;
    }

    /** Allow the plugin to shut down the render pool on disable. */
    public static void shutdown() {
        try { RENDER_POOL.shutdownNow(); } catch (Throwable ignored) {}
    }
}
