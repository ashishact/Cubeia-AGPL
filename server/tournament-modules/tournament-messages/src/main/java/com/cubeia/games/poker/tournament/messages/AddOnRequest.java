/**
 * Copyright (C) 2012 Cubeia Ltd <info@cubeia.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.cubeia.games.poker.tournament.messages;

import java.io.Serializable;

public class AddOnRequest implements Serializable {

    private final int tableId;
    private final int playerId;

    public AddOnRequest(int tableId, int playerId) {
        this.tableId = tableId;
        this.playerId = playerId;
    }

    public int getPlayerId() {
        return playerId;
    }

    public int getTableId() {
        return tableId;
    }
}
