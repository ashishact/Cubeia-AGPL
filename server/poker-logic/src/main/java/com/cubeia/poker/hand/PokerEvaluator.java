package com.cubeia.poker.hand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.cubeia.poker.hand.calculator.HandCalculator;
import com.cubeia.poker.hand.calculator.TexasHoldemHandCalculator;
import com.cubeia.poker.model.PlayerHand;


/**
 * <p>Compares poker hands.</p>
 * 
 * <p>This is a 'naive' implementation of a poker evaluator (and hand calculator),
 * it is not built for speed but with usability in mind. If you need a faster 
 * implementation I suggest looking at the University of Alberta implementation.</p>
 *  
 *
 * @author Fredrik Johansson, Cubeia Ltd
 */
public class PokerEvaluator  {
	
    // TODO: should be injected
	private HandCalculator calc = new TexasHoldemHandCalculator();
	
    // TODO: should be injected
    private HandComparator handComparator;
	
	/**
	 * <p>Rank the hands with the best hand strength at the first position.</p>
	 * 
	 * <p>If a hand have more than 5 cards then the best possible hand strength will be
	 * extracted.</p> 
	 * 
	 * @param hands
	 * @return
	 */
	public List<PlayerHand> rankHands(Collection<PlayerHand> hands) {
		List<PlayerHand> rankedHands = new ArrayList<PlayerHand>(hands);
		for (PlayerHand playerHand : rankedHands) {
			HandStrength handStrength;
			Hand hand = playerHand.getHand();
			
			// Check if we have a 5 card hand
			if (hand.getCards().size() == 5) {
				handStrength = calc.getHandStrength(hand);
			} else if (hand.getCards().size() > 5) {
				// More than 5 cards, we need to check all combinations
				handStrength = getBestCombinationHandStrength(hand);
			} else {
			    handStrength = new HandStrength(HandType.NOT_RANKED);
			}
			
			hand.setHandStrength(handStrength);
		}
		
		handComparator = new HandComparator();
		Collections.sort(rankedHands, new Comparator<PlayerHand>() {
		    @Override
		    public int compare(PlayerHand o1, PlayerHand o2) {
		        return handComparator.compare(o1.getHand(), o2.getHand());
		    }
		});
		
		return rankedHands;
	}

	/**
	 * Get all possible hand combinations and rank them.
	 *  
	 * @param hand with more than 5 cards
	 * @return the best HandStrength found.
	 */
	protected HandStrength getBestCombinationHandStrength(Hand hand) {
		List<HandStrength> allPossibleHands = new ArrayList<HandStrength>();
		Combinator<Card> combinator = new Combinator<Card>(hand.getCards(), 5);
		for (List<Card> cards : combinator) {
			HandStrength handStrength = calc.getHandStrength(new Hand(cards));
			allPossibleHands.add(handStrength);
		}
		Collections.sort(allPossibleHands, new HandStrengthComparator());
		return allPossibleHands.get(0);
	}
}
