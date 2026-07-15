package com.ftn.sbnz.kjar;

/** Controls the two deterministic ranking steps in the rules agenda. */
public class RankingRequest {

    private int nextRank = 1;

    public int getNextRank() {
        return nextRank;
    }

    public void setNextRank(int nextRank) {
        this.nextRank = nextRank;
    }
}
