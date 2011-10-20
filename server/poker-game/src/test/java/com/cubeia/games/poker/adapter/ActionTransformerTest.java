/**
 * Copyright (Cnu) 2010 Cubeia Ltd <info@cubeia.com>
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

package com.cubeia.games.poker.adapter;

import static com.cubeia.games.poker.adapter.ActionTransformer.convertRankToProtocolEnum;
import static com.cubeia.poker.hand.HandType.ROYAL_STRAIGHT_FLUSH;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import se.jadestone.dicearena.game.poker.network.protocol.BestHand;
import se.jadestone.dicearena.game.poker.network.protocol.CardToDeal;
import se.jadestone.dicearena.game.poker.network.protocol.DealPrivateCards;
import se.jadestone.dicearena.game.poker.network.protocol.Enums;
import se.jadestone.dicearena.game.poker.network.protocol.Enums.ActionType;
import se.jadestone.dicearena.game.poker.network.protocol.GameCard;
import se.jadestone.dicearena.game.poker.network.protocol.HandEnd;
import se.jadestone.dicearena.game.poker.network.protocol.PotTransfer;
import se.jadestone.dicearena.game.poker.network.protocol.PotTransfers;

import com.cubeia.poker.action.PokerActionType;
import com.cubeia.poker.hand.Card;
import com.cubeia.poker.hand.CardIdGenerator;
import com.cubeia.poker.hand.Hand;
import com.cubeia.poker.hand.HandType;
import com.cubeia.poker.hand.IndexCardIdGenerator;
import com.cubeia.poker.hand.Rank;
import com.cubeia.poker.hand.Suit;
import com.cubeia.poker.model.PlayerHand;
import com.cubeia.poker.model.RatedPlayerHand;
import com.cubeia.poker.player.PokerPlayer;
import com.cubeia.poker.pot.Pot;
import com.cubeia.poker.pot.PotTransition;

public class ActionTransformerTest {

    @Test
	public void testCreateHandEndPacket() {
		Hand hand1 = new Hand("As Ks");
		Hand hand2 = new Hand("Td Tc");
		
		Hand community = new Hand("Qs Js Ts 4d 2c");
		hand1.addCards(community.getCards());
		hand2.addCards(community.getCards());
		
        addIdsToCards(hand1);
        addIdsToCards(hand2);
		
		List<RatedPlayerHand> hands = new ArrayList<RatedPlayerHand>();
		hands.add(new RatedPlayerHand(new PlayerHand(11, hand1), HandType.HIGH_CARD, Card.list("As Ks Qs Js Ts")));
		hands.add(new RatedPlayerHand(new PlayerHand(22, hand2), HandType.HIGH_CARD, Card.list("Qs Js Ts Td Tc")));
		
		PotTransfers potTransfers = new PotTransfers();
        HandEnd end = ActionTransformer.createHandEndPacket(hands, potTransfers);
		
		Assert.assertEquals(2, end.hands.size());
		Assert.assertNotSame("Two High", end.hands.get(0).handType.name());
		Assert.assertNotSame("Two High", end.hands.get(1).handType.name());
		
		assertThat(end.potTransfers, is(potTransfers));
	}

	private void addIdsToCards(Hand hand) {
	    CardIdGenerator idGen = new IndexCardIdGenerator();
	    List<Card> oldCards = hand.getCards();
	    hand.clear();
	    hand.addCards(idGen.copyAndAssignIds(oldCards));
    }

    @Test
    public void testCreatePrivateVisibleCards() {
		List<Card> cards = new ArrayList<Card>();
		cards.add(new Card(0, "AS"));
		cards.add(new Card(1, "AS"));
		DealPrivateCards privateCards = ActionTransformer.createPrivateCardsPacket(1, cards, false);
		Assert.assertEquals(2, privateCards.cards.size());
		CardToDeal dealtCard = privateCards.cards.get(0);
		Assert.assertEquals(1, dealtCard.player);
		Assert.assertEquals(Enums.Rank.ACE, dealtCard.card.rank);
		Assert.assertEquals(Enums.Suit.SPADES, dealtCard.card.suit);
	}
	
    @Test
	public void testCreatePrivateHiddenCards() {
		List<Card> cards = new ArrayList<Card>();
		cards.add(new Card(0, "AS"));
		cards.add(new Card(1, "AH"));
		DealPrivateCards privateCards = ActionTransformer.createPrivateCardsPacket(1, cards, true);
		Assert.assertEquals(2, privateCards.cards.size());
		CardToDeal dealtCard = privateCards.cards.get(0);
		Assert.assertEquals(1, dealtCard.player);
		Assert.assertEquals(Enums.Rank.HIDDEN, dealtCard.card.rank);
		Assert.assertEquals(Enums.Suit.HIDDEN, dealtCard.card.suit);
		dealtCard = privateCards.cards.get(1);
		Assert.assertEquals(1, dealtCard.player);
		Assert.assertEquals(Enums.Rank.HIDDEN, dealtCard.card.rank);
		Assert.assertEquals(Enums.Suit.HIDDEN, dealtCard.card.suit);
	}
	
    @Test
	public void testTransformActionTypeToPokerActionType() {
	    assertThat("wrong number of action types, something broken?", ActionType.values().length, is(9));
        assertThat(ActionTransformer.transform(ActionType.FOLD), is(PokerActionType.FOLD));
        assertThat(ActionTransformer.transform(ActionType.CHECK), is(PokerActionType.CHECK));
        assertThat(ActionTransformer.transform(ActionType.CALL), is(PokerActionType.CALL));
        assertThat(ActionTransformer.transform(ActionType.BET), is(PokerActionType.BET));
        assertThat(ActionTransformer.transform(ActionType.BIG_BLIND), is(PokerActionType.BIG_BLIND));
        assertThat(ActionTransformer.transform(ActionType.SMALL_BLIND), is(PokerActionType.SMALL_BLIND));
        assertThat(ActionTransformer.transform(ActionType.RAISE), is(PokerActionType.RAISE));
        assertThat(ActionTransformer.transform(ActionType.DECLINE_ENTRY_BET), is(PokerActionType.DECLINE_ENTRY_BET));
        assertThat(ActionTransformer.transform(ActionType.ANTE), is(PokerActionType.ANTE));
        
        // sanity check
        for (ActionType at : ActionType.values()) {
            ActionTransformer.transform(at);
        }
	}
	
    @Test
	public void testRankConversion() {
        assertThat(Enums.Rank.values().length, is(14));
        assertThat(Rank.values().length, is(13));
        
	    assertThat(Enums.Rank.ACE,    is(convertRankToProtocolEnum(Rank.ACE  )));
        assertThat(Enums.Rank.TWO,    is(convertRankToProtocolEnum(Rank.TWO  )));
        assertThat(Enums.Rank.THREE,  is(convertRankToProtocolEnum(Rank.THREE)));
        assertThat(Enums.Rank.FOUR,   is(convertRankToProtocolEnum(Rank.FOUR )));
        assertThat(Enums.Rank.FIVE,   is(convertRankToProtocolEnum(Rank.FIVE )));
        assertThat(Enums.Rank.SIX,    is(convertRankToProtocolEnum(Rank.SIX  )));
        assertThat(Enums.Rank.SEVEN,  is(convertRankToProtocolEnum(Rank.SEVEN)));
        assertThat(Enums.Rank.EIGHT,  is(convertRankToProtocolEnum(Rank.EIGHT)));
        assertThat(Enums.Rank.NINE,   is(convertRankToProtocolEnum(Rank.NINE )));
        assertThat(Enums.Rank.TEN,    is(convertRankToProtocolEnum(Rank.TEN  )));
        assertThat(Enums.Rank.JACK,   is(convertRankToProtocolEnum(Rank.JACK )));
        assertThat(Enums.Rank.QUEEN,  is(convertRankToProtocolEnum(Rank.QUEEN)));
        assertThat(Enums.Rank.KING,   is(convertRankToProtocolEnum(Rank.KING )));
	}
	
    @Test
    public void testSuitConversion() {
        assertThat(Enums.Suit.values().length, is(4 + 1));
        assertThat(Suit.values().length, is(4));
        
        assertThat(Enums.Suit.CLUBS, is(ActionTransformer.convertSuitToProtocolEnum(Suit.CLUBS)));
        assertThat(Enums.Suit.DIAMONDS, is(ActionTransformer.convertSuitToProtocolEnum(Suit.DIAMONDS)));
        assertThat(Enums.Suit.HEARTS, is(ActionTransformer.convertSuitToProtocolEnum(Suit.HEARTS)));
        assertThat(Enums.Suit.SPADES, is(ActionTransformer.convertSuitToProtocolEnum(Suit.SPADES)));
    }
    
    @Test
    public void testHandTypeConvertaion() {
        assertThat(Enums.HandType.values().length, is(11));
        assertThat(HandType.values().length, is(11));
        
        assertThat(Enums.HandType.FLUSH, is(ActionTransformer.convertHandTypeToEnum(HandType.FLUSH)));
        assertThat(Enums.HandType.FOUR_OF_A_KIND, is(ActionTransformer.convertHandTypeToEnum(HandType.FOUR_OF_A_KIND)));
        assertThat(Enums.HandType.FULL_HOUSE, is(ActionTransformer.convertHandTypeToEnum(HandType.FULL_HOUSE)));
        assertThat(Enums.HandType.HIGH_CARD, is(ActionTransformer.convertHandTypeToEnum(HandType.HIGH_CARD)));
        assertThat(Enums.HandType.PAIR, is(ActionTransformer.convertHandTypeToEnum(HandType.PAIR)));
        assertThat(Enums.HandType.STRAIGHT, is(ActionTransformer.convertHandTypeToEnum(HandType.STRAIGHT)));
        assertThat(Enums.HandType.STRAIGHT_FLUSH, is(ActionTransformer.convertHandTypeToEnum(HandType.STRAIGHT_FLUSH)));
        assertThat(Enums.HandType.THREE_OF_A_KIND, is(ActionTransformer.convertHandTypeToEnum(HandType.THREE_OF_A_KIND)));
        assertThat(Enums.HandType.TWO_PAIR, is(ActionTransformer.convertHandTypeToEnum(HandType.TWO_PAIRS)));
        assertThat(Enums.HandType.ROYAL_STRAIGHT_FLUSH, is(ActionTransformer.convertHandTypeToEnum(HandType.ROYAL_STRAIGHT_FLUSH)));
        assertThat(Enums.HandType.UNKNOWN, is(ActionTransformer.convertHandTypeToEnum(HandType.NOT_RANKED)));
        
    }
	
    @Test
    public void testCreatePlayerAction() {
        assertThat("wrong number of poker action types, something broken?", PokerActionType.values().length, is(9));
        assertThat(ActionTransformer.createPlayerAction(PokerActionType.FOLD).type, is(ActionType.FOLD));
        assertThat(ActionTransformer.createPlayerAction(PokerActionType.CHECK).type, is(ActionType.CHECK));
        assertThat(ActionTransformer.createPlayerAction(PokerActionType.CALL).type, is(ActionType.CALL));
        assertThat(ActionTransformer.createPlayerAction(PokerActionType.BET).type, is(ActionType.BET));
        assertThat(ActionTransformer.createPlayerAction(PokerActionType.BIG_BLIND).type, is(ActionType.BIG_BLIND));
        assertThat(ActionTransformer.createPlayerAction(PokerActionType.SMALL_BLIND).type, is(ActionType.SMALL_BLIND));
        assertThat(ActionTransformer.createPlayerAction(PokerActionType.RAISE).type, is(ActionType.RAISE));
        assertThat(ActionTransformer.createPlayerAction(PokerActionType.DECLINE_ENTRY_BET).type, is(ActionType.DECLINE_ENTRY_BET));
        assertThat(ActionTransformer.createPlayerAction(PokerActionType.ANTE).type, is(ActionType.ANTE));

        // sanity check
        for (PokerActionType pat : PokerActionType.values()) {
            ActionTransformer.createPlayerAction(pat);
        }
    }	
    
    @Test
    public void testCreatePotTransferPacket() {
        PokerPlayer player = mock(PokerPlayer.class);
        int playerId = 333;
        when(player.getId()).thenReturn(playerId);
        Pot pot = mock(Pot.class);
        int potId = 23;
        when(pot.getId()).thenReturn(potId);
        long amount = 3434;
        
        PotTransition potTransition = new PotTransition(player, pot, amount );
        PotTransfer potTransferPacket = ActionTransformer.createPotTransferPacket(potTransition);
        assertThat(potTransferPacket.amount, is((int) amount));
        assertThat(potTransferPacket.playerId, is(playerId));
        assertThat(potTransferPacket.potId, is((byte) potId));
    }
    
    @Test
    public void testCreateBestHandPacket() {
        List<Card> cardsInHand = asList(new Card(1, "5H"), new Card(2, "JC"));
        BestHand createBestHandPacket = ActionTransformer.createBestHandPacket(234, ROYAL_STRAIGHT_FLUSH, cardsInHand);
        assertThat(createBestHandPacket.handType, is(Enums.HandType.ROYAL_STRAIGHT_FLUSH));
        assertThat(createBestHandPacket.cards.size(), is(2));
        assertThat(createBestHandPacket.player, is(234));
    }
    
    @Test
    public void testConvertCards() {
        List<Card> cardsInHand = asList(new Card(1, "5H"), new Card(2, "JC"));
        List<GameCard> cards = ActionTransformer.convertCards(cardsInHand);
        assertThat(cards.size(), is(2));
        
        GameCard card1 = cards.get(0);
        assertThat(card1.cardId, is(1));
        assertThat(card1.rank, is(Enums.Rank.FIVE));
        assertThat(card1.suit, is(Enums.Suit.HEARTS));

        GameCard card2 = cards.get(1);
        assertThat(card2.cardId, is(2));
        assertThat(card2.rank, is(Enums.Rank.JACK));
        assertThat(card2.suit, is(Enums.Suit.CLUBS));
    }
    
}