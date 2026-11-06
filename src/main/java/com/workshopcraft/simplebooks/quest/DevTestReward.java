package com.workshopcraft.simplebooks.quest;

import com.google.gson.JsonObject;
import com.workshopcraft.simplebooks.api.Display;
import com.workshopcraft.simplebooks.api.RewardContext;
import com.workshopcraft.simplebooks.api.RewardProblems;
import com.workshopcraft.simplebooks.api.RewardSpec;
import com.workshopcraft.simplebooks.api.RewardType;
import com.workshopcraft.simplebooks.api.SimpleBooksApi;
import com.workshopcraft.simplebooks.api.SimpleBooksPlugin;
import net.minecraft.network.chat.Component;

/** Dev runs only: "simplebooks:dev_toast" sends a chat line with the reward's text. */
public final class DevTestReward implements RewardType, SimpleBooksPlugin {
    public static final String ID = "simplebooks:dev_toast";

    @Override
    public void register() {
        if (SimpleBooksApi.rewardType(ID) == null) SimpleBooksApi.registerRewardType(ID, this);
    }

    @Override
    public RewardSpec parse(JsonObject json, RewardProblems problems) {
        RewardSpec s = RewardSpec.of(json);
        if (s.string("text", "").isBlank()) problems.error("text", "missing");
        return s;
    }

    @Override
    public Display display(RewardSpec spec) {
        return new Display(spec.string("icon", "minecraft:paper"), spec.string("label", spec.string("text", ID)));
    }

    @Override
    public void grant(RewardContext ctx, RewardSpec spec) {
        ctx.player().sendSystemMessage(Component.literal(spec.string("text", "Dev reward")));
    }
}
