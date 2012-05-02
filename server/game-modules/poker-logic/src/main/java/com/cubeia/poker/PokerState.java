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
import com.cubeia.poker.adapter.HandEndStatus;
import com.cubeia.poker.adapter.ServerAdapter;
import com.cubeia.poker.hand.Card;
import com.cubeia.poker.hand.ExposeCardsHolder;
import com.cubeia.poker.hand.Rank;
import com.cubeia.poker.player.PokerPlayer;
import com.cubeia.poker.player.PokerPlayerStatus;
import com.cubeia.poker.player.SitOutStatus;
import com.cubeia.poker.pot.PotHolder;
import com.cubeia.poker.pot.PotTransition;
import com.cubeia.poker.rake.LinearRakeWithLimitCalculator;
import com.cubeia.poker.result.HandResult;
import com.cubeia.poker.result.Result;
import com.cubeia.poker.rounds.betting.BetStrategyName;
import com.cubeia.poker.rounds.blinds.BlindsInfo;
import com.cubeia.poker.sitout.SitoutCalculator;
import com.cubeia.poker.states.*;
import com.cubeia.poker.timing.Periods;
import com.cubeia.poker.timing.TimingProfile;
import com.cubeia.poker.tournament.RoundReport;
import com.cubeia.poker.util.ThreadLocalProfiler;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

import static java.util.Arrays.asList;

/**
 * This is the class that users of the poker api will interface with.
 * <p/>
 * This class is responsible for handling all poker actions.
 * <p/>
 * Also, the current state of the game can be queried from this class, to be able to send a snapshot
 * view of the game to new players.
 * <p/>
 * NOTE: The name of the class should really be Poker Game.
 * TODO: Are we breaking SRP with having these two responsibilities? How can this be fixed?
 */
public class PokerState implements Serializable, IPokerState {

    private static final Logger log = LoggerFactory.getLogger(PokerState.class);

    private static final long serialVersionUID = -7208084698542289729L;

    // TODO: The internal states should preferably not be public.
    public static final PokerGameSTM NOT_STARTED = new NotStartedSTM();

    public static final PokerGameSTM WAITING_TO_START = new WaitingToStartSTM();

    public static final PokerGameSTM PLAYING = new PlayingSTM();

    public static final PokerGameSTM SHUTDOWN = new ShutdownSTM();

    /* -------- Dependency Injection Members, initialization needed -------- */

    GameType gameType;

    /**
     * The server adapter is the layer between the server and the game logic.
     * You must set the adapter before using the game logic. The adapter is
     * declared transient, so if you serialize the game state you will need to
     * reset the server adapter.
     */
    @VisibleForTesting
    protected transient ServerAdapter serverAdapter;

    //	@VisibleForTesting
    //	protected TimingProfile timing = TimingFactory.getRegistry().getDefaultTimingProfile();

    /**
     * Identifier. May be used as seem fit.
     */
    private int id = -1;

    /**
     * Will be set to true if this is a tournament table.
     */
    private boolean tournamentTable = false;

    /**
     * Will be set if this is a tournament table.
     */
    private int tournamentId = -1;

    /**
     * Used by the server adapter layer to store state.
     */
    private Object adapterState;

    /* ------------------------- Internal Members -------------------------- */

    /**
     * Maps playerId to player
     */
    @VisibleForTesting
    protected Map<Integer, PokerPlayer> playerMap = new HashMap<Integer, PokerPlayer>();

    /**
     * Maps playerId to player during the current hand
     */
    @VisibleForTesting
    protected Map<Integer, PokerPlayer> currentHandPlayerMap = new HashMap<Integer, PokerPlayer>();

    @VisibleForTesting
    protected SortedMap<Integer, PokerPlayer> seatingMap = new TreeMap<Integer, PokerPlayer>();

    /**
     * Seatings during the current round
     */
    private SortedMap<Integer, PokerPlayer> currentHandSeatingMap = new TreeMap<Integer, PokerPlayer>();

