package com.cubeia.poker.hand;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>Simple playing card.</p>
 * 
 * <p>This class implements Comparable which will sort the cards according
 * to rank first and suit secondly. The suits are sorted according to the
 * ordinals of the suits as defined in the Suit enum class.</p> 
 * 
 * @author Fredrik Johansson, Cubeia Ltd
 */
public class Card implements Serializable {
    private static final long serialVersionUID = 9039935616385887536L;

    private final Suit suit;
	
	private final Rank rank;
	
	private final Integer id;
	
	/**
	 * Needed by Jboss serialization
	 */
	@SuppressWarnings("unused")
    private Card() {
	    this.rank = null;
	    this.suit = null;
	    this.id = null;
	}
	
	/**
	 * Creates an anonymous card.
	 * @param rank the rank
	 * @param suit the suid
	 */
	public Card(Rank rank, Suit suit) {
		this.rank = rank;
		this.suit = suit;
		this.id = null;
	}
	
	/**
	 * Creates an identifiable card.
	 * @param id id of the card
	 * @param rank the rank
	 * @param suit the suit
	 */
    public Card(Integer id, Rank rank, Suit suit) {
        this.rank = rank;
        this.suit = suit;
        this.id = id;
    }
	
	/**
	 * Shorthand value, 
	 * e.g. AS = Ace of Spades, 5c = Five of Clubs etc.
	 * 
	 * @param s
	 */
	public Card(String s) {
	    this(null, s);
	}
	
	public Card(Integer id, String s) {
        this.rank = Rank.fromShortString(s.charAt(0));
        this.suit = Suit.fromShortString(s.charAt(1));
        this.id = id;
    }

	/**
	 * @see #Card(String)
	 * @param s
	 * @return
	 */
	public static Card fromString(String s) {
	    return new Card(s);
	}

	/**
	 * Convenience method to create a list of cards.
	 * 
	 * @param str whitespace separated list of 2 character card descriptors.
	 * @return
	 */
	public static List<Card> list(String str) {
		List<Card> result = new LinkedList<Card>();
		for (String s : str.split(" ")) {
			result.add(new Card(s));
		}
		
		return result;
	}
	
    public Card makeCopyWithId(int id) {
        return new Card(id, rank, suit);
    }
	
    /**
     * Returns a copy of this card with the id stripped (nulled).
     * @return card with id set to null
     */
    public Card makeCopyWithoutId() {
        return new Card(null, rank, suit);
    }
    
    /**
     * Copy the given collection of cards and strip their id:s.
     * @param cards cards to copy
     * @return collection of cards with id:s set to null
     */
    public static List<Card> makeCopyWithoutIds(Collection<Card> cards) {
        ArrayList<Card> cardsCopy = new ArrayList<Card>();
        for (Card c : cards) {
            cardsCopy.add(c.makeCopyWithoutId());
        }
        return cardsCopy;
    }
    
    public String toString() {
		return rank.toShortString()+suit.toShortString();
	}
	
	public Rank getRank() {
		return rank;
	}
	
	public Suit getSuit() {
		return suit;
	}
	
	public Integer getId() {
        return id;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((rank == null) ? 0 : rank.hashCode());
        result = prime * result + ((suit == null) ? 0 : suit.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Card other = (Card) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (rank != other.rank)
            return false;
        if (suit != other.suit)
            return false;
        return true;
    }

}
