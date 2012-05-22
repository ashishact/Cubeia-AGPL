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

package com.cubeia.poker.variant.texasholdem;

import com.cubeia.poker.PokerSettings;
import com.cubeia.poker.action.ActionRequest;
import com.cubeia.poker.action.ActionRequestFactory;
import com.cubeia.poker.action.PokerAction;
import com.cubeia.poker.action.PokerActionType;
import com.cubeia.poker.adapter.HandEndStatus;
import com.cubeia.poker.hand.*;
import com.cubeia.poker.player.PokerPlayer;
import com.cubeia.poker.pot.PotTransition;
import com.cubeia.poker.result.HandResult;
import com.cubeia.poker.result.RevealOrderCalculator;
import com.cubeia.poker.rng.RNGProvider;
import com.cubeia.poker.rounds.Round;
import com.cubeia.poker.rounds.RoundHelper;
import com.cubeia.poker.rounds.RoundVisitor;
import com.cubeia.poker.rounds.ante.AnteRound;
import com.cubeia.poker.rounds.betting.BettingRound;
import com.cubeia.poker.rounds.betting.DefaultPlayerToActCalculator;
import com.cubeia.poker.rounds.betting.NoLimitBetStrategy;
import com.cubeia.poker.rounds.blinds.BlindsRound;
import com.cubeia.poker.rounds.dealing.*;
import com.cubeia.poker.timing.Periods;
import com.cubeia.poker.util.HandResultCalculator;
import com.cubeia.poker.variant.AbstractGameType;
import com.cubeia.poker.variant.HandResultCreator;
import org.apache.log4j.Logger;

import java.util.*;

public class TexasHoldem extends AbstractGameType implements RoundVisitor, Dealer {

    private static final long serialVersionUID = -1523110440727681601L;

    private static transient Logger log = Logger.getLogger(TexasHoldem.class);

    private Round currentRound;

    private Deck deck;

    /**
     * 0 = pre flop 1 = flop 2 = turn 3 = river
     */
    private int roundId;

    private final RNGProvider rngProvider;

    private HandResultCalculator handResultCalculator = new HandResultCalculator(new TexasHoldemHandCalculator());

    private RoundHelper roundHelper;

    public TexasHoldem(RNGProvider rngProvider) {
        this.rngProvider = rngProvider;
    }

    @Override
    public String toString() {
        return "TexasHoldem, current round[" + currentRound + "] roundId[" + roundId + "] ";
    }

    @Override
    public void startHand() {
        initHand();
    }

    private void initHand() {
        deck = new StandardDeck(new Shuffler<Card>(rngProvider.getRNG()), new IndexCardIdGenerator());

        currentRound = new BlindsRound(context, serverAdapterHolder);
        roundId = 0;
    }

    @Override
    public void act(PokerAction action) {
        currentRound.act(action);
        checkFinishedRound();
    }

    private void checkFinishedRound() {
        if (currentRound.isFinished()) {
            handleFinishedRound();
        }
    }

    private void dealPocketCards(PokerPlayer p, int n) {
        for (int i = 0; i < n; i++) {
            p.addPocketCard(deck.deal(), false);
        }
        getServerAdapter().notifyPrivateCards(p.getId(), p.getPocketCards().getCards());
    }

    private void dealCommunityCards(int n) {
        List<Card> dealt = new LinkedList<Card>();
        for (int i = 0; i < n; i++) {
            dealt.add(deck.deal());
        }
        context.getCommunityCards().addAll(dealt);
        getServerAdapter().notifyCommunityCards(dealt);
    }

    public void handleFinishedRound() {
        currentRound.visit(this);
    }

    private void reportPotUpdate() {
        notifyPotAndRakeUpdates(Collections.<PotTransition>emptyList());
    }

    public int getCurrentRoundId() {
        return roundId;
    }

    private void startBettingRound() {
        log.trace("Starting new betting round. Round ID: " + (roundId + 1));
        currentRound = new BettingRound(context.getBlindsInfo().getDealerButtonSeatId(), context, serverAdapterHolder, new DefaultPlayerToActCalculator(),
                new ActionRequestFactory(new NoLimitBetStrategy()), new TexasHoldemFutureActionsCalculator());
        roundId++;
    }

    private boolean isHandFinished() {
        return (roundId >= 3 || context.countNonFoldedPlayers() <= 1);
    }

    public int countPlayersSittingIn() {
        int sittingIn = 0;
        for (PokerPlayer p : context.getCurrentHandSeatingMap().values()) {
            if (!p.isSittingOut()) {
                sittingIn++;
            }
        }

        return sittingIn;
    }

    public void dealCommunityCards() {
        if (roundId == 0) {
            dealCommunityCards(3);
        } else {
            dealCommunityCards(1);
        }
    }

    @Override
    public void dealExposedPocketCards() {
        dealCommunityCards();
    }

    @Override
    public void dealInitialPocketCards() {
        // Not used yet.
    }

    @Override
    public void exposeShowdownCards() {
        // Not valid / used yet.
    }

    private void handleFinishedHand(HandResult handResult) {
        log.debug("Hand over. Result: " + handResult.getPlayerHands());
        notifyHandFinished(handResult, HandEndStatus.NORMAL);
    }

