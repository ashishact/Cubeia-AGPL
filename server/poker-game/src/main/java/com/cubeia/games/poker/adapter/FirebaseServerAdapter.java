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

package com.cubeia.games.poker.adapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.jadestone.dicearena.game.poker.network.protocol.BestHand;
import se.jadestone.dicearena.game.poker.network.protocol.DealPrivateCards;
import se.jadestone.dicearena.game.poker.network.protocol.DealPublicCards;
import se.jadestone.dicearena.game.poker.network.protocol.DealerButton;
import se.jadestone.dicearena.game.poker.network.protocol.DeckInfo;
import se.jadestone.dicearena.game.poker.network.protocol.Enums;
import se.jadestone.dicearena.game.poker.network.protocol.ExposePrivateCards;
import se.jadestone.dicearena.game.poker.network.protocol.HandEnd;
import se.jadestone.dicearena.game.poker.network.protocol.PerformAction;
import se.jadestone.dicearena.game.poker.network.protocol.PlayerBalance;
import se.jadestone.dicearena.game.poker.network.protocol.PlayerPokerStatus;
import se.jadestone.dicearena.game.poker.network.protocol.Pot;
import se.jadestone.dicearena.game.poker.network.protocol.PotTransfer;
import se.jadestone.dicearena.game.poker.network.protocol.PotTransfers;
import se.jadestone.dicearena.game.poker.network.protocol.RequestAction;
import se.jadestone.dicearena.game.poker.network.protocol.StartNewHand;

import com.cubeia.backoffice.accounting.api.Money;
import com.cubeia.firebase.api.action.GameAction;
import com.cubeia.firebase.api.action.GameDataAction;
import com.cubeia.firebase.api.action.GameObjectAction;
import com.cubeia.firebase.api.action.mtt.MttRoundReportAction;
import com.cubeia.firebase.api.common.AttributeValue;
import com.cubeia.firebase.api.game.context.GameContext;
import com.cubeia.firebase.api.game.lobby.LobbyTableAttributeAccessor;
import com.cubeia.firebase.api.game.player.GenericPlayer;
import com.cubeia.firebase.api.game.player.PlayerStatus;
import com.cubeia.firebase.api.game.table.Table;
import com.cubeia.firebase.api.game.table.TableType;
import com.cubeia.firebase.api.service.ServiceRegistry;
import com.cubeia.firebase.api.util.UnmodifiableSet;
import com.cubeia.games.poker.FirebaseState;
import com.cubeia.games.poker.PokerGame;
import com.cubeia.games.poker.cache.ActionCache;
import com.cubeia.games.poker.handler.Trigger;
import com.cubeia.games.poker.handler.TriggerType;
import com.cubeia.games.poker.jmx.PokerStats;
import com.cubeia.games.poker.logic.TimeoutCache;
import com.cubeia.games.poker.model.PokerPlayerImpl;
import com.cubeia.games.poker.persistence.history.HandHistoryDAO;
import com.cubeia.games.poker.persistence.history.model.EventType;
import com.cubeia.games.poker.persistence.history.model.PlayedHand;
import com.cubeia.games.poker.persistence.history.model.PlayedHandEvent;
import com.cubeia.games.poker.tournament.PokerTournamentRoundReport;
import com.cubeia.games.poker.util.ProtocolFactory;
import com.cubeia.games.poker.util.WalletAmountConverter;
import com.cubeia.network.wallet.firebase.api.WalletServiceContract;
import com.cubeia.network.wallet.firebase.domain.ResultEntry;
import com.cubeia.network.wallet.firebase.domain.RoundResultResponse;
import com.cubeia.poker.PokerState;
import com.cubeia.poker.action.ActionRequest;
import com.cubeia.poker.action.PokerAction;
import com.cubeia.poker.adapter.HandEndStatus;
import com.cubeia.poker.adapter.ServerAdapter;
import com.cubeia.poker.hand.Card;
import com.cubeia.poker.hand.HandType;
import com.cubeia.poker.model.RatedPlayerHand;
import com.cubeia.poker.player.PokerPlayer;
import com.cubeia.poker.player.PokerPlayerStatus;
import com.cubeia.poker.pot.PotTransition;
import com.cubeia.poker.result.HandResult;
import com.cubeia.poker.result.Result;
import com.cubeia.poker.timing.Periods;
import com.cubeia.poker.tournament.RoundReport;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

/**
 * Firebase implementation of the poker logic's server adapter.
 *
 * @author Fredrik Johansson, Cubeia Ltd
 */
public class FirebaseServerAdapter implements ServerAdapter {

