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

package mone.command.defaults;

import mone.api.MoneAPI;
import mone.Mone;
import mone.api.IMone;
import mone.api.command.Command;
import mone.api.command.argument.IArgConsumer;
import mone.api.command.exception.CommandException;
import mone.api.command.exception.CommandInvalidStateException;
import mone.api.command.helpers.TabCompleteHelper;
import mone.api.pathing.goals.Goal;
import mone.api.process.ICustomGoalProcess;
import mone.api.process.IElytraProcess;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static mone.api.command.IMoneChatControl.FORCE_COMMAND_PREFIX;

public class ElytraCommand extends Command {

    public ElytraCommand(IMone Mone) {
        super(Mone, "elytra");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        final ICustomGoalProcess customGoalProcess = Mone.getCustomGoalProcess();
        final IElytraProcess elytra = Mone.getElytraProcess();
        if (args.hasExactlyOne() && args.peekString().equals("supported")) {
            logDirect(elytra.isLoaded() ? "yes" : unsupportedSystemMessage());
            return;
        }
        if (!elytra.isLoaded()) {
            throw new CommandInvalidStateException(unsupportedSystemMessage());
        }

        if (!args.hasAny()) {
            if (MoneAPI.getSettings().elytraTermsAccepted.value) {
                if (detectOn2b2t()) {
                    warn2b2t();
                }
            } else {
                gatekeep();
            }
            Goal iGoal = customGoalProcess.mostRecentGoal();
            if (iGoal == null) {
                throw new CommandInvalidStateException("No goal has been set");
            }
            if (ctx.world().dimension() != Level.NETHER) {
                throw new CommandInvalidStateException("Only works in the nether");
            }
            try {
                elytra.pathTo(iGoal);
            } catch (IllegalArgumentException ex) {
                throw new CommandInvalidStateException(ex.getMessage());
            }
            return;
        }

        final String action = args.getString();
        switch (action) {
            case "reset": {
                elytra.resetState();
                logDirect("Reset state but still flying to same goal");
                break;
            }
            case "repack": {
                elytra.repackChunks();
                logDirect("Queued all loaded chunks for repacking");
                break;
            }
            default: {
                throw new CommandInvalidStateException("Invalid action");
            }
        }
    }

    private void warn2b2t() {
        if (MoneAPI.getSettings().elytraPredictTerrain.value) {
            long seed = MoneAPI.getSettings().elytraNetherSeed.value;
            if (seed != NEW_2B2T_SEED && seed != OLD_2B2T_SEED) {
                logDirect(Component.literal("It looks like you're on 2b2t, but elytraNetherSeed is incorrect.")); // match color
                logDirect(suggest2b2tSeeds());
            }
        }
    }

    private Component suggest2b2tSeeds() {
        MutableComponent clippy = Component.literal("");
        clippy.append("Within a few hundred blocks of spawn/axis/highways/etc, the terrain is too fragmented to be predictable. Mone Elytra will still work, just with backtracking. ");
        clippy.append("However, once you get more than a few thousand blocks out, you should try ");
        MutableComponent olderSeed = Component.literal("the older seed (click here)");
        olderSeed.setStyle(olderSeed.getStyle().withUnderlined(true).withBold(true).withHoverEvent(new HoverEvent.ShowText(Component.literal(MoneAPI.getSettings().prefix.value + "set elytraNetherSeed " + OLD_2B2T_SEED))).withClickEvent(new ClickEvent.RunCommand(FORCE_COMMAND_PREFIX + "set elytraNetherSeed " + OLD_2B2T_SEED)));
        clippy.append(olderSeed);
        clippy.append(". Once you're further out into newer terrain generation (this includes everything up through 1.12), you should try ");
        MutableComponent newerSeed = Component.literal("the newer seed (click here)");
        newerSeed.setStyle(newerSeed.getStyle().withUnderlined(true).withBold(true).withHoverEvent(new HoverEvent.ShowText(Component.literal(MoneAPI.getSettings().prefix.value + "set elytraNetherSeed " + NEW_2B2T_SEED))).withClickEvent(new ClickEvent.RunCommand(FORCE_COMMAND_PREFIX + "set elytraNetherSeed " + NEW_2B2T_SEED)));
        clippy.append(newerSeed);
        clippy.append(". Once you get into 1.19 terrain, the terrain becomes unpredictable again, due to custom non-vanilla generation, and you should set #elytraPredictTerrain to false. ");
        return clippy;
    }