    /**
     * We need to keep track of watchers outside of the Firebase kept state
     */
    private Set<Integer> watchers = new HashSet<Integer>();

    @VisibleForTesting
    private PokerGameSTM currentState = NOT_STARTED;

    private boolean handFinished = false;

    @VisibleForTesting
    protected PotHolder potHolder;

    private List<Card> communityCards = new ArrayList<Card>();

    /**
     * Map of external table properties. External properties are optional stuff that might be needed
     * when integrating to external systems. Session/table/tournament id's for example.
     */
    private Map<String, Serializable> externalTableProperties = new HashMap<String, Serializable>();

    @VisibleForTesting
    protected PokerSettings settings;

    private PokerPlayer lastPlayerToBeCalled;

    private long startTime;

    public PokerState() {
    }

    public String toString() {
        return "PokerState - state[" + getCurrentState() + "] type[" + gameType + "]";
    }

    @Override
    public void init(GameType gameType, PokerSettings settings) {
        this.gameType = gameType;
        this.settings = settings;
    }

    /**
     * Adds a player.
     * <p/>
     * TODO: Validation is required. Currently, a player can be seated in two seats. Possibly throw a checked exception.
     *
     * @param player
     */
    public void addPlayer(PokerPlayer player) {
        playerMap.put(player.getId(), player);
        seatingMap.put(player.getSeatId(), player);
        if (!isTournamentTable()) {
            startGame();
        }
    }

    /**
     * Starts the game if all criterias are met
     */
    private void startGame() {
        if (getCurrentState().getClass() == NOT_STARTED.getClass() && playerMap.size() > 1) {
            serverAdapter.scheduleTimeout(settings.getTiming().getTime(Periods.START_NEW_HAND));
            setCurrentState(WAITING_TO_START);
        }
    }

    public void act(PokerAction action) {
        // Check sizes of caches and log warnings
        checkWarnings();
        getCurrentState().act(action, this);
    }

    public List<Card> getCommunityCards() {
        return communityCards;
    }

    public boolean isFinished() {
        return handFinished;
    }

    public void timeout() {
        getCurrentState().timeout(this);
    }

    public boolean isPlayerSeated(int playerId) {
        return playerMap.containsKey(playerId);
    }

    public Collection<PokerPlayer> getSeatedPlayers() {
        return playerMap.values();
    }

    @Override
    public Map<Integer, PokerPlayer> getCurrentHandPlayerMap() {
        return currentHandPlayerMap;
    }

    /**
     * Returns the map of external table properties.
     *
     * @return
     */
    public Map<String, Serializable> getExternalTableProperties() {
        return externalTableProperties;
    }

    @Override
    public PokerPlayer getPlayerInCurrentHand(Integer playerId) {
        return getCurrentHandPlayerMap().get(playerId);
    }

    @Override
    public SortedMap<Integer, PokerPlayer> getCurrentHandSeatingMap() {
        return currentHandSeatingMap;
    }

    @Override
    public void sitOutPlayersMarkedForSitOutNextRound() {
        for (PokerPlayer player : playerMap.values()) {
            if (player.getSitOutNextRound()) {
                player.setSitOutStatus(SitOutStatus.SITTING_OUT);
            }
        }
    }

    /**
     * Returns the players that are sitting in and has no active buy in request to the backend.
     *
     * @return players ready to play
     */
    @VisibleForTesting
    @Override
    public Collection<PokerPlayer> getPlayersReadyToStartHand() {
        return createCopyWithNotReadyPlayersExcluded(playerMap).values();
    }

    @Override
    public int countNonFoldedPlayers() {
        int nonFolded = 0;
        for (PokerPlayer p : getCurrentHandPlayerMap().values()) {
            if (!p.hasFolded()) {
                nonFolded++;
            }
        }

        return nonFolded;
    }