	private static Logger log = LoggerFactory.getLogger(FirebaseServerAdapter.class);

    private WalletAmountConverter amountConverter = new WalletAmountConverter();
    
	@Inject
	ActionCache cache;
	
	@Inject
	GameContext gameContext;

	@Inject
	Table table;
	
	@Inject 
	PokerState state;
	
	@VisibleForTesting
	protected ProtocolFactory protocolFactory = new ProtocolFactory();
	
    int handCount;

//    @Inject
//    private PokerCEPService pokerCepService;
	
	
	/*------------------------------------------------
	 
		ADAPTER METHODS
		
		These methods are the adapter interface
		implementations

	 ------------------------------------------------*/
	
    @Override
	public void notifyNewHand() {
	    PlayedHand playedHand = new PlayedHand();
	    playedHand.setTableId(table.getId());
	    playedHand.setEvents(new HashSet<PlayedHandEvent>());
	    getFirebaseState().setPlayerHand(playedHand);
        
	    StartNewHand packet = new StartNewHand();
	    GameDataAction action = protocolFactory.createGameAction(packet, 0, table.getId());
	    log.debug("--> Send StartNewHand["+packet+"] to everyone");
		sendPublicPacket(action, -1);
		
        log.debug("Starting new hand. FBPlayers: "+table.getPlayerSet().getPlayerCount()+", PokerPlayers: "+state.getSeatedPlayers().size());
    }

    @Override
	public void notifyDealerButton(int seat) {
		DealerButton packet = new DealerButton();
		packet.seat = (byte)seat;
		GameDataAction action = protocolFactory.createGameAction(packet, 0, table.getId());
		log.debug("--> Send DealerButton["+packet+"] to everyone");
		sendPublicPacket(action, -1);
		
		addEventToHandHistory(seat, EventType.DEALER_BUTTON, null);
	}
	
    @Override
	public void requestAction(ActionRequest request) {
		RequestAction packet = ActionTransformer.transform(request);
		GameDataAction action = protocolFactory.createGameAction(packet, request.getPlayerId(), table.getId());
		log.debug("--> Send RequestAction["+packet+"] to everyone");
		sendPublicPacket(action, -1);
		setRequestSequence(packet.seq, packet.player);
		
		// Schedule timeout inc latency grace period
		long latency = state.getTimingProfile().getTime(Periods.LATENCY_GRACE_PERIOD);
		schedulePlayerTimeout(request.getTimeToAct()+latency, request.getPlayerId(), packet.seq);
	}

    @Override
    public void scheduleTimeout(long millis) {
		GameObjectAction action = new GameObjectAction(table.getId());
		TriggerType type = TriggerType.TIMEOUT;
        Trigger timeout = new Trigger(type); 
        timeout.setSeq(-1);
		action.setAttachment(timeout);
		table.getScheduler().scheduleAction(action, millis);
		setRequestSequence(-1, 0);
	}
	
    @Override
	public void notifyActionPerformed(PokerAction pokerAction) {
		PokerPlayer pokerPlayer = state.getPokerPlayer(pokerAction.getPlayerId());
		PerformAction packet = ActionTransformer.transform(pokerAction, pokerPlayer);
		GameDataAction action = protocolFactory.createGameAction(packet, pokerAction.getPlayerId(), table.getId());
		log.debug("--> Send PerformAction["+packet+"] to everyone");
		sendPublicPacket(action, -1);
		addEventToHandHistory(pokerAction);
	}


    @Override
    public void notifyCommunityCards(List<Card> cards) {
		DealPublicCards packet = ActionTransformer.createPublicCardsPacket(cards);
		GameDataAction action = protocolFactory.createGameAction(packet, 0, table.getId());
		log.debug("--> Send DealPublicCards["+packet+"] to everyone");
		sendPublicPacket(action, -1);
	}

	
    @Override
	public void notifyPrivateCards(int playerId, List<Card> cards) {
		// Send the cards to the owner with proper rank & suit information
		DealPrivateCards packet = ActionTransformer.createPrivateCardsPacket(playerId, cards, false);
		GameDataAction action = protocolFactory.createGameAction(packet, playerId, table.getId());
		log.debug("--> Send DealPrivateCards["+packet+"] to player["+playerId+"]");
		sendPrivatePacket(playerId, action);
		
		// Send the cards as hidden to the other players
		DealPrivateCards hiddenCardsPacket = ActionTransformer.createPrivateCardsPacket(playerId, cards, true);
		GameDataAction ntfyAction = protocolFactory.createGameAction(hiddenCardsPacket, playerId, table.getId());
		log.debug("--> Send DealPrivateCards(hidden)["+hiddenCardsPacket+"] to everyone");
		sendPublicPacket(ntfyAction, playerId);
	}
    
