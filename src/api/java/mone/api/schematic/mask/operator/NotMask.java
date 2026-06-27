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

package mone.api.schematic.mask.operator;

import mone.api.schematic.mask.AbstractMask;
import mone.api.schematic.mask.Mask;
import mone.api.schematic.mask.StaticMask;
import net.minecraft.world.level.block.state.BlockState;

/**
 */
public final class NotMask extends AbstractMask {

    private final Mask source;

    public NotMask(Mask source) {
        super(source.widthX(), source.heightY(), source.lengthZ());
        this.source = source;
    }

    @Override
    public boolean partOfMask(int x, int y, int z, BlockState currentState) {
        return !this.source.partOfMask(x, y, z, currentState);
    }

    public static final class Static extends AbstractMask implements StaticMask {

        private final StaticMask source;

        public Static(StaticMask source) {
            super(source.widthX(), source.heightY(), source.lengthZ());
            this.source = source;
        }

        @Override
        public boolean partOfMask(int x, int y, int z) {
            return !this.source.partOfMask(x, y, z);
        }
    }
}