    /**
     * Returns true if the player is in the set of players for the hand and
     * we are in a playing state (i.e. not playing or waiting to start will result
     * in false being returned).
     */
    @Override
    public boolean isPlayerInHand(int playerId) {
        return getCurrentHandPlayerMap().containsKey(playerId) && getCurrentState().equals(PLAYING);
    }

    public void startHand() {
        if (getPlayersReadyToStartHand().size() > 1) {
            setCurrentState(PLAYING);

            resetValuesAtStartOfHand();

            saveStartingBalances();

            currentHandSeatingMap = createCopyWithNotReadyPlayersExcluded(seatingMap);
            currentHandPlayerMap = createCopyWithNotReadyPlayersExcluded(playerMap);

            log.debug("starting new hand with players: {}", currentHandPlayerMap.keySet());

            notifyNewHand();
            notifyAllPlayerBalances();
            notifyAllHandStartPlayerStatus();

            gameType.startHand();
            startTime = System.currentTimeMillis();
        } else {
            throw new IllegalStateException("Not enough players to start hand. Was: " + getPlayersReadyToStartHand().size() + ", expected > 1. Players: "
                    + playerMap);
        }
    }

    public long getStartTime() {
        return startTime;
    }

    /**
     * Take a copy of the supplied map where all players that are not ready to start a hand excluded.
     *
     * @param map
     * @return
     */
    @VisibleForTesting
    protected SortedMap<Integer, PokerPlayer> createCopyWithNotReadyPlayersExcluded(Map<Integer, PokerPlayer> map) {
        TreeMap<Integer, PokerPlayer> treeMap = new TreeMap<Integer, PokerPlayer>();
        for (Integer pid : map.keySet()) {
            PokerPlayer pokerPlayer = map.get(pid);
            if (playerReadyToStartHand(pokerPlayer)) {
                treeMap.put(pid, pokerPlayer);
            }
        }
        return treeMap;
    }

    @VisibleForTesting
    protected boolean playerReadyToStartHand(PokerPlayer pokerPlayer) {
        return !pokerPlayer.isSittingOut()
                && !pokerPlayer.isBuyInRequestActive()
                && gameType.canPlayerAffordEntryBet(pokerPlayer, settings, false);
    }

    @VisibleForTesting
    protected void resetValuesAtStartOfHand() {
        for (PokerPlayer player : playerMap.values()) {
            player.resetBeforeNewHand();
        }

        potHolder = new PotHolder(new LinearRakeWithLimitCalculator(settings.getRakeSettings()));
        gameType.prepareNewHand();
    }

    @VisibleForTesting
    protected void saveStartingBalances() {
        for (PokerPlayer p : playerMap.values()) {
            p.setStartingBalance(p.getBalance());
        }
    }

    // TODO: This opens up for tinkering. Should we disallow this method?
    public GameType getGameType() {
        return gameType;
    }

    /**
     * Should be called by the game when a hand has finished.
     * <p/>
     * TODO: Should not be here. (The user of PokerGame has no interest in calling or seeing this method)
     * TODO: Isn't it a bit confusing that a method named "notifyX" also does important business logic?
     *
     * @param result
     * @param status
     */
    public void notifyHandFinished(HandResult result, HandEndStatus status) {
        handFinished = true;

        awardWinners(result.getResults());

        if (tournamentTable) {
            // Report round to tournament coordinator and wait for notification
            tournamentRoundReport();
        } else {
            notifyHandEnd(result, status);

            for (PokerPlayer player : playerMap.values()) {
                serverAdapter.notifyPlayerBalance(player);
            }

            serverAdapter.performPendingBuyIns(playerMap.values());

            // clean up players here and make leaving players leave and so on
            // also update the lobby
            // cleanupPlayers();
            cleanupPlayers();

            //setPlayersWithoutMoneyAsSittingOut();
            sendBuyinInfoToPlayersWithoutMoney();

            TimingProfile timing = settings.getTiming();
            log.debug("Schedule hand over timeout in: {}", timing != null ? timing.getTime(Periods.START_NEW_HAND) : 0);
            serverAdapter.scheduleTimeout(timing.getTime(Periods.START_NEW_HAND));
        }

        setCurrentState(WAITING_TO_START);

    }

