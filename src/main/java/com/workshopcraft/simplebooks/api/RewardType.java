package com.workshopcraft.simplebooks.api;

import com.google.gson.JsonObject;

/** A reward type added by another mod. Register it on both sides so the book can show it. */
public interface RewardType {
    /** Read and check the reward's fields. Report problems and return null to skip the reward. */
    RewardSpec parse(JsonObject json, RewardProblems problems);

    default Display display(RewardSpec spec) {
        return new Display(spec.string("icon", ""), spec.string("label", spec.type()));
    }

    /** Give the reward to the claiming player. Server thread only. */
    void grant(RewardContext ctx, RewardSpec spec);
}
