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

package com.cubeia.poker.pot;

import static com.cubeia.poker.pot.Pot.PotType.MAIN;
import static com.cubeia.poker.pot.Pot.PotType.SIDE;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.hasItems;

import java.util.Arrays;
import java.util.Collection;

import junit.framework.TestCase;

import com.cubeia.poker.player.DefaultPokerPlayer;
import com.cubeia.poker.player.PokerPlayer;

public class PotTest extends TestCase {
	private static int counter = 0;

	public void testSimpleCase() {
		PokerPlayer p1 = createPokerPlayer(20);
		PokerPlayer p2 = createPokerPlayer(20);

		PotHolder potHolder = new PotHolder();
		potHolder.moveChipsToPot(Arrays.asList(p1, p2));
		assertEquals(1, potHolder.getNumberOfPots());
		assertEquals(40, potHolder.getTotalPotSize());
		assertEquals(40, potHolder.getPotSize(0));
	}

	private PokerPlayer createPokerPlayer(int amountBet) {
		return createPokerPlayer(amountBet, false);
	}
	
	private PokerPlayer createPokerPlayer(int amountBet, Boolean allIn) {
		PokerPlayer p = new DefaultPokerPlayer(counter ++);
		if ( allIn ) {
			p.addChips(amountBet);
		} else {
			p.addChips(5 * amountBet);
		}
		p.addBet(amountBet);
		return p;
	}

	public void testSimpleCaseWithFold() {
		PokerPlayer p1 = createPokerPlayer(20);
		PokerPlayer p2 = createPokerPlayer(20);
		PokerPlayer p3 = createPokerPlayer(10);
		p3.setHasFolded(true);

		PotHolder potHolder = new PotHolder();
		Collection<PotTransition> potTransitions = potHolder.moveChipsToPot(asList(p1, p2, p3));
		assertEquals(1, potHolder.getNumberOfPots());
		assertEquals(50, potHolder.getTotalPotSize());
		assertEquals(50, potHolder.getPotSize(0));
		
		assertThat(potTransitions.size(), is(3));
		Pot pot = potHolder.getPot(0);
        assertThat(potTransitions, hasItems(
            new PotTransition(p1, pot, 20),
            new PotTransition(p2, pot, 20),
            new PotTransition(p3, pot, 10)));
	}

	public void testOneSidePot() {
		PokerPlayer p1 = createPokerPlayer(20);
		PokerPlayer p2 = createPokerPlayer(20);
		PokerPlayer p3 = createPokerPlayer(10, true);

		PotHolder potHolder = new PotHolder();
		Collection<PotTransition> potTransitions = potHolder.moveChipsToPot(Arrays.asList(p1, p2, p3));
		assertEquals(2, potHolder.getNumberOfPots());
		assertEquals(50, potHolder.getTotalPotSize());
		assertEquals(30, potHolder.getPotSize(0));
		assertEquals(20, potHolder.getPotSize(1));
		assertEquals(3, potHolder.getPot(0).getPotContributors().size());
		assertEquals(2, potHolder.getPot(1).getPotContributors().size());
		
		// Transitions:
		// p1: 10 -> main pot, 10 -> side pot
		// p2: 10 -> main pot, 10 -> side pot
		// p3: 10 -> main pot
        assertThat(potTransitions.size(), is(5));
        Pot mainPot = potHolder.getPot(0);
        Pot sidePot = potHolder.getPot(1);
        
        assertThat(mainPot.getType(), is(MAIN));
        assertThat(sidePot.getType(), is(SIDE));
        assertThat(potTransitions, hasItems(
            new PotTransition(p1, mainPot, 10),
            new PotTransition(p2, mainPot, 10),
            new PotTransition(p3, mainPot, 10),
            new PotTransition(p1, sidePot, 10),
            new PotTransition(p2, sidePot, 10)));
	}

	public void testTwoSidePots() {
		PotHolder potHolder = createPotWithSidePots();
		assertEquals(3, potHolder.getNumberOfPots());
		assertEquals(60, potHolder.getTotalPotSize());
		assertEquals(20, potHolder.getPotSize(0));
		assertEquals(30, potHolder.getPotSize(1));
		assertEquals(10, potHolder.getPotSize(2));
	}
	