    /**
     * Send to all players in the current hand that do not have enough money to pay ante
     * to buy in more
     */
    @VisibleForTesting
    protected void sendBuyinInfoToPlayersWithoutMoney() {
        for (PokerPlayer player : seatingMap.values()) {

            boolean canPlayerAffordEntryBet = gameType.canPlayerAffordEntryBet(player, settings, true);
            if (!canPlayerAffordEntryBet) {
                if (!player.isBuyInRequestActive()) {
                    notifyBuyinInfo(player.getId(), true);
                }
            }
        }
    }

    /**
     * If a player has no money left he should be set as sitting out to
     * prevent him to be included in new games.
     */
    @VisibleForTesting
    public void setPlayersWithoutMoneyAsSittingOut() {
        ThreadLocalProfiler.add("PokerState.setPlayersWithoutMoneyAsSittingOut");
        for (PokerPlayer player : seatingMap.values()) {

            boolean canPlayerAffordEntryBet = gameType.canPlayerAffordEntryBet(player, settings, true);

            if (!canPlayerAffordEntryBet) {
                playerIsSittingOut(player.getId(), SitOutStatus.SITTING_OUT);
            }
        }
    }

    public void notifyHandEnd(HandResult result, HandEndStatus status) {
        serverAdapter.notifyHandEnd(result, status);
    }


    @VisibleForTesting
    public void commitPendingBalances() {
        for (PokerPlayer player : playerMap.values()) {
            player.commitBalanceNotInHand(getMaxBuyIn());
        }
    }

    private void awardWinners(Map<PokerPlayer, Result> results) {
        for (Entry<PokerPlayer, Result> entry : results.entrySet()) {
            PokerPlayer player = entry.getKey();
            player.addChips(entry.getValue().getWinningsIncludingOwnBets());
        }
    }

    private void tournamentRoundReport() {
        RoundReport report = new RoundReport();
        for (PokerPlayer player : playerMap.values()) {
            report.setSetBalance(player.getId(), player.getBalance());
        }
        log.debug("Sending tournament round report: " + report);
        serverAdapter.reportTournamentRound(report);
    }

    public PokerGameSTM getGameState() {
        return getCurrentState();
    }

    /**
     * TODO: Should not be here. (The user of PokerGame has no interest in calling or seeing this method)
     * Also: This should encapsulated so it cannot be tinkered with.
     */
    public void setState(PokerGameSTM state) {
        this.setCurrentState(state);
    }

    public void removePlayer(PokerPlayer player) {
        removePlayer(player.getId());
    }

    public void removePlayer(int playerId) {
        PokerPlayer removed = playerMap.remove(playerId);
        if (removed != null) {
            seatingMap.remove(removed.getSeatId());
        }
    }

    public PokerPlayer getPokerPlayer(int playerId) {
        return playerMap.get(playerId);
    }

    // TODO: Should not be possible to call like this. The game type should only be possible to change between hands.
    public void setGameType(GameType gameType) {
        log.debug("setting gametype = " + gameType + " to state: " + this);
        this.gameType = gameType;
    }

    // TODO: Should not be public.
    public BlindsInfo getBlindsInfo() {
        return gameType.getBlindsInfo();
    }

    public TimingProfile getTimingProfile() {
        return settings.getTiming();
    }

//    @Override
//    public PokerVariant getPokerVariant() {
//        return settings.getVariant();
//    }

    public int getTableSize() {
        return settings.getTableSize();
    }

    @Override
    public void callOrRaise() {
        potHolder.callOrRaise();
    }

    public boolean isTournamentTable() {
        return tournamentTable;
    }

    // TODO: Refactor to inheritance.
    public void setTournamentTable(boolean tournamentTable) {
        this.tournamentTable = tournamentTable;
    }

