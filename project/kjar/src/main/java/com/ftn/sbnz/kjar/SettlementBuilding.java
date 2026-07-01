package com.ftn.sbnz.kjar;

import java.util.Set;

import org.kie.api.definition.type.Role;

/** Event prepared by the service when a player builds a settlement. */
@Role(Role.Type.EVENT)
public class SettlementBuilding {

    private final int playerId;
    private final int mePlayerId;
    private final int locationNodeId;
    private final Set<Integer> blockedNodeIds;

    public SettlementBuilding(int playerId, int mePlayerId, int locationNodeId,
                              Set<Integer> blockedNodeIds) {
        this.playerId = playerId;
        this.mePlayerId = mePlayerId;
        this.locationNodeId = locationNodeId;
        this.blockedNodeIds = blockedNodeIds;
    }

    public int getPlayerId() { return playerId; }
    public int getMePlayerId() { return mePlayerId; }
    public int getLocationNodeId() { return locationNodeId; }
    public Set<Integer> getBlockedNodeIds() { return blockedNodeIds; }
}
