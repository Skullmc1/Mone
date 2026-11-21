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
import mone.api.event.events.TickEvent;
import mone.api.process.IMoneProcess;
import mone.api.utils.Helper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Runs a sequential queue of Mone commands (macro chains), starting the next command
 * automatically once the previous task becomes idle.
 */
public final class QueueBehavior extends Behavior implements Helper {

    private static final int IDLE_TICKS_BEFORE_NEXT = 20;

    private final Deque<String> queue = new ArrayDeque<>();
    private boolean running = false;
    private boolean paused = false;
    private String currentCommand = null;
    private int idleTicks = 0;

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
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            return;
        }
        if (!running || paused) {
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
}