    public int getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(int tournamentId) {
        this.tournamentId = tournamentId;
    }

    /**
     * Called by the adapter layer when a player leaves or disconnects.
     *
     * @param playerId
     */
    public void playerIsSittingOut(int playerId, SitOutStatus status) {

        if (isTournamentTable()) {
            log.debug("won't sit out tournament player");
            return;
        }

        log.debug("player {} is sitting out", playerId);

        PokerPlayer player = playerMap.get(playerId);
        if (player == null) return;

        if (player.isSittingOut()) {
            return;
        }

        player.setSitOutStatus(status);
        player.setSitOutNextRound(true);
        notifyPlayerSittingOut(playerId);

        // if we declined ante or did not pay ante
        // then we should be removed from the current hand- and seating-map
        if (status == SitOutStatus.MISSED_ANTE) {
            currentHandPlayerMap.remove(playerId);
            int seatId = player.getSeatId();
            currentHandSeatingMap.remove(seatId);
        }

    }

    /**
     * Called by the adapter layer when a player rejoins/reconnects.
     *
     * @param playerId
     */
    public void playerIsSittingIn(int playerId) {

        log.debug("player {} is sitting in", playerId);

        PokerPlayer player = playerMap.get(playerId);
        if (player == null) {
            log.warn("player {} not at table but tried to sit in. Ignoring.", playerId);
            return;
        }

        if (!player.isSittingOut()) {
            log.debug("sitin status has not changed");
            return;
        }


        if (gameType.canPlayerAffordEntryBet(player, settings, true)) {
            log.debug("Player {} can afford ante. Sit in", player);

            player.sitIn();
            player.setSitOutNextRound(false);
            player.setSitInAfterSuccessfulBuyIn(false);
            notifyPlayerSittingIn(playerId);

            // Check if we are waiting for this player (could be a reconnect)
            // if so then re-send the action request

            startGame();
        } else {
            log.debug("player {} is out of cash, must bring more before joining", player);

            if (!player.isBuyInRequestActive() && player.getRequestedBuyInAmount() == 0L) {
                log.debug("player {} does not have buy in request active so notify buy in info", player);
                notifyBuyinInfo(playerId, true);
            }
        }

    }

    /**
     * Expose all pocket cards for players still in the hand
     * i.e. not folded. Will set a flag so that sequential calls
     * will not generate any outgoing packets.
     */
    public void exposeShowdownCards() {
        if (countNonFoldedPlayers() > 1) {
            ExposeCardsHolder holder = new ExposeCardsHolder();
            for (PokerPlayer p : getCurrentHandSeatingMap().values()) {
                if (!p.hasFolded() && !p.isExposingPocketCards()) {
                    // exposePrivateCards(p.getId(), p.getPrivatePocketCards());
                    holder.setExposedCards(p.getId(), p.getPrivatePocketCards());
                    p.setExposingPocketCards(true);
                }
            }

            if (holder.hasCards()) {
                exposePrivateCards(holder);
            }
        }
    }

    public boolean hasAllPlayersExposedCards() {

        if (countNonFoldedPlayers() > 1) {
            for (PokerPlayer p : getCurrentHandSeatingMap().values()) {
                if (!p.hasFolded() && !p.isExposingPocketCards()) {
                    return false;
                }
            }
        }

        return true;
    }

    /*------------------------------------------------

         SERVER ADAPTER METHODS

         These methods propagate to the server adapter.
         The nature of the methods is that they
         demand communication with the player(s).

         // TODO: None of these methods should be public here. Instead, inject the server adapter into classes
                  that need to call the server adapter.

      ------------------------------------------------*/


    public void notifyNewHand() {
        serverAdapter.notifyNewHand();
    }

    public void requestAction(ActionRequest r) {
        r.setTimeToAct(getTimingProfile().getTime(Periods.ACTION_TIMEOUT));
        r.setTotalPotSize(getTotalPotsize());
        log.debug("Send player action request [" + r + "]");
        serverAdapter.requestAction(r);
    }