    private void handleCanceledHand() {
        notifyHandFinished(new HandResult(), HandEndStatus.CANCELED_TOO_FEW_PLAYERS);
    }

    private void moveChipsToPot() {

        context.getPotHolder().moveChipsToPotAndTakeBackUncalledChips(context.getCurrentHandSeatingMap().values());

        for (PokerPlayer p : context.getCurrentHandSeatingMap().values()) {
            p.setHasActed(false);
            p.clearActionRequest();
        }
    }

    public void requestAction(ActionRequest r) {
        // OUCH! TODO!
        if (blindRequested(r) && context.isTournamentTable()) {
            getServerAdapter().scheduleTimeout(context.getTimingProfile().getTime(Periods.AUTO_POST_BLIND_DELAY));
        } else {
            roundHelper.requestAction(r);
        }
    }

    @Override
    public void requestMultipleActions(Collection<ActionRequest> requests) {
        throw new UnsupportedOperationException("sending multiple action requests not implemented");
    }

    @Override
    public void scheduleRoundTimeout() {
        log.debug("scheduleRoundTimeout in: " + context.getTimingProfile().getTime(Periods.RIVER));
        getServerAdapter().scheduleTimeout(context.getTimingProfile().getTime(Periods.RIVER));
    }

    private boolean blindRequested(ActionRequest r) {
        return r.isOptionEnabled(PokerActionType.SMALL_BLIND) || r.isOptionEnabled(PokerActionType.BIG_BLIND);
    }

    @Override
    public void prepareNewHand() {
        context.getCommunityCards().clear();
        for (PokerPlayer player : context.getCurrentHandPlayerMap().values()) {
            player.clearHand();
            player.setHasFolded(false);
        }
    }

    @Override
    public void timeout() {
        log.debug("Timeout");
        currentRound.timeout();
        checkFinishedRound();
    }

    @Override
    public String getStateDescription() {
        return currentRound == null ? "th-round=null" : currentRound.getClass() + "_" + currentRound.getStateDescription();
    }

    @Override
    public void visit(BettingRound bettingRound) {
        moveChipsToPot();
        reportPotUpdate();

        if (isHandFinished()) {
            exposeShowdownCards();
            PokerPlayer playerAtDealerButton = context.getPlayerInDealerSeat();
            List<Integer> playerRevealOrder = new RevealOrderCalculator().calculateRevealOrder(context.getCurrentHandSeatingMap(), context.getLastPlayerToBeCalled(), playerAtDealerButton);
            Set<PokerPlayer> muckingPlayers = new HashSet<PokerPlayer>();
            HandResult handResult = new HandResultCreator(new TexasHoldemHandCalculator()).createHandResult(context.getCommunityCards(), handResultCalculator, context.getPotHolder(), context.getCurrentHandPlayerMap(), playerRevealOrder, muckingPlayers);

            handleFinishedHand(handResult);
            context.getPotHolder().clearPots();
        } else {
            // Start deal community cards round
            currentRound = new DealCommunityCardsRound(this);
            // Schedule timeout for the community cards round
            scheduleRoundTimeout();
        }
    }

    @Override
    public void visit(ExposePrivateCardsRound exposePrivateCardsRound) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void visit(AnteRound anteRound) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void visit(BlindsRound blindsRound) {
        if (blindsRound.isCanceled()) {
            handleCanceledHand();
        } else {
            updateBlindsInfo(blindsRound);
            dealPocketCards();
            prepareBettingRound();
        }
    }

    @Override
    public void visit(DealCommunityCardsRound round) {
        startBettingRound();
    }

    @Override
    public void visit(DealExposedPocketCardsRound round) {
        throw new UnsupportedOperationException(round.getClass().getSimpleName() + " round not allowed in Texas Holdem");
    }

    @Override
    public void visit(DealInitialPocketCardsRound round) {
        throw new UnsupportedOperationException(round.getClass().getSimpleName() + " round not allowed in Texas Holdem");
    }

    private void prepareBettingRound() {
        currentRound = new BettingRound(context.getBlindsInfo().getBigBlindSeatId(), context, serverAdapterHolder, new DefaultPlayerToActCalculator(), new ActionRequestFactory(new NoLimitBetStrategy()), new TexasHoldemFutureActionsCalculator());
    }

    private void updateBlindsInfo(BlindsRound blindsRound) {
        context.setBlindsInfo(blindsRound.getBlindsInfo());
    }

    private void dealPocketCards() {
        for (PokerPlayer p : context.getCurrentHandSeatingMap().values()) {
            if (!p.isSittingOut()) {
                dealPocketCards(p, 2);
            }
        }
    }


    @Override
    // FIXME: Implement for Texas Hold'em.
    public void sendAllNonFoldedPlayersBestHand() {
        log.warn("Implement sendAllNonFoldedPlayersBestHand for Texas Hold'em.");
    }

    @Override
    public boolean canPlayerAffordEntryBet(PokerPlayer player, PokerSettings settings, boolean includePending) {
        return player.getBalance() + (includePending ? player.getPendingBalanceSum() : 0) >= settings.getAnteLevel();
    }

    @Override
    public boolean isCurrentlyWaitingForPlayer(int playerId) {
        return currentRound.isWaitingForPlayer(playerId);
    }
}
