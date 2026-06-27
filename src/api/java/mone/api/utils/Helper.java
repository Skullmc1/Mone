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

package mone.api.utils;

import mone.api.MoneAPI;
import mone.api.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Arrays;
import java.util.Calendar;
import java.util.stream.Stream;

/**
 * An ease-of-access interface to provide the {@link Minecraft} game instance,
 * chat and console logging mechanisms, and the Mone chat prefix.
 *
 */
public interface Helper {

    /**
     * Instance of {@link Helper}. Used for static-context reference.
     */
    Helper HELPER = new Helper() {};

    /**
     * The tag to assign to chat messages when {@link Settings#useMessageTag} is {@code true}.
     */
    GuiMessageTag MESSAGE_TAG = new GuiMessageTag(0xFF8800, GuiMessageTag.Icon.CHAT_MODIFIED, Component.literal("Mone message."), "Mone");

    static Component getPrefix() {
        // Inner text component
        final Calendar now = Calendar.getInstance();
        final boolean xd = now.get(Calendar.MONTH) == Calendar.APRIL && now.get(Calendar.DAY_OF_MONTH) <= 3;
        MutableComponent Mone = Component.literal(xd ? "Baritoe" : MoneAPI.getSettings().shortBaritonePrefix.value ? "B" : "Mone");
        Mone.setStyle(Mone.getStyle().withColor(ChatFormatting.GOLD));

        // Outer brackets
        MutableComponent prefix = Component.literal("");
        prefix.setStyle(Mone.getStyle().withColor(ChatFormatting.DARK_RED));
        prefix.append("[");
        prefix.append(Mone);
        prefix.append("]");

        return prefix;
    }

    /**
     * Send a message to display as a toast popup
     *
     * @param title   The title to display in the popup
     * @param message The message to display in the popup
     */
    default void logToast(Component title, Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> MoneAPI.getSettings().toaster.value.accept(title, message));
        }
    }

    /**
     * Send a message to display as a toast popup
     *
     * @param title   The title to display in the popup
     * @param message The message to display in the popup
     */
    default void logToast(String title, String message) {
        logToast(Component.literal(title), Component.literal(message));
    }

    /**
     * Send a message to display as a toast popup
     *
     * @param message The message to display in the popup
     */
    default void logToast(String message) {
        logToast(Helper.getPrefix(), Component.literal(message));
    }

    /**
     * Send a message as a desktop notification
     *
     * @param message The message to display in the notification
     */
    default void logNotification(String message) {
        logNotification(message, false);
    }

    /**
     * Send a message as a desktop notification
     *
     * @param message The message to display in the notification
     * @param error   Whether to log as an error
     */
    default void logNotification(String message, boolean error) {
        if (MoneAPI.getSettings().desktopNotifications.value) {
            logNotificationDirect(message, error);
        }
    }

    /**
     * Send a message as a desktop notification regardless of desktopNotifications
     * (should only be used for critically important messages)
     *
     * @param message The message to display in the notification
     */
    default void logNotificationDirect(String message) {
        logNotificationDirect(message, false);
    }

    /**
     * Send a message as a desktop notification regardless of desktopNotifications
     * (should only be used for critically important messages)
     *
     * @param message The message to display in the notification
     * @param error   Whether to log as an error
     */
    default void logNotificationDirect(String message, boolean error) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> MoneAPI.getSettings().notifier.value.accept(message, error));
        }
    }

    /**
     * Send a message to chat only if chatDebug is on
     *
     * @param message The message to display in chat
     */
    default void logDebug(String message) {
        if (!MoneAPI.getSettings().chatDebug.value) {
            //System.out.println("Suppressed debug message:");
            //System.out.println(message);
            return;
        }
        // We won't log debug chat into toasts
        // Because only a madman would want that extreme spam -_-
        logDirect(message, false);
    }

    /**
     * Send components to chat with the [Mone] prefix
     *
     * @param logAsToast Whether to log as a toast notification
     * @param components The components to send
     */
    default void logDirect(boolean logAsToast, Component... components) {
        MutableComponent component = Component.literal("");
        if (!logAsToast && !MoneAPI.getSettings().useMessageTag.value) {
            component.append(getPrefix());
            component.append(Component.literal(" "));
        }
        Arrays.asList(components).forEach(component::append);
        if (logAsToast) {
            logToast(getPrefix(), component);
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> MoneAPI.getSettings().logger.value.accept(component));
            }
        }
    }

    /**
     * Send components to chat with the [Mone] prefix
     *
     * @param components The components to send
     */
    default void logDirect(Component... components) {
        logDirect(MoneAPI.getSettings().logAsToast.value, components);
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a
     * direct response to a chat command)
     *
     * @param message    The message to display in chat
     * @param color      The color to print that message in
     * @param logAsToast Whether to log as a toast notification
     */
    default void logDirect(String message, ChatFormatting color, boolean logAsToast) {
        Stream.of(message.split("\n")).forEach(line -> {
            MutableComponent component = Component.literal(line.replace("\t", "    "));
            component.setStyle(component.getStyle().withColor(color));
            logDirect(logAsToast, component);
        });
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a
     * direct response to a chat command)
     *
     * @param message The message to display in chat
     * @param color   The color to print that message in
     */
    default void logDirect(String message, ChatFormatting color) {
        logDirect(message, color, MoneAPI.getSettings().logAsToast.value);
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a
     * direct response to a chat command)
     *
     * @param message    The message to display in chat
     * @param logAsToast Whether to log as a toast notification
     */
    default void logDirect(String message, boolean logAsToast) {
        logDirect(message, ChatFormatting.GRAY, logAsToast);
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a
     * direct response to a chat command)
     *
     * @param message The message to display in chat
     */
    default void logDirect(String message) {
        logDirect(message, MoneAPI.getSettings().logAsToast.value);
    }

    default void logUnhandledException(final Throwable exception) {
        HELPER.logDirect("An unhandled exception occurred. " +
                        "The error is in your game's log, please report this at https://github.com/cabaletta/Mone/issues",
                ChatFormatting.RED);
        exception.printStackTrace();
    }
}
