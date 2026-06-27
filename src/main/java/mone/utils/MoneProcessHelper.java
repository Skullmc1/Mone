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

import mone.Mone;
import mone.api.process.IMoneProcess;
import mone.api.utils.Helper;
import mone.api.utils.IPlayerContext;

public abstract class MoneProcessHelper implements IMoneProcess, Helper {

    protected final Mone Mone;
    protected final IPlayerContext ctx;

    public MoneProcessHelper(Mone Mone) {
        this.Mone = Mone;
        this.ctx = Mone.getPlayerContext();
    }

    @Override
    public boolean isTemporary() {
        return false;
    }
}
