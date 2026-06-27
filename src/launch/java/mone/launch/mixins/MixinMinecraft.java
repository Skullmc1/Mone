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

import mone.api.MoneAPI;
import mone.api.IMone;
import mone.api.event.events.PlayerUpdateEvent;
import mone.api.event.events.TickEvent;
import mone.api.event.events.WorldEvent;
import mone.api.event.events.type.EventState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiFunction;

/**
 */
@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Shadow
    public LocalPlayer player;
    @Shadow
    public ClientLevel level;

    @Unique
    private BiFunction<EventState, TickEvent.Type, TickEvent> tickProvider;

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/client/gui/Gui.tick()V",
                    shift = At.Shift.AFTER
            )
    )
    private void runTick(CallbackInfo ci) {
        this.tickProvider = TickEvent.createNextProvider();

        for (IMone Mone : MoneAPI.getProvider().getAllMones()) {
            TickEvent.Type type = Mone.getPlayerContext().player() != null && Mone.getPlayerContext().world() != null
                    ? TickEvent.Type.IN
                    : TickEvent.Type.OUT;
            Mone.getGameEventHandler().onTick(this.tickProvider.apply(EventState.PRE, type));
        }
    }

    @Inject(
            method = "tick",
            at = @At("RETURN")
    )
    private void postRunTick(CallbackInfo ci) {
        if (this.tickProvider == null) {
            return;
        }

        for (IMone Mone : MoneAPI.getProvider().getAllMones()) {
            TickEvent.Type type = Mone.getPlayerContext().player() != null && Mone.getPlayerContext().world() != null
                    ? TickEvent.Type.IN
                    : TickEvent.Type.OUT;
            Mone.getGameEventHandler().onPostTick(this.tickProvider.apply(EventState.POST, type));
        }

        this.tickProvider = null;
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/client/multiplayer/ClientLevel.tickEntities()V",
                    shift = At.Shift.AFTER
            )
    )
    private void postUpdateEntities(CallbackInfo ci) {
        IMone Mone = MoneAPI.getProvider().getMoneForPlayer(this.player);
        if (Mone != null) {
            // Intentionally call this after all entities have been updated. That way, any modification to rotations
            // can be recognized by other entity code. (Fireworks and Pigs, for example)
            Mone.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(EventState.POST));
        }
    }

    @Inject(
            method = "setLevel",
            at = @At("HEAD")
    )
    private void preLoadWorld(final ClientLevel world, final CallbackInfo ci) {
        // If we're unloading the world but one doesn't exist, ignore it
        if (this.level == null && world == null) {
            return;
        }

        // mc.world changing is only the primary Mone

        IMone primary = MoneAPI.getProvider().getPrimaryMone();
        if (primary != null) {
            primary.getGameEventHandler().onWorldEvent(
                    new WorldEvent(
                            world,
                            EventState.PRE
                    )
            );
        }
    }

    @Inject(
            method = "setLevel",
            at = @At("RETURN")
    )
    private void postLoadWorld(final ClientLevel world, final CallbackInfo ci) {
        // still fire event for both null, as that means we've just finished exiting a world

        // mc.world changing is only the primary Mone
        IMone primary = MoneAPI.getProvider().getPrimaryMone();
        if (primary != null) {
            primary.getGameEventHandler().onWorldEvent(
                    new WorldEvent(
                            world,
                            EventState.POST
                    )
            );
        }
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/client/gui/Gui.screen()Lnet/minecraft/client/gui/screens/Screen;",
                    ordinal = 1
            )
    )
    private Screen passEvents(Gui gui) {
        IMone primary = MoneAPI.getProvider().getPrimaryMone();
        if (primary != null && primary.getPathingBehavior().isPathing() && player != null) {
            return null;
        }
        return gui.screen();
    }

    // TODO
    // FIXME
    // bradyfix
    // i cant mixin
    // lol
    // https://discordapp.com/channels/208753003996512258/503692253881958400/674760939681349652
    // https://discordapp.com/channels/208753003996512258/503692253881958400/674756457966862376
    /*@Inject(
            method = "rightClickMouse",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/client/entity/player/ClientPlayerEntity.swingArm(Lnet/minecraft/util/Hand;)V",
                    ordinal = 1
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onBlockUse(CallbackInfo ci, Hand var1[], int var2, int var3, Hand enumhand, ItemStack itemstack, EntityRayTraceResult rt, Entity ent, ActionResultType art, BlockRayTraceResult raytrace, int i, ActionResultType enumactionresult) {
        // rightClickMouse is only for the main player
        MoneAPI.getProvider().getPrimaryMone().getGameEventHandler().onBlockInteract(new BlockInteractEvent(raytrace.getPos(), BlockInteractEvent.Type.USE));
    }*/
}