    @Override
    public void notifyBestHand(int playerId, HandType handType, List<Card> cardsInHand) {
        BestHand bestHandPacket = ActionTransformer.createBestHandPacket(playerId, handType, cardsInHand);
        GameDataAction bestHandAction = protocolFactory.createGameAction(bestHandPacket, playerId, table.getId());
        log.debug("--> Send BestHandPacket["+bestHandPacket+"] to player["+playerId+"]");
        table.getNotifier().notifyPlayer(playerId, bestHandAction);
    }

	@Override
	public void notifyPrivateExposedCards(int playerId, List<Card> cards) {
        // Send the cards as public to the other players
        DealPrivateCards hiddenCardsPacket = ActionTransformer.createPrivateCardsPacket(playerId, cards, false);
        GameDataAction ntfyAction = protocolFactory.createGameAction(hiddenCardsPacket, playerId, table.getId());
        log.debug("--> Send DealPrivateCards(exposed)["+hiddenCardsPacket+"] to everyone");
        sendPublicPacket(ntfyAction, -1);
	}
	
    @Override
	public void exposePrivateCards(int playerId, List<Card> cards) {
		ExposePrivateCards packet = ActionTransformer.createExposeCardsPacket(playerId, cards);
		GameDataAction action = protocolFactory.createGameAction(packet, playerId, table.getId());
		log.debug("--> Send ExposePrivateCards["+packet+"] to everyone");
		sendPublicPacket(action, playerId);
	}

    @Deprecated
    @Override
	public void notifyPlayerBalanceReset(PokerPlayer player) {
		notifyPlayerBalance(player);
	}

    @Override
	public void notifyHandEnd(HandResult handResult, HandEndStatus handEndStatus) {
		if (handEndStatus.equals(HandEndStatus.NORMAL) && handResult != null) {
			try {
			    List<PlayerBalance> balances = new ArrayList<PlayerBalance>();
			    
				// Handle wins and losses. Talk to wallet.
//				Collection<ResultEntry> resultEntries = new ArrayList<ResultEntry>();
//				Map<PokerPlayer, Result> results = handResult.getResults();
//				for (PokerPlayer p : results.keySet()) {
////					GameDataAction action = ActionTransformer.createPlayerBalanceAction((int)p.getBalance(), p.getId(), table.getId());
////					table.getNotifier().notifyAllPlayers(action);
//					
//				    balances.add(new PlayerBalance((int) p.getBalance(), p.getId()));
//					
////					long sessionId = ((PokerPlayerImpl) p).getSessionId();
//					Result result = results.get(p);
//					// FIXME: Hardcoded currency code
//					ResultEntry entry = new ResultEntry(sessionId, amountConverter.convertToWalletAmount(result.getNetResult()), PokerGame.CURRENCY_CODE);
//					resultEntries.add(entry);
//				}
				
				List<PotTransfer> transfers = new ArrayList<PotTransfer>();
				for (PotTransition pt : handResult.getPotTransitions()) {
				    transfers.add(ActionTransformer.createPotTransferPacket(pt));
				}
                PotTransfers potTransfers = new PotTransfers(false, transfers, null, balances);
				
//				WalletServiceContract walletService = getServices().getServiceInstance(WalletServiceContract.class);
				
				// TODO: Change to use doTransaction(...) instead of deprecated method roundResult(...)
//				RoundResultResponse roundResult = walletService.roundResult(
//				     -1l, (long) PokerGame.POKER_GAME_ID, (long) table.getId(), resultEntries, 
//				     createRoundReportDescription(handEndStatus));
//				validateWalletBalances(roundResult);
				
				// TODO: The following logic should be moved to poker-logic
				// I.e. ranking hands etc do not belong in the game-layer
				Collection<RatedPlayerHand> hands = handResult.getPlayerHands();
                HandEnd packet = ActionTransformer.createHandEndPacket(hands, potTransfers);
				GameDataAction action = protocolFactory.createGameAction(packet, 0, table.getId());
				log.debug("--> Send HandEnd["+packet+"] to everyone");
				sendPublicPacket(action, -1);
				
				PokerStats.getInstance().reportHandEnd();
				
				// Remove all idling players
				cleanupPlayers();
				writeHandHistory(hands, handEndStatus);
				updateLobby();
				
			} catch (Throwable e) {
				log.error("FAIL when reporting hand results", e);
			}
			
		} else {
			log.info("The hand was cancelled on table: " + table.getId() + " - " + table.getMetaData().getName());
			// TODO: The hand was cancelled... do something!
			cleanupPlayers();
		}
		
        clearActionCache();
	}