    /**
     * takes all players bet stacks and sums it to the pot
     *
     * @return sum of the size of all pots commited to the main or side pots
     */
    @VisibleForTesting
    protected long getTotalPotsize() {
        long totalPot = potHolder.getTotalPotSize();

        for (PokerPlayer player : getCurrentHandPlayerMap().values()) {
            totalPot += player.getBetStack();
        }

        return totalPot;
    }

    public void requestMultipleActions(Collection<ActionRequest> requests) {
        for (ActionRequest request : requests) {
            request.setTimeToAct(getTimingProfile().getTime(Periods.ACTION_TIMEOUT));
            request.setTotalPotSize(getTotalPotsize());
        }
        serverAdapter.requestMultipleActions(requests);
    }

    public void notifyCommunityCards(List<Card> cards) {
        serverAdapter.notifyCommunityCards(cards);
    }

    public void notifyPrivateCards(int playerId, List<Card> cards) {
        serverAdapter.notifyPrivateCards(playerId, cards);
    }

    public void notifyPrivateExposedCards(int playerId, List<Card> cards) {
        serverAdapter.notifyPrivateExposedCards(playerId, cards);
    }

    //	public void exposePrivateCards(int playerId, Collection<Card> cards) {
    //		serverAdapter.exposePrivateCards(playerId, cards);
    //	}

    private void exposePrivateCards(ExposeCardsHolder holder) {
        serverAdapter.exposePrivateCards(holder);
    }

    public void notifyPlayerBalance(int playerId) {
        serverAdapter.notifyPlayerBalance(playerMap.get(playerId));
    }

    public void notifyAllPlayerBalances() {
        for (PokerPlayer player : seatingMap.values()) {
            notifyPlayerBalance(player.getId());
        }
    }


    public void notifyTakeBackUncalledBets(int playerId, long amount) {
        serverAdapter.notifyTakeBackUncalledBet(playerId, (int) amount);
    }

    /**
     * Notify everyone about hand start status.
     */
    public void notifyAllHandStartPlayerStatus() {
        for (PokerPlayer player : seatingMap.values()) {
            if (player.isSittingOut()) {
                serverAdapter.notifyHandStartPlayerStatus(player.getId(), PokerPlayerStatus.SITOUT);
            } else {
                serverAdapter.notifyHandStartPlayerStatus(player.getId(), PokerPlayerStatus.SITIN);
            }
        }
    }


    /**
     * TODO: Make this method private and change calls to this method from the BlindsRound
     * to use playerIsSittingOut instead to have one common way to set a player as sitout.
     */
    public void notifyPlayerSittingOut(int playerId) {
        log.debug("notifyPlayerSittingOut() id: " + playerId + " status:" + PokerPlayerStatus.SITOUT.name());
        boolean isInCurrentHand = isPlayerInHand(playerId);
        serverAdapter.notifyPlayerStatusChanged(playerId, PokerPlayerStatus.SITOUT, isInCurrentHand);
    }

    public void notifyPlayerSittingIn(int playerId) {
        log.debug("notifyPlayerSittingIn() id: " + playerId + " status:" + PokerPlayerStatus.SITIN.name());
        boolean isInCurrentHand = isPlayerInHand(playerId);
        serverAdapter.notifyPlayerStatusChanged(playerId, PokerPlayerStatus.SITIN, isInCurrentHand);
    }

    public void setHandFinished(boolean finished) {
        handFinished = finished;
    }

    /**
     * Removes all disconnected players from the table
     */
    public void cleanupPlayers() {
        // Clean up players in states not accessible to the poker logic
        serverAdapter.cleanupPlayers(new SitoutCalculator());
    }

    @Override
    public void notifyBetStacksUpdated() {
        notifyRakeInfo();
    }

