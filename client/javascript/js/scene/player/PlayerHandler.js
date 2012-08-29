PlayerHandler = function (pid) {
    this.myPlayerPid = pid;
};

PlayerHandler.prototype.getPlayerEntityIdByPid = function (pid) {
    var playerEntityId = "playerId_" + pid;
    return playerEntityId;
};


PlayerHandler.prototype.addWatchingPlayer = function (pid, nick) {
    var playerEntity = entityHandler.addEntity(this.getPlayerEntityIdByPid(pid));

    playerEntity.name = nick;
    playerEntity.pid = pid;

    if (pid == this.myPlayerPid)
    {
        view.table.addSelf(nick);
    }
    else
    {
        entityHandler.addSpatial("body", playerEntity, 0, 0);
        entityHandler.setEntityToWatchingState(playerEntity);
    }
};

PlayerHandler.prototype.updateSeatIdWithPlayerEntity = function (seatId, playerEntity) {
    var seat = view.table.getSeatBySeatNumber(seatId);
    entityHandler.setEntityToSeatedAtSeatIdState(playerEntity, seatId);
    seat.occupant = playerEntity;
    view.spatialManager.setVisualEntityTransform(playerEntity, seat.spatial.attachmentPoints.player.transform.posX, seat.spatial.attachmentPoints.player.transform.posY);
    pokerDealer.addPlayerCardsComponent(playerEntity);
};

PlayerHandler.prototype.getPlayerEntityActionTimePercentRemaining = function (playerEntity, currentTime) {
    var startTime = playerEntity.state.actionStartTime;
    var timeToAct = playerEntity.state.timeToAct;
    var percentDone = uiUtils.getPercentDoneForMinMaxCurrent(0, timeToAct, currentTime - startTime);
    return 100 - percentDone;
}


PlayerHandler.prototype.seatPlayerIdAtTable = function (pid, seatId) {
    if (pid == this.myPlayerPid)
    {
        // TODO: HUD.
    }
    else
    {
        var playerEntity = entityHandler.getEntityById(this.getPlayerEntityIdByPid(pid));
        this.updateSeatIdWithPlayerEntity(seatId, playerEntity);
    }
};

PlayerHandler.prototype.updateSeatBalance = function (pid, balance) {
    if (pid == this.myPlayerPid) {
        view.table.updateOwnBalance(balance);
    }
    else
    {
        var playerEntity = entityHandler.getEntityById(this.getPlayerEntityIdByPid(pid));
        var seatEntityId = view.seatHandler.getSeatEntityIdBySeatNumber(playerEntity.state.seatId);
        var seatEntity = entityHandler.getEntityById(seatEntityId);
        if (!seatEntity) {
            console.log("No seat entity for pid " + pid);
        } else {
            var balanceDivId = seatEntity.ui.balanceDivId;
            var div = document.getElementById(balanceDivId);
            div.innerHTML = balance;
        }
    }
};

PlayerHandler.prototype.unseatPlayer = function (pid) {
    var playerEntity = entityHandler.getEntityById(this.getPlayerEntityIdByPid(pid));
    this.updateSeatBalance(pid, "");


//    var seatEntity = entityHandler.getEntityById(view.seatHandler.getSeatEntityIdBySeatNumber(playerEntity.state.seatId));
//	seatHandler.removePlayerFromSeat(seatEntity)

    var table = entityHandler.getEntityById(view.table.entityId);

    for (index in table.seats) {
        if (table.seats[index].occupant == null) {
            var balanceDivId = table.seats[index].ui.balanceDivId;
            document.getElementById(balanceDivId).innerHTML = "";
            document.getElementById(table.seats[index].ui.betFieldDivId).innerHTML = "";
            seatHandler.removePlayerFromSeat(table.seats[index]);
        }
    }

    view.watchingPlayers.setEntityToWatching(playerEntity);

};

PlayerHandler.prototype.handlePlayerStatus = function (pid, status) {
    var playerEntity = entityHandler.getEntityById(this.getPlayerEntityIdByPid(pid));
    var seatEntity = entityHandler.getEntityById(view.seatHandler.getSeatEntityIdBySeatNumber(playerEntity.state.seatId));
    if (!seatEntity) return;

    switch (status) {
        case POKER_PROTOCOL.PlayerTableStatusEnum.SITIN :
            playerActions.handlePlayerActionFeedback(pid, "Sit In")
            document.getElementById(seatEntity.spatial.transform.anchorId).style.opacity = 1;
            break;
        case POKER_PROTOCOL.PlayerTableStatusEnum.SITOUT :
            playerActions.handlePlayerActionFeedback(pid, "Sit Out")
            document.getElementById(seatEntity.spatial.transform.anchorId).style.opacity = 0.6;
            break;
    }
}
