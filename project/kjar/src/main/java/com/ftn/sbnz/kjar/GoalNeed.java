package com.ftn.sbnz.kjar;

public class GoalNeed {

    private String type;
    private String reason;

    public GoalNeed() {
    }

    public GoalNeed(String type, String reason) {
        this.type = type;
        this.reason = reason;
    }

    public String getType() { return type; }
    public String getReason() { return reason; }
    public void setType(String type) { this.type = type; }
    public void setReason(String reason) { this.reason = reason; }
}