    private void clearActionCache() {
        if (cache != null) {
        	cache.clear(table.getId());
        }
    }

	/**
	 * Creates a simple textual description of the hand to be used in the round report.
	 * @param handEndStatus status of the hand
	 * @return the description
	 */
    private String createRoundReportDescription(HandEndStatus handEndStatus) {
        return "Pokerhand, table[" + table.getId() + "]";
    }
	
    private String createPlayerBalanceResetDescription(int playerId) {
    	return "Resetting balance for pid["+playerId+"]";
    }
    
    @Override
	public void notifyPlayerBalance(PokerPlayer p) {
		if (p == null) return;
		
	    GameDataAction action = ActionTransformer.createPlayerBalanceAction((int)p.getBalance(), p.getId(), table.getId());
	    sendPublicPacket(action, 0);
	}
	
	
    private void validateWalletBalances(RoundResultResponse roundResult) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Sends a poker tournament round report to the tournament as set in the table meta-data.
	 * 
	 * @param report, poker-logic protocol object, not null.
	 */
	public void reportTournamentRound(RoundReport report) {
	    PokerStats.getInstance().reportHandEnd();
	    
	    // Map the report to a server specific round report
        PokerTournamentRoundReport pokerReport = new PokerTournamentRoundReport(report.getBalanceMap());
        MttRoundReportAction action = new MttRoundReportAction(table.getMetaData().getMttId(), table.getId());
        action.setAttachment(pokerReport);
        table.getTournamentNotifier().sendToTournament(action);
        clearActionCache();
    }
	
	
	public void updatePots(Collection<com.cubeia.poker.pot.Pot> pots, Collection<PotTransition> potTransitions) {
	    boolean fromPlayerToPot = !potTransitions.isEmpty()  &&  potTransitions.iterator().next().isFromPlayerToPot();
	    List<PlayerBalance> balances = new ArrayList<PlayerBalance>();
	    List<Pot> clientPots = new ArrayList<Pot>();
	    List<PotTransfer> transfers = new ArrayList<PotTransfer>();
	    
		for (com.cubeia.poker.pot.Pot pot : pots) {
			clientPots.add(ActionTransformer.createPotUpdatePacket(pot.getId(), pot.getPotSize()));
		}
		
		for (PotTransition potTransition : potTransitions) {
		    log.debug("sending pot update to client: {}", potTransition);
		    transfers.add(ActionTransformer.createPotTransferPacket(potTransition));
		}
		
		for (PokerPlayer player : state.getCurrentHandPlayerMap().values()) {
		    balances.add(new PlayerBalance((int) player.getBalance(), player.getId()));
		}
		
        PotTransfers potTransfers = new PotTransfers(fromPlayerToPot, transfers, clientPots, balances);
        GameDataAction action = protocolFactory.createGameAction(potTransfers, 0, table.getId());
        sendPublicPacket(action, -1);
    }

    @Override
	public void notifyPlayerStatusChanged(int playerId, PokerPlayerStatus status) {
		PlayerPokerStatus packet = new PlayerPokerStatus();
		packet.player = playerId;
		switch (status) {
			case ALLIN:
				packet.status = Enums.PlayerTableStatus.ALLIN;
				break;
			case NORMAL:
				packet.status = Enums.PlayerTableStatus.NORMAL;
				break;
			case SITOUT:
				packet.status = Enums.PlayerTableStatus.SITOUT;
				break;
		}
		GameDataAction action = protocolFactory.createGameAction(packet, playerId, table.getId());
        sendPublicPacket(action, -1);
	}
	
	/*------------------------------------------------
	 
		PRIVATE METHODS
		
	 ------------------------------------------------*/
	
	/**
	 * Schedule a player timeout trigger command.
	 * @param seq 
	 */
	public void schedulePlayerTimeout(long millis, int pid, int seq) {
        GameObjectAction action = new GameObjectAction(table.getId());
        TriggerType type = TriggerType.PLAYER_TIMEOUT;
        Trigger timeout = new Trigger(type, pid);
        timeout.setSeq(seq);
        action.setAttachment(timeout);
        UUID actionId = table.getScheduler().scheduleAction(action, millis);
        TimeoutCache.getInstance().addTimeout(table.getId(), pid, actionId);
    }
	
