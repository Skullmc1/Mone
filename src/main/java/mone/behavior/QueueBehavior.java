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

package mone.behavior;

import mone.Mone;
import mone.api.MoneAPI;
import mone.api.event.events.TickEvent;
import mone.api.process.IMoneProcess;
import mone.api.utils.BetterBlockPos;
import mone.api.utils.Helper;
import net.minecraft.client.gui.screens.DeathScreen;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Runs a sequential queue of Mone commands (macro scripts), starting the next command
 * automatically once the previous task becomes idle.
 * Supports automatic death recovery (goto death pos -> pickup items -> resume task).
 */
public final class QueueBehavior extends Behavior implements Helper {

    private static final int IDLE_TICKS_BEFORE_NEXT = 20;

    private final Deque<String> queue = new ArrayDeque<>();
    private boolean running = false;
    private boolean paused = false;
    private String currentCommand = null;
    private int idleTicks = 0;

    // Death recovery state
    private boolean awaitingRespawn = false;
    private BetterBlockPos pendingDeathPos = null;
    private String interruptedCommand = null;

    public QueueBehavior(Mone mone) {
        super(mone);
    }

    public void addCommand(String cmd) {
        String trimmed = cmd == null ? "" : cmd.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        queue.addLast(trimmed);
        running = true;
        paused = false;
    }

    public int addChain(String semicolonSeparated) {
        if (semicolonSeparated == null || semicolonSeparated.isBlank()) {
            return 0;
        }
        int added = 0;
        for (String part : semicolonSeparated.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                queue.addLast(trimmed);
                added++;
            }
        }
        if (added > 0) {
            running = true;
            paused = false;
        }
        return added;
    }

    public void runScript(List<String> commands) {
        clear();
        for (String cmd : commands) {
            if (cmd != null && !cmd.isBlank()) {
                queue.addLast(cmd.trim());
            }
        }
        if (!queue.isEmpty()) {
            running = true;
            paused = false;
            idleTicks = IDLE_TICKS_BEFORE_NEXT;
            logDirect("[Queue] Starting script with " + queue.size() + " step(s)"
                    + (MoneAPI.getSettings().queueRecoverOnDeath.value ? " [Death Recovery ON]" : ""));
        }
    }

    public List<String> getQueuedCommands() {
        return new ArrayList<>(queue);
    }

    public String getCurrentCommand() {
        return currentCommand;
    }

    public boolean isRunning() {
        return running && !paused;
    }

    public boolean isPaused() {
        return paused;
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        if (!queue.isEmpty() || currentCommand != null) {
            running = true;
            paused = false;
            idleTicks = 0;
        }
    }

    public void skip() {
        Mone.getPathingBehavior().cancelEverything();
        currentCommand = null;
        idleTicks = IDLE_TICKS_BEFORE_NEXT;
    }

    public void clear() {
        queue.clear();
        currentCommand = null;
        running = false;
        paused = false;
        idleTicks = 0;
        awaitingRespawn = false;
        pendingDeathPos = null;
        interruptedCommand = null;
    }

    @Override
    public void onPlayerDeath() {
        if (!running || !MoneAPI.getSettings().queueRecoverOnDeath.value) {
            return;
        }
        if (ctx.player() == null) {
            return;
        }
        pendingDeathPos = ctx.playerFeet();
        interruptedCommand = currentCommand;
        awaitingRespawn = true;
        currentCommand = null;
        idleTicks = 0;
        logDirect("[Queue] Died at " + pendingDeathPos.x + ", " + pendingDeathPos.y + ", " + pendingDeathPos.z
                + ". Will recover items and resume after respawn.");
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            return;
        }
        if (!running || paused) {
            return;
        }

        if (awaitingRespawn) {
            if (ctx.player() != null && ctx.player().isAlive() && ctx.player().getHealth() > 0
                    && !(ctx.minecraft().gui.screen() instanceof DeathScreen)) {
                awaitingRespawn = false;
                if (interruptedCommand != null && !interruptedCommand.isBlank()) {
                    queue.addFirst(interruptedCommand);
                }
                if (pendingDeathPos != null) {
                    queue.addFirst("pickup");
                    queue.addFirst("goto " + pendingDeathPos.x + " " + pendingDeathPos.y + " " + pendingDeathPos.z);
                }
                logDirect("[Queue] Respawned! Heading to death location to recover items...");
                pendingDeathPos = null;
                interruptedCommand = null;
                idleTicks = IDLE_TICKS_BEFORE_NEXT;
            }
            return;
        }

        if (isMoneBusy()) {
            idleTicks = 0;
            return;
        }

        idleTicks++;
        if (idleTicks < IDLE_TICKS_BEFORE_NEXT) {
            return;
        }

        if (queue.isEmpty()) {
            if (currentCommand != null) {
                logDirect("[Queue] All queued tasks completed.");
            }
            currentCommand = null;
            running = false;
            idleTicks = 0;
            return;
        }

        String next = queue.pollFirst();
        currentCommand = next;
        idleTicks = 0;
        logDirect("[Queue] Running: " + next + " (" + queue.size() + " remaining)");
        try {
            Mone.getCommandManager().execute(next);
        } catch (Exception ex) {
            logDirect("[Queue] Command failed: " + next + " (" + ex.getMessage() + ")");
        }
    }

    private boolean isMoneBusy() {
        if (Mone.getPathingBehavior().isPathing()
                || Mone.getPathingBehavior().hasPath()
                || Mone.getPathingBehavior().getInProgress().isPresent()) {
            return true;
        }
        Optional<IMoneProcess> active = Mone.getPathingControlManager().mostRecentInControl();
        return active.isPresent() && active.get().isActive() && !active.get().isTemporary();
    }

    // --- Saved Script Presets ---

    private Path scriptsFile() {
        return Mone.getDirectory().resolve("queue_scripts.properties");
    }

    public Map<String, String> loadSavedScripts() {
        Map<String, String> res = new LinkedHashMap<>();
        Path file = scriptsFile();
        if (!Files.exists(file)) {
            return res;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
            for (String key : props.stringPropertyNames()) {
                res.put(key, props.getProperty(key));
            }
        } catch (IOException ignored) {
        }
        return res;
    }

    public void saveScript(String name, List<String> steps) {
        if (name == null || name.isBlank()) {
            return;
        }
        Map<String, String> all = loadSavedScripts();
        all.put(name.trim(), String.join(" ; ", steps));
        writeScripts(all);
    }

    public void deleteSavedScript(String name) {
        Map<String, String> all = loadSavedScripts();
        if (all.remove(name) != null) {
            writeScripts(all);
        }
    }

    private void writeScripts(Map<String, String> all) {
        Properties props = new Properties();
        props.putAll(all);
        try {
            Files.createDirectories(Mone.getDirectory());
            try (OutputStream out = Files.newOutputStream(scriptsFile())) {
                props.store(out, "Mone saved queue scripts");
            }
        } catch (IOException ignored) {
        }
    }
}
