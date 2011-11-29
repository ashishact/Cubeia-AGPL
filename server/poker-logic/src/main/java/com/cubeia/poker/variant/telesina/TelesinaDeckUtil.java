package com.cubeia.poker.variant.telesina;

import static com.google.common.collect.ImmutableSet.copyOf;
import static com.google.common.collect.Sets.cartesianProduct;
import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;

import com.cubeia.poker.hand.Card;
import com.cubeia.poker.hand.Rank;
import com.cubeia.poker.hand.Suit;
import com.sun.java.swing.plaf.nimbus.LoweredBorder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

public class TelesinaDeckUtil {

	private TelesinaDeckUtil() { }
    
    public static final String DEFAULT_40_CARDS_DECK_FILE = "telesina40c.txt";
	public static String DECK_40_CARDS_FILE = "telesina40c.txt";
    public static final String DEFAULT_32_CARDS_DECK_FILE = "telesina32c.txt";
	public static String DECK_32_CARDS_FILE = "telesina32c.txt";
	
	public static Rank calculateLowestRank(int participants) {
		int firstRankIndex = Math.max(0, 11 - participants - 2);
    	return Rank.values()[firstRankIndex];
	}
	
	public static List<Card> createDeckCards(int participants) {
		return createDeckCards(calculateLowestRank(participants));
	}
	
	@SuppressWarnings("unchecked")
    public static List<Card> createDeckCards(Rank lowestRank) {
        ArrayList<Card> cards = new ArrayList<Card>();
        List<Rank> ranks = asList(Rank.values()).subList(lowestRank.ordinal(), Rank.values().length);
        for (List<Enum<?>> cardContainer : cartesianProduct(copyOf(Suit.values()), copyOf(ranks))) {
            Suit suit = (Suit) cardContainer.get(0);
            Rank rank = (Rank) cardContainer.get(1);
            cards.add(new Card(rank, suit));
        }
        return cards;
    }
    
    public static List<Card> createDeckFromString(int participants, String deck) {
        int deckLength;
        if(participants == 4){
            deckLength = 32;
        }else if(participants == 6){
            deckLength = 40;
        }else{
            throw new IllegalArgumentException(participants+" participants rigged deck is not supported for the moment");
        }
        if (deck == null || deck.length() != deckLength*2) {
			throw new RuntimeException("deck file doesn't contain the correct amount of cards! is "+deck.length()+" and should be "+deckLength+" for "+participants+" participants");
		}
        //preparing cards
        Rank lowestRank = calculateLowestRank(participants);
        List<Card> sortedDeck = createDeckCards(lowestRank);
        HashMap<String, Card> cardMap = new HashMap<String, Card>();
        for(Card card : sortedDeck){
            cardMap.put(card.toString(), card);
        }
		List<Card> riggedDeck = new ArrayList<Card>();
		for (int i = 0; i < deck.length(); i+=2) {
			String cardString = deck.substring(i,i+2);
            Card card = cardMap.remove(cardString);
            if(card != null){
                riggedDeck.add(new Card(i, cardString));
            }else{
                throw new RuntimeException("the card "+cardString+" is not in the deck ... please check that you are not "
                        + "reusing cards and that all your cards are at least of rank "+lowestRank.name());
            }
		}
		return riggedDeck;
	}

}
