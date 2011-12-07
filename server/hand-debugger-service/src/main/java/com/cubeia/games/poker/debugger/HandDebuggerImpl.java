package com.cubeia.games.poker.debugger;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.cubeia.firebase.api.action.GameAction;
import com.cubeia.firebase.api.action.TableChatAction;
import com.cubeia.firebase.api.action.service.ServiceAction;
import com.cubeia.firebase.api.service.ServiceRouter;
import com.cubeia.games.poker.debugger.cache.TableEventCache;
import com.cubeia.games.poker.debugger.cache.TablePlayerInfoCache;
import com.cubeia.games.poker.debugger.guice.GuiceConfig;
import com.cubeia.games.poker.debugger.server.WebServer;
import com.cubeia.games.poker.services.HandDebuggerContract;
import com.google.inject.Inject;

public class HandDebuggerImpl implements HandDebuggerContract {
	
	@Inject GuiceConfig guice;
	
	@Inject WebServer server;
	
    @Inject TableEventCache tableEvenCache;

    @Inject TablePlayerInfoCache playerInfoCache;

	private ServiceRouter router;
	
	public void start() {
		server.start();
	}

	@Override
	public void addPublicAction(int tableId, GameAction action) {
		tableEvenCache.addPublicAction(tableId, action);
	}

	@Override
	public void addPrivateAction(int tableId, int playerId, GameAction action) {
		tableEvenCache.addPrivateAction(tableId, playerId, action);
	}
	
	@Override
	public void updatePlayerInfo(int tableId, int playerId, String name, boolean isSittingIn, long balance, long betstack) {
	    playerInfoCache.updatePlayerInfo(tableId, playerId, name, isSittingIn, balance, betstack);
	}

	@Override
	public void clearTable(int tableId) {
		tableEvenCache.clearTable(tableId);
	}

	@Override
	public void sendHttpLink(int tableId, int playerId) {
		if (router != null) {
			String myAddress = getLocalIP();
			String url = "http://"+myAddress+":9091/table.html?tableid="+tableId;
			String message = "Hand debugger is available at "+url;
			TableChatAction chat = new TableChatAction(playerId, tableId, message);
	        router.dispatchToPlayer(playerId, chat);
		}
	}

	private String getLocalIP() {
		String address = null;
		try {
			InetAddress ownIp = InetAddress.getLocalHost();
			address = ownIp.getHostAddress();
		} catch (UnknownHostException e) {}
		return address == null ? "localhost" : address;
	}

	@Override
	public void setRouter(ServiceRouter router) {
		this.router = router;
	}

	@Override
	public void onAction(ServiceAction e) {}
}
