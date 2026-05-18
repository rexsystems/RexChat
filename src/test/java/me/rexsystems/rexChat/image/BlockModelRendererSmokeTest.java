package me.rexsystems.rexChat.image;

import me.rexsystems.rexChat.hooks.image.BlockModel;
import me.rexsystems.rexChat.hooks.image.BlockModelRenderer;
import me.rexsystems.rexChat.hooks.image.ItemTextureCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Smoke test: render a small set of vanilla blocks via the model JSON
 * pipeline and write the resulting PNGs to {@code target/render-smoke/} so
 * the output can be eyeballed when iterating on the renderer.
 *
 * <p>Disabled by default (would hit the network on every build). Enable with
 * {@code mvn test -Drender.smoke=true}.
 */
@EnabledIfSystemProperty(named = "render.smoke", matches = "true")
public final class BlockModelRendererSmokeTest {

    @Test
    public void renderSamples() throws Exception {
        File outDir = new File("target/render-smoke");
        if (!outDir.exists()) outDir.mkdirs();

        // Pin to 1.21.11 (matches the user's server) instead of the
        // {version} placeholder Bukkit-resolves so we can run offline.
        String base = "https://assets.mcasset.cloud/1.21.11/assets/minecraft/textures/";
        ItemTextureCache cache = new ItemTextureCache(outDir, base);
        cache.setDebug(System.out::println);
        BlockModelRenderer renderer = new BlockModelRenderer(cache);
        renderer.setDebug(System.out::println);

        String[] blocks = {"dirt", "stone", "oak_planks", "oak_log", "diamond_block",
                           "grass_block", "cobblestone", "crafting_table"};
        for (String name : blocks) {
            BlockModel model = BlockModel.load("block/" + name, cache);
            if (model == null) {
                System.out.println("[smoke] " + name + " model not found");
                continue;
            }
            model.resolveTextureVars();
            BufferedImage img = renderer.render(model);
            if (img == null) {
                System.out.println("[smoke] " + name + " render returned null");
                continue;
            }
            File f = new File(outDir, name + ".png");
            ImageIO.write(img, "png", f);
            System.out.println("[smoke] " + name + " -> " + f.getAbsolutePath()
                    + " (" + img.getWidth() + "x" + img.getHeight() + ")");
        }
    }
}