    public void notifyPotAndRakeUpdates(Collection<PotTransition> potTransitions) {
        serverAdapter.notifyPotUpdates(potHolder.getPots(), potTransitions);

        // notify all the new balances
        for (PokerPlayer player : currentHandPlayerMap.values()) {
            serverAdapter.notifyPlayerBalance(player);
        }
        notifyRakeInfo();
    }

    public void notifyRakeInfo() {
        serverAdapter.notifyRakeInfo(potHolder.calculateRakeIncludingBetStacks(getCurrentHandSeatingMap().values()));
    }

    /**
     * TODO: Should not be here. (The user of PokerGame has no interest in calling or seeing this method)
     */
    public void notifyDealerButton(int dealerButtonSeatId) {
        serverAdapter.notifyDealerButton(dealerButtonSeatId);
    }


    public ServerAdapter getServerAdapter() {
        return serverAdapter;
    }

    public void setServerAdapter(ServerAdapter serverAdapter) {
        this.serverAdapter = serverAdapter;
    }

    // TODO: Refactor. The holder of this instance can create a new class which holds this instance together with other data.
    public Object getAdapterState() {
        return adapterState;
    }

    // TODO: Refactor. The holder of this instance can create a new class which holds this instance together with other data.
    public void setAdapterState(Object adapterState) {
        this.adapterState = adapterState;
    }

    public void unseatPlayer(int playerId, boolean setAsWatcher) {
        serverAdapter.unseatPlayer(playerId, setAsWatcher);
    }

    /*------------------------------------------------

         END OF SERVER ADAPTER METHODS

      ------------------------------------------------*/


    // TODO: Refactor. The holder of this instance can create a new class which holds this instance together with other data.
    public int getId() {
        return id;
    }

    // TODO: Refactor. The holder of this instance can create a new class which holds this instance together with other data.
    public void setId(int id) {
        this.id = id;
    }

    public String getStateDescription() {
        return getCurrentState().getClass().getName() + "_" + gameType.getStateDescription();
    }

    /**
     * Adds chips to a player. If the player is in a hand, the chips will be
     * added after the hand if finished.
     *
     * @param playerId
     * @param chips
     * @return <code>true</code> if the chips were added immediately,
     *         <code>false</code> if they will be added when the hand is
     *         finished.
     */
    public void addChips(int playerId, long chips) {
        if (!playerMap.containsKey(playerId)) {
            throw new IllegalArgumentException("Player " + playerId + " tried to add chips, but was not seated.");
        }

        if (isPlayerInHand(playerId)) {
            // Add pending chips request.
        } else {
            playerMap.get(playerId).addChips(chips);
        }
    }

    public int getBalance(int playerId) {
        return (int) playerMap.get(playerId).getBalance();
    }

    public PotHolder getPotHolder() {
        return potHolder;
    }

    public int getAnteLevel() {
        return settings.getAnteLevel();
    }

    //	public void setAnteLevel(int anteLevel) {
    //		this.anteLevel = anteLevel;
    //	}

    public int getEntryBetLevel() {
        return settings.getEntryBetLevel();
    }

    public int getMinBuyIn() {
        return settings.getMinBuyIn();
    }

    public int getMaxBuyIn() {
        return settings.getMaxBuyIn();
    }

    public BetStrategyName getBetStrategyName() {
        return settings.getBetStrategy();
    }

    private void checkWarnings() {
        if (playerMap.size() > 20) {
            log.warn("PLAYER MAP SIZE WARNING. Size=" + playerMap.size() + ", Values: " + playerMap);
        }
        if (seatingMap.size() > 20) {
            log.warn("SEATING MAP SIZE WARNING. Size=" + seatingMap.size() + ", Values: " + seatingMap);
        }
    }

    public void notifyDeckInfo(int size, Rank rankLow) {
        serverAdapter.notifyDeckInfo(size, rankLow);
    }

    public boolean removeAsWatcher(int playerId) {
        return watchers.remove(playerId);
    }

    public void addWatcher(int playerId) {
        watchers.add(playerId);
    }

