package me.rexsystems.rexChat.hooks.image;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Renders a parsed {@link BlockModel} to a {@link BufferedImage} matching
 * Minecraft's vanilla GUI projection: 30° pitch + 225° yaw + 0.625 scale,
 * with per-axis face shading.
 *
 * <p>Implementation strategy:
 * <ol>
 *   <li>Each {@link BlockModel.Element} contributes 6 axis-aligned quads
 *       (one per face), each with 4 vertices in cube-local space [0..16]³
 *       plus per-vertex UV.</li>
 *   <li>Vertices go through the model's GUI display matrix (translate,
 *       scale, then rotate XYZ Euler). Result is in centred world space.</li>
 *   <li>Orthographic projection: {@code screen.x = world.x},
 *       {@code screen.y = -world.y}; world.z is kept for back-to-front
 *       sorting.</li>
 *   <li>Each quad is sorted by depth and rasterised via
 *       {@link AffineTransform} from its texture rectangle to its screen
 *       parallelogram. Shading is applied via per-face RGB scale to mimic
 *       MC's GUI lighting (top brightest, sides dimmer).</li>
 * </ol>
 *
 * <p>This handles the vast majority of vanilla blocks (full cubes plus
 * multi-element models like stairs / slabs). Element-local rotation with a
 * 22.5° axis is honoured too.
 */
public final class BlockModelRenderer {

    /** Output canvas size in pixels. Items historically render to 32 in MC's
     *  inventory image (16 model pixels × 2). Using 32 gives crisp upscale
     *  to a 16-slot 4× output. */
    public static final int OUT_SIZE = 32;

