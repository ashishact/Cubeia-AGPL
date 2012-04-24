package com.cubeia.games.poker.debugger;

import com.cubeia.firebase.api.action.GameAction;
import com.cubeia.firebase.api.service.Contract;
import com.cubeia.firebase.api.service.RoutableService;

public interface HandDebuggerContract extends Contract, RoutableService {

    void start();

    void addPublicAction(int tableId, GameAction action);

    void addPrivateAction(int tableId, int playerId, GameAction action);

    void clearTable(int tableId);

    void sendHttpLink(int tableId, int playerId);

    void updatePlayerInfo(int tableId, int playerId, String name, boolean isSittingIn, long tableBalance, long betStack);

}