    public void notifyBuyinInfo(int playerId, boolean mandatoryBuyin) {
        serverAdapter.notifyBuyInInfo(playerId, mandatoryBuyin);
    }

    public void notifyNewRound() {
        serverAdapter.notifyNewRound();
    }

    public PokerSettings getSettings() {
        return settings;
    }

    public PokerPlayer getLastPlayerToBeCalled() {
        return lastPlayerToBeCalled;
    }

    public void setLastPlayerToBeCalled(PokerPlayer lastPlayerToBeCalled) {
        this.lastPlayerToBeCalled = lastPlayerToBeCalled;
    }

    public PokerPlayer getPlayerAtDealerButton() {
        if (getCurrentHandSeatingMap() == null || getCurrentHandSeatingMap().isEmpty()) {
            throw new IllegalStateException("no current hand seating map when getting player at dealer button");
        }
        return getCurrentHandSeatingMap().get(getBlindsInfo().getDealerButtonSeatId());
    }

    @Override
    public void shutdown() {
        log.debug("Shutting down table {}", getId());
        setCurrentState(SHUTDOWN);
    }

    @Override
    public boolean isShutdown() {
        return SHUTDOWN.equals(getCurrentState());
    }

    @Override
    public boolean isPlaying() {
        return PLAYING.equals(getCurrentState());
    }

    protected PokerGameSTM getCurrentState() {
        return currentState;
    }

    protected void setCurrentState(PokerGameSTM newState) {
        if (SHUTDOWN.equals(getCurrentState()) && !newState.equals(getCurrentState())) {
            throw new UnsupportedOperationException("attempted illegal state change from SHUTDOWN -> " + newState);
        }
        this.currentState = newState;
    }

    private int getNumberOfAllinPlayers() {

        int counter = 0;

        for (PokerPlayer pokerPlayer : getCurrentHandPlayerMap().values()) {
            if (pokerPlayer.isAllIn() || pokerPlayer.hasFolded()) {
                ++counter;
            }
        }

        return counter;

    }

    public boolean isAtLeastAllButOneAllIn() {
        return getNumberOfAllinPlayers() >= currentHandPlayerMap.size() - 1;
    }

    public long getPlayersTotalContributionToPot(PokerPlayer player) {
        if (potHolder != null) {
            return potHolder.calculatePlayersContributionToPotIncludingBetStacks(player);
        } else {
            return player.getBetStack();
        }
    }

    @VisibleForTesting
    public Set<PokerPlayer> getMuckingPlayers() {
        HashSet<PokerPlayer> muckers = new HashSet<PokerPlayer>();

        boolean allButOneOrAllFolded = countNonFoldedPlayers() <= 1;
        if (allButOneOrAllFolded) {
            muckers.addAll(getCurrentHandPlayerMap().values());
        } else {
            for (PokerPlayer player : getCurrentHandPlayerMap().values()) {
                if (player.hasFolded()) {
                    muckers.add(player);
                }
            }
        }

        return muckers;
    }

    @Override
    public void handleBuyInRequest(PokerPlayer pokerPlayer, int amount) {
        pokerPlayer.addRequestedBuyInAmount(amount);

        if (asList(WAITING_TO_START, NOT_STARTED).contains(getCurrentState())) {
            log.debug("game is not started, performing immediate reserve request for player: {}", pokerPlayer.getId());
            getServerAdapter().performPendingBuyIns(Collections.singleton(pokerPlayer));
        }
    }

    public boolean isWaitingForPlayerToAct(int playerId) {
        return getCurrentState().isCurrentlyWaitingForPlayer(this, playerId);
    }

    @Override
    public boolean isEveryoneSittingOut() {
        boolean everyoneIsSittingOut = true;
        Map<Integer, PokerPlayer> allCurrentPlayers = getCurrentHandPlayerMap();
        for (PokerPlayer player : allCurrentPlayers.values()) {
            everyoneIsSittingOut &= player.isSittingOut();
        }
        return everyoneIsSittingOut;
    }


}
