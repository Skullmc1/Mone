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

package mone.launch.mixins;

import mone.utils.MinimapRenderer;
import mone.utils.SettingsGuiScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(
            method = "keyPress",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        // action == 1 is GLFW_PRESS
        if (action == 1
                && this.minecraft != null
                && this.minecraft.player != null
                && this.minecraft.level != null
                && this.minecraft.gui.screen() == null) {
            if (event.key() == MinimapRenderer.resolveFullscreenKeyCode()) {
                MinimapRenderer.openFullscreenMap();
                ci.cancel();
            } else if (event.key() == SettingsGuiScreen.resolveSettingsKeyCode()) {
                SettingsGuiScreen.open();
                ci.cancel();
            }
        }
    }
}
