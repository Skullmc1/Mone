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

package mone;

import mone.api.IMone;
import mone.api.IMoneProvider;
import mone.api.cache.IWorldScanner;
import mone.api.command.ICommandSystem;
import mone.api.schematic.ISchematicSystem;
import mone.cache.FasterWorldScanner;
import mone.command.CommandSystem;
import mone.Mone;
import mone.command.ExampleMoneControl;
import mone.utils.schematic.SchematicSystem;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 */
public final class MoneProvider implements IMoneProvider {

    private final List<IMone> all;
    private final List<IMone> allView;

    public MoneProvider() {
        this.all = new CopyOnWriteArrayList<>();
        this.allView = Collections.unmodifiableList(this.all);
    }

    @Override
    public IMone getPrimaryMone() {
        if (this.all.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                synchronized (this) {
                    if (this.all.isEmpty()) {
                        final Mone primary = (Mone) this.createMone(mc);
                        primary.registerBehavior(ExampleMoneControl::new);
                    }
                }
            } else {
                return null;
            }
        }
        return this.all.get(0);
    }

    @Override
    public List<IMone> getAllMones() {
        return this.allView;
    }

    @Override
    public synchronized IMone createMone(Minecraft minecraft) {
        IMone Mone = this.getMoneForMinecraft(minecraft);
        if (Mone == null) {
            this.all.add(Mone = new Mone(minecraft));
        }
        return Mone;
    }

    @Override
    public synchronized boolean destroyMone(IMone Mone) {
        return Mone != this.getPrimaryMone() && this.all.remove(Mone);
    }

    @Override
    public IWorldScanner getWorldScanner() {
        return FasterWorldScanner.INSTANCE;
    }

    @Override
    public ICommandSystem getCommandSystem() {
        return CommandSystem.INSTANCE;
    }

    @Override
    public ISchematicSystem getSchematicSystem() {
        return SchematicSystem.INSTANCE;
    }
}
