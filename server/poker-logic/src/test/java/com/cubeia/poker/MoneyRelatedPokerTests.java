/**
 * Copyright (C) 2010 Cubeia Ltd <info@cubeia.com>
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

package com.cubeia.poker;

import com.cubeia.poker.action.ActionRequest;
import com.cubeia.poker.action.PokerAction;
import com.cubeia.poker.action.PokerActionType;
import com.cubeia.poker.action.PossibleAction;

public class MoneyRelatedPokerTests extends GuiceTest {

	@Override
	protected void setUp() throws Exception {
		super.setUp();
	}

	public void testSmallBlindCost() {
		long startingChips = 10000;
		MockPlayer[] mp = TestUtils.createMockPlayers(4, startingChips);
		int[] p = TestUtils.createPlayerIdArray(mp);
		assertEquals(4, p.length);
		TestUtils.addPlayers(state, mp, 0);
		assertEquals(startingChips, mp[0].getBalance());

		// Force start
		state.timeout();
		
		// Blinds
//		mockServerAdapter.getActionRequest()
		act(PokerActionType.SMALL_BLIND);
		long balance = mp[2].getBalance();
		act(PokerActionType.BIG_BLIND);
		assertEquals(balance - 100, mp[2].getBalance());
		
//		System.out.println(mp[1].getBalance());
//		System.out.println(mp[3].getBalance());

		// Everyone folds and bb wins
		act(PokerActionType.FOLD);
		act(PokerActionType.FOLD);
		act(PokerActionType.FOLD);
	}
	
	private void act(PokerActionType choice) {
		ActionRequest request = mockServerAdapter.getLastActionRequest();
		PossibleAction option = request.getOption(choice);
		PokerAction action = new PokerAction(request.getPlayerId(), choice, option.getMinAmount());
		state.act(action);
	}
}