	public void testTwoSidePots2() {
		PokerPlayer p1 = createPokerPlayer(5, true);
		PokerPlayer p2 = createPokerPlayer(10);
		PokerPlayer p3 = createPokerPlayer(8, true);
		PokerPlayer p4 = createPokerPlayer(10);
		PokerPlayer p5 = createPokerPlayer(2);

		PotHolder potHolder = new PotHolder();
		Collection<PotTransition> potTransitions = potHolder.moveChipsToPot(Arrays.asList(p1, p2, p3, p4, p5));
		assertEquals(3, potHolder.getNumberOfPots());
		assertEquals(35, potHolder.getTotalPotSize());
		assertEquals(22, potHolder.getPotSize(0));
		assertEquals(9, potHolder.getPotSize(1));
		assertEquals(4, potHolder.getPotSize(2));
		
        // Transitions:
        // p1:  5 -> main pot
        // p2:  5 -> main pot, 3 -> side pot 1, 2 -> side pot 2
        // p3:  5 -> main pot, 3 -> side pot 1
        // p4:  5 -> main pot, 3 -> side pot 1, 2 -> side pot 2
        // p5:  2 -> main pot
        assertThat(potTransitions.size(), is(10));
        Pot mainPot = potHolder.getPot(0);
        Pot sidePot1 = potHolder.getPot(1);
        Pot sidePot2 = potHolder.getPot(2);
        
        assertThat(mainPot.getType(), is(MAIN));
        assertThat(sidePot1.getType(), is(SIDE));
        assertThat(sidePot2.getType(), is(SIDE));
        assertThat(potTransitions, hasItems(
            new PotTransition(p1, mainPot,  5),
            new PotTransition(p2, mainPot,  5),
            new PotTransition(p2, sidePot1, 3),
            new PotTransition(p2, sidePot2, 2),
            new PotTransition(p3, mainPot,  5),
            new PotTransition(p3, sidePot1, 3),
            new PotTransition(p4, mainPot,  5),
            new PotTransition(p4, sidePot1, 3),
            new PotTransition(p4, sidePot2, 2),
            new PotTransition(p5, mainPot,  2)));
	}


	public void testConsecutiveMoves() {
		PotHolder potHolder = createPotWithSidePots();
		assertEquals(3, potHolder.getNumberOfPots());
		assertEquals(60, potHolder.getTotalPotSize());
		assertEquals(20, potHolder.getPotSize(0));
		assertEquals(30, potHolder.getPotSize(1));
		assertEquals(10, potHolder.getPotSize(2));

		PokerPlayer p1 = createPokerPlayer(20);
		PokerPlayer p2 = createPokerPlayer(20);
		// Second round
		potHolder.moveChipsToPot(Arrays.asList(p1, p2));
		assertEquals(3, potHolder.getNumberOfPots());
		assertEquals(100, potHolder.getTotalPotSize());
		assertEquals(50, potHolder.getPotSize(2));
	}

	public void testReturnUnCalledChips() {
		PokerPlayer p1 = createPokerPlayer(5);
		PokerPlayer p2 = createPokerPlayer(10);

		PotHolder potHolder = new PotHolder();
		potHolder.moveChipsToPot(Arrays.asList(p1, p2));
		assertEquals(5, p2.getReturnedChips());
	}

	public void testReturnUnCalledChipsAfterFold() {
		PokerPlayer p1 = createPokerPlayer(0);
		PokerPlayer p2 = createPokerPlayer(10);

		PotHolder potHolder = new PotHolder();
		potHolder.moveChipsToPot(Arrays.asList(p1, p2));
		assertEquals(10, p2.getReturnedChips());
	}

	public void testRake() {
		PotHolder p = new PotHolder();
		p.addPot(5L);
		p.addPot(20L);

		p.rake(10);
		assertEquals(5, p.getRakeAmount());
		assertEquals(0, p.getPot(0).getPotSize());
		assertEquals(25, p.getTotalPotSize());
	}

	public void testRakeNothing() {
		PotHolder p = new PotHolder();
		p.rake(0);
	}
	
	public void testPotId() {
		PotHolder potHolder = createPotWithSidePots();
		int i = 0;
		for (Pot p : potHolder.getPots()) {
			assertEquals(i++, p.getId());
		}
	}

	private PotHolder createPotWithSidePots() {
		PokerPlayer p1 = createPokerPlayer(20);
		PokerPlayer p2 = createPokerPlayer(20);
		PokerPlayer p3 = createPokerPlayer(15, true);
		PokerPlayer p4 = createPokerPlayer(5, true);

		PotHolder potHolder = new PotHolder();
		potHolder.moveChipsToPot(Arrays.asList(p1, p2, p3, p4));
		return potHolder;
	}
	

}