	/**
	 * Remove all players in state LEAVING or DISCONNECTED
	 */
	public void cleanupPlayers() {
	    if (table.getMetaData().getType().equals(TableType.NORMAL)) {
            UnmodifiableSet<GenericPlayer> players = table.getPlayerSet().getPlayers();
            for (GenericPlayer p : players) {
                if (p.getStatus() == PlayerStatus.DISCONNECTED || p.getStatus() == PlayerStatus.LEAVING) {

                	log.debug("Cleanup - unseat player["+p.getPlayerId()+"] from table["+table.getId()+"]");
                	table.getPlayerSet().unseatPlayer(p.getPlayerId());
                	table.getListener().playerLeft(table, p.getPlayerId());
                }
            }
	    }
    }

    /**
	 * This action will be cached and used for sending current state to 
	 * joining players.
	 * 
	 * If skipPlayerId is -1 then no player will be skipped.
	 * 
	 * @param action
	 * @param skipPlayerId
	 */
	private void sendPublicPacket(GameAction action, int skipPlayerId) {
		if (skipPlayerId < 0) {
			table.getNotifier().notifyAllPlayers(action);
		} else {
			table.getNotifier().notifyAllPlayersExceptOne(action, skipPlayerId);
		}
		// Add to state cache
		if (cache != null) {
			cache.addPublicAction(table.getId(), action);
		}
	}
	
    /**
     * Send private packet to player and cache it as private. The cached action
     * will be sent to the player when rejoining.
     * 
     * @param playerId player id
     * @param action action
     */
    private void sendPrivatePacket(int playerId, GameAction action) {
        table.getNotifier().notifyPlayer(playerId, action);
            
        if (cache != null) {
            cache.addPrivateAction(table.getId(), playerId, action);
        }
    }


	private FirebaseState getFirebaseState() {
		return (FirebaseState)state.getAdapterState();
	}
	
    private void setRequestSequence(int seq, int player) {
    	getFirebaseState().setCurrentRequestSequence(seq);
    }
    
    
    /**
     * FIXME: No real implementation below!
     * 
     * @param hands
     * @param handEndStatus
     */
    private void writeHandHistory(Collection<RatedPlayerHand> hands, HandEndStatus handEndStatus) {
        if (getServices() != null) {
        	try {
	            HandHistoryDAO dao = new HandHistoryDAO(getServices());
	            dao.persist(getFirebaseState().getPlayerHand());
        	} catch (Exception e) {
        		log.error("Failed to persist hand history", e);
        	}
        } else {
            log.warn("Services is null when trying to persist");
        }
    }


    private ServiceRegistry getServices() {
		return gameContext.getServices();
	}




	private void updateLobby() {
        FirebaseState fbState = (FirebaseState)state.getAdapterState();
        handCount = fbState.getHandCount();
        handCount++;
        LobbyTableAttributeAccessor lobbyTable = table.getAttributeAccessor();
        lobbyTable.setAttribute("handcount", new AttributeValue(handCount));
        fbState.setHandCount(handCount);
    }

    /**
     * FIXME: PlayerHand has been removed. We need a new approach to hand history
     * 
     * @param pid
     * @param type
     * @param bet
     */
    private void addEventToHandHistory(int pid, EventType type, Long bet) {
//    	try {
//	        PlayedHandEvent event = new PlayedHandEvent();
//	        event.setPlayerId(pid);
//	        event.setType(type);
//	        event.setBet(bet);
//	        fbState.getPlayerHand().getEvents().add(event);
//    	} catch (Exception e) {
//    		log.error("Failed to add event to hand history pid["+pid+"], type["+type+"], bet["+bet+"], fbstate.playerhand["+fbState.getPlayerHand()+"]", e);
//    	}
    }
    
    /**
     * FIXME: PlayerHand has been removed. We need a new approach to hand history
     * 
     */
    private void addEventToHandHistory(PokerAction action) {
//    	try {
//	        PlayedHandEvent event = new PlayedHandEvent();
//	        event.setPlayerId(action.getPlayerId());
//	        event.setType(EventTypeTransformer.transform(action.getActionType()));
//	        event.setBet(action.getBetAmount());
//	        fbState.getPlayerHand().getEvents().add(event);
//    	} catch (Exception e) {
//    		log.error("Failed to add event to hand history action["+action+"], fbstate.playerhand["+fbState.getPlayerHand()+"]", e);
//    	}
    }
    
    @Override
    public void notifyDeckInfo(int size, com.cubeia.poker.hand.Rank rankLow) {
        DeckInfo deckInfoPacket = new DeckInfo(size, ActionTransformer.convertRankToProtocolEnum(rankLow));
        GameDataAction action = protocolFactory.createGameAction(deckInfoPacket, 0, table.getId());
        sendPublicPacket(action, -1);
    }
}
