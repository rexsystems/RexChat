package me.rexsystems.rexChat.image;

import me.rexsystems.rexChat.hooks.image.BlockModel;
import me.rexsystems.rexChat.hooks.image.BlockModelRenderer;
import me.rexsystems.rexChat.hooks.image.GuiTextureCache;
import me.rexsystems.rexChat.hooks.image.ItemTextureCache;
import me.rexsystems.rexChat.hooks.image.PlayerBodyRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Preview test: composite a full inventory image exactly like the {@code [inv]}
 * Discord preview would, but with sample blocks instead of a real
 * {@link org.bukkit.entity.Player}'s inventory and a body sprite fetched from
 * mc-heads.net for a known UUID. Runs offline against mcasset.cloud + the
 * BlockModelRenderer pipeline so we can iterate on the look without
 * deploying the plugin to a server.
 *
 * <p>Disabled by default. Run with {@code mvn test -Drender.preview=true}
 * to produce {@code target/render-preview/inventory.png}.
 */
@EnabledIfSystemProperty(named = "render.preview", matches = "true")
public final class InventoryPreviewTest {

    /** MC pixel scale: each MC pixel becomes SCALE screen pixels. */
    private static final int SCALE = 4;

    // ----- inventory.png slot positions (1×) -----
    private static final int INV_W = 176;
    private static final int INV_H = 166;
    private static final int MAIN_INV_X  = 8,  MAIN_INV_Y  = 84;
    private static final int HOTBAR_X    = 8,  HOTBAR_Y    = 142;
    private static final int SLOT_STRIDE = 18;
    private static final int PREVIEW_X   = 26, PREVIEW_Y   = 8;
    private static final int PREVIEW_W   = 44, PREVIEW_H   = 60;
    private static final int PREVIEW_BODY_DY = 8;