    /** MC GUI face-shading factors keyed by ORIGINAL (pre-rotation) face axis. */
    private static final double SHADE_UP    = 1.00;
    private static final double SHADE_DOWN  = 0.50;
    private static final double SHADE_NS    = 0.80;  // north + south
    private static final double SHADE_EW    = 0.60;  // east + west

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
     * {@link #OUT_SIZE}×{@link #OUT_SIZE} ARGB image. Returns {@code null} if
     * the model has no elements (in which case the caller should fall back).
     */
    public BufferedImage render(BlockModel model) {
        if (model == null || model.elements.isEmpty()) return null;

        // Pre-compute the model→world matrix (translation + scale + rotation).
        Mat4 modelMatrix = buildGuiMatrix(model.guiTransform);

        // 1) Generate every face from every element.
        List<RenderQuad> quads = new ArrayList<>();
        for (BlockModel.Element el : model.elements) {
            collectFaces(el, model, modelMatrix, quads);
        }
        if (quads.isEmpty()) return null;

        // 2) Sort back-to-front (painter's algorithm). Larger world-Z = closer.
        quads.sort(Comparator.comparingDouble(q -> q.avgDepth));

        // 3) Rasterise.
        BufferedImage canvas = new BufferedImage(OUT_SIZE, OUT_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for (RenderQuad q : quads) drawQuad(g, q);
        } finally {
            g.dispose();
        }
        return canvas;
    }

    // ---------- face generation ----------

    private static final String[] FACES =
            { "down", "up", "north", "south", "west", "east" };

    private void collectFaces(BlockModel.Element el, BlockModel model,
                               Mat4 modelMatrix, List<RenderQuad> out) {
        double fx = el.from[0], fy = el.from[1], fz = el.from[2];
        double tx = el.to[0],   ty = el.to[1],   tz = el.to[2];

        // Element-local rotation matrix, if any. Order of operations:
        //   p_out = T_origin * R * T_-origin * p
        // → first translate so rotation origin is at world origin, rotate,
        // then translate back. With preMultiply that's calls in execution
        // order: T_-origin, R, T_+origin.
        Mat4 elementRot = null;
        if (el.rotationAxis != null && el.rotationAngle != 0 && el.rotationOrigin != null) {
            elementRot = Mat4.identity();
            elementRot.translate(-el.rotationOrigin[0], -el.rotationOrigin[1], -el.rotationOrigin[2]);
            switch (el.rotationAxis) {
                case "x": elementRot.rotateX(el.rotationAngle); break;
                case "y": elementRot.rotateY(el.rotationAngle); break;
                case "z": elementRot.rotateZ(el.rotationAngle); break;
                default:
            }
            elementRot.translate(el.rotationOrigin[0], el.rotationOrigin[1], el.rotationOrigin[2]);
        }

        for (String key : FACES) {
            BlockModel.Face face = el.faces.get(key);
            if (face == null) continue;

            double[][] vert3 = faceVertices(key, fx, fy, fz, tx, ty, tz);
            double[][] uv    = faceUv(key, fx, fy, fz, tx, ty, tz, face.uv);

            // Resolve texture path → image.
            String texPath = model.resolveFaceTexture(face.texture);
            if (texPath == null || texPath.startsWith("#")) {
                debug.accept("model: unresolved texture " + face.texture + " on face " + key);
                continue;
            }
            BufferedImage tex = textures.getRaw(texPath);
            if (tex == null) {
                debug.accept("model: texture missing - " + texPath);
                continue;
            }

            // Apply rotation (element-local first, then GUI matrix).
            double[][] world3 = new double[4][3];
            for (int i = 0; i < 4; i++) {
                double[] v = vert3[i].clone();
                if (elementRot != null) elementRot.transform(v);
                modelMatrix.transform(v);
                world3[i] = v;
            }

            // Project to screen.
            double[][] screen = new double[4][2];
            double avgZ = 0;
            for (int i = 0; i < 4; i++) {
                screen[i][0] = world3[i][0] + OUT_SIZE / 2.0;
                screen[i][1] = OUT_SIZE / 2.0 - world3[i][1];
                avgZ += world3[i][2];
            }
            avgZ /= 4.0;

            // Back-face cull: skip if the rotated normal points AWAY from the viewer.
            // We compute the screen-space cross product instead of a 3D normal
            // (cheaper + same answer for orthographic projection).
            double e1x = screen[1][0] - screen[0][0];
            double e1y = screen[1][1] - screen[0][1];
            double e2x = screen[3][0] - screen[0][0];
            double e2y = screen[3][1] - screen[0][1];
            double cross = e1x * e2y - e1y * e2x;
            if (cross >= 0) continue; // face is away from viewer

            double shade = shadeFor(key);
            out.add(new RenderQuad(screen, uv, tex, shade, avgZ, face.rotation));
        }
    }

    /** Returns the 4 vertices (in CCW order viewed from outside) for a given face. */
    private static double[][] faceVertices(String face,
                                           double fx, double fy, double fz,
                                           double tx, double ty, double tz) {
        switch (face) {
            case "down":  return new double[][] {{fx, fy, tz}, {tx, fy, tz}, {tx, fy, fz}, {fx, fy, fz}};
            case "up":    return new double[][] {{fx, ty, fz}, {tx, ty, fz}, {tx, ty, tz}, {fx, ty, tz}};
            case "north": return new double[][] {{tx, ty, fz}, {fx, ty, fz}, {fx, fy, fz}, {tx, fy, fz}};
            case "south": return new double[][] {{fx, ty, tz}, {tx, ty, tz}, {tx, fy, tz}, {fx, fy, tz}};
            case "west":  return new double[][] {{fx, ty, fz}, {fx, ty, tz}, {fx, fy, tz}, {fx, fy, fz}};
            case "east":  return new double[][] {{tx, ty, tz}, {tx, ty, fz}, {tx, fy, fz}, {tx, fy, tz}};
            default: return null;
        }
    }

    /**
     * UV rectangle for the face, in source-texture pixel coordinates within
     * a 16×16 unit space. Custom UVs from the model JSON override; otherwise
     * derive from the element's from/to so trimmed elements still sample
     * the right portion of the texture.
     */
    private static double[][] faceUv(String face,
                                     double fx, double fy, double fz,
                                     double tx, double ty, double tz,
                                     double[] uvOverride) {
        double u1, v1, u2, v2;
        if (uvOverride != null && uvOverride.length >= 4) {
            u1 = uvOverride[0]; v1 = uvOverride[1];
            u2 = uvOverride[2]; v2 = uvOverride[3];
        } else {
            switch (face) {
                case "down":  u1 = fx;       v1 = 16 - tz; u2 = tx;       v2 = 16 - fz; break;
                case "up":    u1 = fx;       v1 = fz;      u2 = tx;       v2 = tz;      break;
                case "north": u1 = 16 - tx;  v1 = 16 - ty; u2 = 16 - fx;  v2 = 16 - fy; break;
                case "south": u1 = fx;       v1 = 16 - ty; u2 = tx;       v2 = 16 - fy; break;
                case "west":  u1 = fz;       v1 = 16 - ty; u2 = tz;       v2 = 16 - fy; break;
                case "east":  u1 = 16 - tz;  v1 = 16 - ty; u2 = 16 - fz;  v2 = 16 - fy; break;
                default: u1 = 0; v1 = 0; u2 = 16; v2 = 16;
            }
        }
        return new double[][] {{u1, v1}, {u2, v1}, {u2, v2}, {u1, v2}};
    }

    private static double shadeFor(String face) {
        switch (face) {
            case "up":    return SHADE_UP;
            case "down":  return SHADE_DOWN;
            case "north":
            case "south": return SHADE_NS;
            case "east":
            case "west":  return SHADE_EW;
            default:      return 1.0;
        }
    }

    // ---------- rasterisation ----------

    /**
     * A single ready-to-rasterise face: 4 screen-space vertices (in CCW
     * order), 4 UV coordinates into {@link #texture}, an {@link #avgDepth}
     * for back-to-front sort, and a {@link #shade} factor 0..1 applied at
     * draw time.
     */
    private static final class RenderQuad {
        final double[][] screen;
        final double[][] uv;
        final BufferedImage texture;
        final double shade;
        final double avgDepth;
        final int textureRotation;

        RenderQuad(double[][] s, double[][] u, BufferedImage t, double sh, double d, int rot) {
            screen = s; uv = u; texture = t; shade = sh; avgDepth = d; textureRotation = rot;
        }
    }

    private static void drawQuad(Graphics2D g, RenderQuad q) {
        // Crop the texture to the UV region, rotate per face.rotation.
        BufferedImage region = sampleTextureRegion(q.texture, q.uv, q.textureRotation);
        if (region == null) return;

        // Apply shading by re-painting through a per-channel scale.
        if (q.shade < 1.0) {
            region = applyShade(region, (float) q.shade);
        }

        // Compute affine transform from texture rect (0,0)-(W,H) to screen
        // parallelogram (P0,P1,P2,P3). 3-point mapping is enough; we use
        // P0, P1 (right of P0) and P3 (below P0).
        double w = region.getWidth();
        double h = region.getHeight();
        double[] P0 = {q.screen[0][0], q.screen[0][1]};
        double[] P1 = {q.screen[1][0], q.screen[1][1]};
        double[] P3 = {q.screen[3][0], q.screen[3][1]};

        double m00 = (P1[0] - P0[0]) / w;
        double m10 = (P1[1] - P0[1]) / w;
        double m01 = (P3[0] - P0[0]) / h;
        double m11 = (P3[1] - P0[1]) / h;
        double m02 = P0[0];
        double m12 = P0[1];

        AffineTransform prev = g.getTransform();
        try {
            AffineTransform t = new AffineTransform(prev);
            t.concatenate(new AffineTransform(m00, m10, m01, m11, m02, m12));
            g.setTransform(t);
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(region, 0, 0, null);
        } finally {
            g.setTransform(prev);
        }
    }

    /**
     * Cut out the UV-specified region of the texture and apply any
     * {@link BlockModel.Face#rotation} (multiples of 90°). UV is in 16-unit
     * texture space; we scale to the actual pixel size.
     */
    private static BufferedImage sampleTextureRegion(BufferedImage tex, double[][] uv, int rotation) {
        int texW = tex.getWidth();
        int texH = tex.getHeight();
        // Scale UV (0..16) to actual pixel coords.
        double sx = texW / 16.0;
        double sy = texH / 16.0;
        int x1 = (int) Math.round(uv[0][0] * sx);
        int y1 = (int) Math.round(uv[0][1] * sy);
        int x2 = (int) Math.round(uv[2][0] * sx);
        int y2 = (int) Math.round(uv[2][1] * sy);

        int rx = Math.min(x1, x2);
        int ry = Math.min(y1, y2);
        int rw = Math.abs(x2 - x1);
        int rh = Math.abs(y2 - y1);
        if (rw == 0 || rh == 0) return null;
        if (rx < 0 || ry < 0 || rx + rw > texW || ry + rh > texH) {
            // Out of bounds — clamp.
            rx = Math.max(0, rx);
            ry = Math.max(0, ry);
            rw = Math.min(rw, texW - rx);
            rh = Math.min(rh, texH - ry);
            if (rw <= 0 || rh <= 0) return null;
        }
        BufferedImage sub = tex.getSubimage(rx, ry, rw, rh);
        if (rotation == 0) return copy(sub);

        // Rotate by 90/180/270 degrees clockwise.
        BufferedImage out;
        int r = ((rotation % 360) + 360) % 360;
        if (r == 90 || r == 270) out = new BufferedImage(rh, rw, BufferedImage.TYPE_INT_ARGB);
        else                     out = new BufferedImage(rw, rh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            AffineTransform t = new AffineTransform();
            switch (r) {
                case 90:  t.translate(rh, 0);   t.rotate(Math.PI / 2);  break;
                case 180: t.translate(rw, rh);  t.rotate(Math.PI);      break;
                case 270: t.translate(0, rw);   t.rotate(-Math.PI / 2); break;
                default:
            }
            g.drawImage(sub, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    /** Multiply each colour channel by {@code factor}; alpha unchanged. */
    private static BufferedImage applyShade(BufferedImage src, float factor) {
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

    // ---------- transform helpers ----------

    /**
     * Compose the model→world matrix from a {@code display.gui} transform.
     * MC convention (per Mojang's source): {@code mulPose(Xp).mulPose(Yp).mulPose(Zp)}
     * builds the matrix {@code M = Rx * Ry * Rz} so a vertex is transformed as
     * {@code v' = Rx * Ry * Rz * v} — i.e. <em>Z is applied first, then Y, then X</em>
     * (right-to-left). With {@link Mat4#preMultiply} prepending on the LEFT,
     * the call order is the same as the application order: Z first, then Y,
     * then X.
     */
    private static Mat4 buildGuiMatrix(BlockModel.GuiTransform t) {
        Mat4 m = Mat4.identity();
        // 1) Centre the [0..16] cube on the origin.
        m.translate(-8, -8, -8);
        // 2) Scale.
        m.scale(t.scale[0], t.scale[1], t.scale[2]);
        // 3) Rotate (Z applied first to vertex, then Y, then X — vanilla order).
        m.rotateZ(t.rotation[2]);
        m.rotateY(t.rotation[1]);
        m.rotateX(t.rotation[0]);
        // 4) Translate by the configured display offset.
        m.translate(t.translation[0], t.translation[1], t.translation[2]);
        return m;
    }

    /**
     * Tiny 4×4 row-major matrix struct, just enough for our transform pipeline.
     * Operations are mutating + chainable. Vector transforms use the upper-left
     * 3×3 + translation column (homogeneous w=1, no perspective divide).
     */
    static final class Mat4 {
        final double[] m = new double[16];

        static Mat4 identity() {
            Mat4 r = new Mat4();
            r.m[0] = r.m[5] = r.m[10] = r.m[15] = 1.0;
            return r;
        }

        void translate(double tx, double ty, double tz) {
            // pre-multiply: this = T * this
            Mat4 t = identity();
            t.m[3]  = tx;
            t.m[7]  = ty;
            t.m[11] = tz;
            preMultiply(t);
        }

        void scale(double sx, double sy, double sz) {
            Mat4 s = identity();
            s.m[0] = sx; s.m[5] = sy; s.m[10] = sz;
            preMultiply(s);
        }

        void rotateX(double deg) {
            if (deg == 0) return;
            double r = Math.toRadians(deg);
            double c = Math.cos(r), s = Math.sin(r);
            Mat4 m = identity();
            m.m[5] = c;  m.m[6] = -s;
            m.m[9] = s;  m.m[10] = c;
            preMultiply(m);
        }

        void rotateY(double deg) {
            if (deg == 0) return;
            double r = Math.toRadians(deg);
            double c = Math.cos(r), s = Math.sin(r);
            Mat4 m = identity();
            m.m[0] = c;  m.m[2] = s;
            m.m[8] = -s; m.m[10] = c;
            preMultiply(m);
        }

        void rotateZ(double deg) {
            if (deg == 0) return;
            double r = Math.toRadians(deg);
            double c = Math.cos(r), s = Math.sin(r);
            Mat4 m = identity();
            m.m[0] = c;  m.m[1] = -s;
            m.m[4] = s;  m.m[5] = c;
            preMultiply(m);
        }

        void preMultiply(Mat4 a) {
            // this = a * this
            double[] r = new double[16];
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    double v = 0;
                    for (int k = 0; k < 4; k++) v += a.m[i * 4 + k] * m[k * 4 + j];
                    r[i * 4 + j] = v;
                }
            }
            System.arraycopy(r, 0, m, 0, 16);
        }

        /** In-place transform of a 3-element vertex (w=1 implicit). */
        void transform(double[] v) {
            double x = v[0], y = v[1], z = v[2];
            v[0] = m[0] * x + m[1] * y + m[2]  * z + m[3];
            v[1] = m[4] * x + m[5] * y + m[6]  * z + m[7];
            v[2] = m[8] * x + m[9] * y + m[10] * z + m[11];
        }
    }
}
