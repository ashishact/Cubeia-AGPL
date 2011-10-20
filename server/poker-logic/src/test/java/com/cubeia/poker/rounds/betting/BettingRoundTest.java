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

package com.cubeia.poker.rounds.betting;

import junit.framework.TestCase;

import com.cubeia.poker.MockGame;
import com.cubeia.poker.MockPlayer;
import com.cubeia.poker.TestListener;
import com.cubeia.poker.TestUtils;
import com.cubeia.poker.action.ActionRequest;
import com.cubeia.poker.action.PokerAction;
import com.cubeia.poker.action.PokerActionType;
import com.cubeia.poker.action.PossibleAction;
import com.cubeia.poker.rounds.betting.BettingRound;

public class BettingRoundTest extends TestCase implements TestListener {

	private ActionRequest requestedAction;

	private MockGame game;

	private BettingRound round;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		game = new MockGame();
		game.listeners.add(this);
		
	}

	public void testHeadsUpBetting() {
		MockPlayer[] p = TestUtils.createMockPlayers(2);

		game.addPlayers(p);
		round = new BettingRound(game, 0, new DefaultPlayerToActCalculator());

		assertFalse(game.roundFinished);

		verifyAndAct(p[1], PokerActionType.BET, 100);
		verifyAndAct(p[0], PokerActionType.FOLD, 100);

		assertTrue(round.isFinished());
	}

	public void testCallAmount() {
		MockPlayer[] p = TestUtils.createMockPlayers(2, 100);

		game.addPlayers(p);
		round = new BettingRound(game, 0, new DefaultPlayerToActCalculator());

		assertFalse(game.roundFinished);
		act(p[1], PokerActionType.BET, 70);
		
		PossibleAction bet = requestedAction.getOption(PokerActionType.CALL);
		assertEquals(70, bet.getMaxAmount());
	}	
	
	public void testRaise() {
		MockPlayer[] p = TestUtils.createMockPlayers(2);

		game.addPlayers(p);
		round = new BettingRound(game, 0, new DefaultPlayerToActCalculator());

		assertFalse(game.roundFinished);

		verifyAndAct(p[1], PokerActionType.BET, 100);
		assertTrue(requestedAction.isOptionEnabled(PokerActionType.RAISE));
		verifyAndAct(p[0], PokerActionType.RAISE, 200);
	}
	
	public void testNoRaiseAllowedWhenAllOtherPlayersAreAllIn() {
		MockPlayer[] p = TestUtils.createMockPlayers(2);

		game.addPlayers(p);
		round = new BettingRound(game, 0, new DefaultPlayerToActCalculator());

		assertFalse(game.roundFinished);

		actMax(PokerActionType.BET);
		assertFalse(requestedAction.isOptionEnabled(PokerActionType.RAISE));
	}	
	
	private void actMax(PokerActionType action) {
		PossibleAction option = requestedAction.getOption(action);
		PokerAction a = new PokerAction(requestedAction.getPlayerId(), action, option.getMaxAmount());
		round.act(a);		
	}

	public void testTimeoutTwice() {
		MockPlayer[] p = TestUtils.createMockPlayers(2);

		game.addPlayers(p);
		round = new BettingRound(game, 0, new DefaultPlayerToActCalculator());

		assertFalse(game.roundFinished);

		round.timeout();
		round.timeout();

		assertTrue(round.isFinished());
	}	
	
	public void testTimeout() {
		MockPlayer[] p = TestUtils.createMockPlayers(2);

		game.addPlayers(p);
		round = new BettingRound(game, 0, new DefaultPlayerToActCalculator());

		assertFalse(game.roundFinished);

		verifyAndAct(p[1], PokerActionType.BET, 100);
		round.timeout();
		assertTrue(round.isFinished());
	}	

	public void testDealerLeft() {
		MockPlayer[] p = TestUtils.createMockPlayers(2);

		game.addPlayers(p);
		round = new BettingRound(game, 3, new DefaultPlayerToActCalculator());

		assertFalse(game.roundFinished);

		round.timeout();
		round.timeout();

		assertTrue(round.isFinished());		
	}

	// HELPERS
	
	private void act(MockPlayer player, PokerActionType action, long amount) {
		PokerAction a = new PokerAction(player.getId(), action);
		a.setBetAmount(amount);
		round.act(a);
	}	

	private void verifyAndAct(MockPlayer player, PokerActionType action, long amount) {
		assertTrue("Tried to " + action + " but available actions were: "
				+ player.getActionRequest().getOptions(), player
				.getActionRequest().isOptionEnabled(action));
		assertTrue(requestedAction.isOptionEnabled(action));
		assertEquals(player.getId(), requestedAction.getPlayerId());
		act(player, action, amount);
	}

	public void notifyActionRequested(ActionRequest r) {
		this.requestedAction = r;
	}

}