    private void gatekeep() {
        MutableComponent gatekeep = Component.literal("");
        gatekeep.append("To disable this message, enable the setting elytraTermsAccepted\n");
        gatekeep.append("Mone Elytra is an experimental feature. It is only intended for long distance travel in the Nether using fireworks for vanilla boost. It will not work with any other mods (\"hacks\") for non-vanilla boost. ");
        MutableComponent gatekeep2 = Component.literal("If you want Mone to attempt to take off from the ground for you, you can enable the elytraAutoJump setting (not advisable on laggy servers!). ");
        gatekeep2.setStyle(gatekeep2.getStyle().withHoverEvent(new HoverEvent.ShowText(Component.literal(MoneAPI.getSettings().prefix.value + "set elytraAutoJump true"))));
        gatekeep.append(gatekeep2);
        MutableComponent gatekeep3 = Component.literal("If you want Mone to go slower, enable the elytraConserveFireworks setting and/or decrease the elytraFireworkSpeed setting. ");
        gatekeep3.setStyle(gatekeep3.getStyle().withHoverEvent(new HoverEvent.ShowText(Component.literal(MoneAPI.getSettings().prefix.value + "set elytraConserveFireworks true\n" + MoneAPI.getSettings().prefix.value + "set elytraFireworkSpeed 0.6\n(the 0.6 number is just an example, tweak to your liking)"))));
        gatekeep.append(gatekeep3);
        MutableComponent gatekeep4 = Component.literal("Mone Elytra ");
        MutableComponent red = Component.literal("wants to know the seed");
        red.setStyle(red.getStyle().withColor(ChatFormatting.RED).withUnderlined(true).withBold(true));
        gatekeep4.append(red);
        gatekeep4.append(" of the world you are in. If it doesn't have the correct seed, it will frequently backtrack. It uses the seed to generate terrain far beyond what you can see, since terrain obstacles in the Nether can be much larger than your render distance. ");
        gatekeep.append(gatekeep4);
        gatekeep.append("\n");
        if (detectOn2b2t()) {
            MutableComponent gatekeep5 = Component.literal("It looks like you're on 2b2t. ");
            gatekeep5.append(suggest2b2tSeeds());
            if (!MoneAPI.getSettings().elytraPredictTerrain.value) {
                gatekeep5.append(MoneAPI.getSettings().prefix.value + "elytraPredictTerrain is currently disabled. ");
            } else {
                if (MoneAPI.getSettings().elytraNetherSeed.value == NEW_2B2T_SEED) {
                    gatekeep5.append("You are using the newer seed. ");
                } else if (MoneAPI.getSettings().elytraNetherSeed.value == OLD_2B2T_SEED) {
                    gatekeep5.append("You are using the older seed. ");
                } else {
                    gatekeep5.append("Defaulting to the newer seed. ");
                    MoneAPI.getSettings().elytraNetherSeed.value = NEW_2B2T_SEED;
                }
            }
            gatekeep.append(gatekeep5);
        } else {
            if (MoneAPI.getSettings().elytraNetherSeed.value == NEW_2B2T_SEED) {
                MutableComponent gatekeep5 = Component.literal("Mone doesn't know the seed of your world. Set it with: " + MoneAPI.getSettings().prefix.value + "set elytraNetherSeed seedgoeshere\n");
                gatekeep5.append("For the time being, elytraPredictTerrain is defaulting to false since the seed is unknown.");
                gatekeep.append(gatekeep5);
                MoneAPI.getSettings().elytraPredictTerrain.value = false;
            } else {
                if (MoneAPI.getSettings().elytraPredictTerrain.value) {
                    MutableComponent gatekeep5 = Component.literal("Mone Elytra is predicting terrain assuming that " + MoneAPI.getSettings().elytraNetherSeed.value + " is the correct seed. Change that with " + MoneAPI.getSettings().prefix.value + "set elytraNetherSeed seedgoeshere, or disable it with " + MoneAPI.getSettings().prefix.value + "set elytraPredictTerrain false");
                    gatekeep.append(gatekeep5);
                } else {
                    MutableComponent gatekeep5 = Component.literal("Mone Elytra is not predicting terrain. If you don't know the seed, this is the correct thing to do. If you do know the seed, input it with " + MoneAPI.getSettings().prefix.value + "set elytraNetherSeed seedgoeshere, and then enable it with " + MoneAPI.getSettings().prefix.value + "set elytraPredictTerrain true");
                    gatekeep.append(gatekeep5);
                }
            }
        }
        logDirect(gatekeep);
    }

    private boolean detectOn2b2t() {
        ServerData data = ctx.minecraft().getCurrentServer();
        return data != null && data.ip.toLowerCase().contains("2b2t.org");
    }

    private static final long OLD_2B2T_SEED = -4100785268875389365L;
    private static final long NEW_2B2T_SEED = 146008555100680L;

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        TabCompleteHelper helper = new TabCompleteHelper();
        if (args.hasExactlyOne()) {
            helper.append("reset", "repack", "supported");
        }
        return helper.filterPrefix(args.getString()).stream();
    }

    @Override
    public String getShortDesc() {
        return "elytra time";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The elytra command tells Mone to, in the nether, automatically fly to the current goal.",
                "",
                "Usage:",
                "> elytra - fly to the current goal",
                "> elytra reset - Resets the state of the process, but will try to keep flying to the same goal.",
                "> elytra repack - Queues all of the chunks in render distance to be given to the native library.",
                "> elytra supported - Tells you if Mone ships a native library that is compatible with your PC."
        );
    }

    private static String unsupportedSystemMessage() {
        final String osArch = System.getProperty("os.arch");
        final String osName = System.getProperty("os.name");
        return String.format(
                "Failed loading native library. Your CPU is %s and your operating system is %s. " +
                        "Supported architectures are 64 bit x86, and 64 bit ARM. Supported operating systems are Windows, " +
                        "Linux, and Mac",
                osArch, osName
        );
    }
}
