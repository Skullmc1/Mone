/*
 * This file is part of Mone.
 *
 * Mone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Mone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Mone.  If not, see <https://www.gnu.org/licenses/>.
 */

package mone.utils;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps Minecraft entity registry paths to their vanilla entity texture face UV regions
 * so the minimap can draw unique mob head icons.
 */
public final class MobHeadRegistry {

    public record HeadIcon(
            Identifier texture,
            int u, int v,
            int faceW, int faceH,
            int texW, int texH,
            int overlayU, int overlayV
    ) {
        public void render(GuiGraphicsExtractor guiGraphics, int x, int y, int size) {
            guiGraphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    x, y,
                    (float) u, (float) v,
                    size, size,
                    faceW, faceH,
                    texW, texH
            );
            if (overlayU >= 0 && overlayV >= 0) {
                guiGraphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        texture,
                        x, y,
                        (float) overlayU, (float) overlayV,
                        size, size,
                        faceW, faceH,
                        texW, texH
                );
            }
        }
    }

    private static final Map<String, HeadIcon> HEAD_ICONS = new HashMap<>();

    static {
        // Humanoid / Undead Hostiles (face 8x8 at (8,8))
        reg("zombie", "zombie/zombie.png", 8, 8, 8, 8, 64, 64, 40, 8);
        reg("husk", "zombie/husk.png", 8, 8, 8, 8, 64, 64, 40, 8);
        reg("drowned", "zombie/drowned.png", 8, 8, 8, 8, 64, 64, 40, 8);
        reg("zombie_villager", "zombie_villager/zombie_villager.png", 8, 8, 8, 10, 64, 64);
        reg("skeleton", "skeleton/skeleton.png", 8, 8, 8, 8, 64, 32);
        reg("stray", "skeleton/stray.png", 8, 8, 8, 8, 64, 32);
        reg("wither_skeleton", "skeleton/wither_skeleton.png", 8, 8, 8, 8, 64, 32);
        reg("bogged", "skeleton/bogged.png", 8, 8, 8, 8, 64, 32);
        reg("creeper", "creeper/creeper.png", 8, 8, 8, 8, 64, 32);
        reg("enderman", "enderman/enderman.png", 8, 8, 8, 8, 64, 32);
        reg("blaze", "blaze/blaze.png", 8, 8, 8, 8, 64, 32);

        // Spiders (head at 32,4 -> front face 8x8 at (40,12))
        reg("spider", "spider/spider.png", 40, 12, 8, 8, 64, 32);
        reg("cave_spider", "spider/cave_spider.png", 40, 12, 8, 8, 64, 32);

        // Illagers & Witch
        reg("witch", "witch/witch.png", 8, 8, 8, 10, 64, 128);
        reg("pillager", "illager/pillager.png", 8, 8, 8, 10, 64, 64);
        reg("vindicator", "illager/vindicator.png", 8, 8, 8, 10, 64, 64);
        reg("evoker", "illager/evoker.png", 8, 8, 8, 10, 64, 64);
        reg("ravager", "illager/ravager.png", 16, 16, 16, 20, 128, 128);
        reg("vex", "illager/vex.png", 5, 5, 5, 5, 32, 32);

        // Slimes & Nether Hostiles
        reg("slime", "slime/slime.png", 8, 8, 8, 8, 64, 32);
        reg("magma_cube", "slime/magmacube.png", 8, 8, 8, 8, 64, 64);
        reg("ghast", "ghast/ghast.png", 16, 16, 16, 16, 128, 64);
        reg("piglin", "piglin/piglin.png", 8, 8, 10, 8, 64, 64);
        reg("piglin_brute", "piglin/piglin_brute.png", 8, 8, 10, 8, 64, 64);
        reg("zombified_piglin", "piglin/zombified_piglin.png", 8, 8, 10, 8, 64, 64);
        reg("hoglin", "hoglin/hoglin.png", 73, 13, 14, 14, 128, 64);
        reg("zoglin", "hoglin/zoglin.png", 73, 13, 14, 14, 128, 64);
        reg("strider", "strider/strider.png", 16, 16, 16, 14, 64, 128);

        // Other Hostiles & Bosses
        reg("phantom", "phantom/phantom.png", 5, 5, 7, 3, 64, 64);
        reg("guardian", "guardian/guardian.png", 16, 16, 12, 12, 64, 64);
        reg("elder_guardian", "guardian/guardian_elder.png", 16, 16, 12, 12, 64, 64);
        reg("shulker", "shulker/shulker.png", 6, 58, 6, 6, 64, 64);
        reg("warden", "warden/warden.png", 10, 42, 16, 16, 128, 128);
        reg("breeze", "breeze/breeze.png", 8, 8, 8, 8, 32, 32);
        reg("creaking", "creaking/creaking.png", 6, 6, 6, 10, 64, 64);
        reg("silverfish", "silverfish/silverfish.png", 2, 2, 3, 2, 64, 32);
        reg("endermite", "endermite/endermite.png", 3, 3, 4, 3, 64, 32);
        reg("wither", "wither/wither.png", 8, 8, 8, 8, 64, 64);
        reg("ender_dragon", "enderdragon/dragon.png", 128, 46, 16, 16, 256, 256);

        // Passive / Neutral / Friendly Mobs
        reg("pig", "pig/pig_temperate.png", 8, 8, 8, 8, 64, 64);
        reg("cow", "cow/cow_temperate.png", 6, 6, 8, 8, 64, 64);
        reg("mooshroom", "cow/mooshroom_red.png", 6, 6, 8, 8, 64, 64);
        reg("sheep", "sheep/sheep.png", 8, 8, 6, 6, 64, 32);
        reg("chicken", "chicken/chicken_temperate.png", 3, 3, 4, 6, 64, 32);
        reg("wolf", "wolf/wolf.png", 4, 4, 6, 6, 64, 32);
        reg("cat", "cat/cat_black.png", 5, 5, 5, 4, 64, 32);
        reg("horse", "horse/horse_brown.png", 7, 20, 6, 5, 64, 64);
        reg("donkey", "horse/donkey.png", 7, 20, 6, 5, 64, 64);
        reg("mule", "horse/mule.png", 7, 20, 6, 5, 64, 64);
        reg("villager", "villager/villager.png", 8, 8, 8, 10, 64, 64);
        reg("wandering_trader", "wandering_trader/wandering_trader.png", 8, 8, 8, 10, 64, 64);
        reg("iron_golem", "iron_golem/iron_golem.png", 8, 8, 8, 10, 128, 128);
        reg("snow_golem", "snow_golem/snow_golem.png", 8, 8, 8, 8, 64, 64);
        reg("bee", "bee/bee.png", 10, 10, 7, 7, 64, 64);
        reg("fox", "fox/fox.png", 7, 11, 8, 6, 48, 32);
        reg("panda", "panda/panda.png", 9, 15, 13, 10, 64, 64);
        reg("axolotl", "axolotl/axolotl_lucy.png", 5, 6, 8, 5, 64, 64);
        reg("frog", "frog/frog_temperate.png", 7, 18, 7, 4, 48, 48);
        reg("goat", "goat/goat.png", 41, 51, 5, 7, 64, 64);
        reg("camel", "camel/camel.png", 35, 35, 7, 7, 128, 128);
        reg("sniffer", "sniffer/sniffer.png", 24, 15, 13, 14, 192, 192);
        reg("rabbit", "rabbit/rabbit_brown.png", 37, 5, 5, 4, 64, 64);
        reg("turtle", "turtle/turtle.png", 8, 5, 6, 5, 128, 64);
        reg("squid", "squid/squid.png", 12, 12, 12, 16, 64, 32);
        reg("glow_squid", "squid/glow_squid.png", 12, 12, 12, 16, 64, 32);
        reg("dolphin", "dolphin/dolphin.png", 6, 6, 8, 7, 64, 64);
        reg("bat", "bat/bat.png", 3, 10, 4, 3, 32, 32);
        reg("allay", "allay/allay.png", 5, 5, 5, 5, 32, 32);
        reg("armadillo", "armadillo/armadillo.png", 45, 17, 3, 5, 64, 64);
        reg("polar_bear", "bear/polarbear.png", 7, 7, 7, 7, 128, 64);
        reg("llama", "llama/llama_creamy.png", 8, 8, 8, 8, 128, 64);
        reg("trader_llama", "llama/llama_creamy.png", 8, 8, 8, 8, 128, 64);
        reg("parrot", "parrot/parrot_red_blue.png", 5, 5, 2, 3, 32, 32);
    }

    private MobHeadRegistry() {}

    private static void reg(String entityIdPath, String entityTexturePath, int u, int v, int w, int h, int texW, int texH) {
        reg(entityIdPath, entityTexturePath, u, v, w, h, texW, texH, -1, -1);
    }

    private static void reg(String entityIdPath, String entityTexturePath, int u, int v, int w, int h, int texW, int texH, int overlayU, int overlayV) {
        HEAD_ICONS.put(entityIdPath, new HeadIcon(
                Identifier.withDefaultNamespace("textures/entity/" + entityTexturePath),
                u, v, w, h, texW, texH, overlayU, overlayV
        ));
    }

    public static HeadIcon getIcon(Entity entity) {
        if (entity instanceof AbstractClientPlayer clientPlayer) {
            Identifier skinTex = clientPlayer.getSkin().body().texturePath();
            if (skinTex != null) {
                return new HeadIcon(skinTex, 8, 8, 8, 8, 64, 64, 40, 8);
            }
        }
        Identifier key = EntityType.getKey(entity.getType());
        if (key == null) {
            return null;
        }
        return HEAD_ICONS.get(key.getPath());
    }
}