    @Test
    public void buildPreview() throws Exception {
        File outDir = new File("target/render-preview");
        if (!outDir.exists()) outDir.mkdirs();

        String base = "https://assets.mcasset.cloud/1.21.11/assets/minecraft/textures/";
        ItemTextureCache itemCache = new ItemTextureCache(outDir, base);
        itemCache.setDebug(System.out::println);
        GuiTextureCache guiCache = new GuiTextureCache(outDir, base);
        BlockModelRenderer modelRenderer = new BlockModelRenderer(itemCache);
        modelRenderer.setDebug(System.out::println);
        PlayerBodyRenderer bodyRenderer = new PlayerBodyRenderer(outDir, itemCache);
        bodyRenderer.setDebug(System.out::println);

        Map<String, BufferedImage> blockCache = new LinkedHashMap<>();

        int w = INV_W * SCALE;
        int h = INV_H * SCALE;
        BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            // 1) Chrome
            BufferedImage chrome = guiCache.get("container/inventory");
            if (chrome != null) {
                g.drawImage(chrome.getSubimage(0, 0, INV_W, INV_H), 0, 0, w, h, null);
            } else {
                g.setColor(new Color(0xC6, 0xC6, 0xC6));
                g.fillRect(0, 0, w, h);
            }

            // 2) Player body — use the REAL PlayerBodyRenderer (composed from
            //    raw skin texture + armour overlays) so the preview matches
            //    what the plugin produces in [inv]. Test sample armour set:
            //    diamond helmet + iron chestplate + golden leggings + netherite boots.
            String uuid = System.getProperty("render.uuid", "069a79f4-44e9-4726-a5be-fca90e38aaf5");
            java.util.UUID uuidObj = java.util.UUID.fromString(uuid);
            BufferedImage body = bodyRenderer.render(
                    uuidObj,
                    "diamond_helmet",
                    "iron_chestplate",
                    "golden_leggings",
                    "netherite_boots",
                    PREVIEW_H * SCALE);
            if (body != null) {
                int dh = PREVIEW_H * SCALE;
                int dw = (int) Math.round((double) body.getWidth() / body.getHeight() * dh);
                int previewW = PREVIEW_W * SCALE;
                int dx = PREVIEW_X * SCALE + Math.max(0, (previewW - dw) / 2);
                int dy = (PREVIEW_Y + PREVIEW_BODY_DY) * SCALE;
                g.setComposite(AlphaComposite.SrcOver);
                g.drawImage(body, dx, dy, dw, dh, null);
            }

            // 3) Some sample blocks via BlockIconRenderer (model JSON pipeline).
            //    Hotbar populated with a representative mix to exercise the
            //    full-cube / column / multi-element / chest paths.
            String[] hotbar = {
                    "DIRT", "STONE", "OAK_LOG", "GRASS_BLOCK", "DIAMOND_BLOCK",
                    "OAK_PLANKS", "COBBLESTONE", "CRAFTING_TABLE", "ENDER_CHEST"
            };
            // Main inventory rows: assorted blocks + a couple of empties.
            String[][] mainInv = {
                    {"OAK_STAIRS", "OAK_SLAB", "STONE_BRICKS", "GLASS", "SAND",
                            "GOLD_BLOCK", "IRON_BLOCK", "REDSTONE_BLOCK", "EMERALD_BLOCK"},
                    {"NETHERITE_BLOCK", "OBSIDIAN", "BIRCH_LOG", "DARK_OAK_PLANKS",
                            "QUARTZ_BLOCK", null, null, null, null},
                    {null, null, null, null, null, null, null, null, null}
            };

            for (int i = 0; i < hotbar.length; i++) {
                drawBlock(g, modelRenderer, blockCache, itemCache, hotbar[i],
                        HOTBAR_X + i * SLOT_STRIDE, HOTBAR_Y);
            }
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    drawBlock(g, modelRenderer, blockCache, itemCache, mainInv[row][col],
                            MAIN_INV_X + col * SLOT_STRIDE,
                            MAIN_INV_Y + row * SLOT_STRIDE);
                }
            }
        } finally {
            g.dispose();
        }

        // Write at native resolution + a 2× upscale so we can inspect easily.
        File native_ = new File(outDir, "inventory.png");
        ImageIO.write(canvas, "png", native_);
        System.out.println("[preview] wrote " + native_.getAbsolutePath()
                + " (" + canvas.getWidth() + "x" + canvas.getHeight() + ")");

        BufferedImage big = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg = big.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        bg.drawImage(canvas, 0, 0, w * 2, h * 2, null);
        bg.dispose();
        File bigFile = new File(outDir, "inventory-2x.png");
        ImageIO.write(big, "png", bigFile);
        System.out.println("[preview] wrote " + bigFile.getAbsolutePath());
    }

    /** Render a block by its model name (e.g. "dirt") via the model JSON pipeline. */
    private void drawBlock(Graphics2D g, BlockModelRenderer modelRenderer,
                           Map<String, BufferedImage> cache, ItemTextureCache textures,
                           String name, int slotX, int slotY) {
        if (name == null) return;
        BufferedImage tex = cache.computeIfAbsent(name, n -> {
            BlockModel model = BlockModel.load("block/" + n.toLowerCase(), textures);
            if (model == null) return null;
            model.resolveTextureVars();
            return modelRenderer.render(model);
        });
        if (tex == null) {
            System.out.println("[preview] no model for " + name);
            return;
        }
        int dx = slotX * SCALE;
        int dy = slotY * SCALE;
        int dw = 16 * SCALE;
        int dh = 16 * SCALE;
        g.drawImage(tex, dx, dy, dw, dh, null);
    }

    /** Pull the rendered body sprite from mc-heads.net (no caching for the test). */
    private static BufferedImage downloadBody(String uuid, int height) {
        try {
            String url = "https://mc-heads.net/body/" + uuid + "/" + height;
            URL u = URI.create(url).toURL();
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestProperty("User-Agent", "RexChat/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                BufferedImage img = ImageIO.read(in);
                conn.disconnect();
                return img;
            }
        } catch (Throwable t) {
            return null;
        }
    }
}
