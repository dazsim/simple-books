package com.workshopcraft.simplebooks.quest;

import com.google.gson.JsonObject;
import com.workshopcraft.simplebooks.api.SimpleBooksApi;
import com.workshopcraft.simplebooks.api.SimpleBooksPlugin;
import com.workshopcraft.simplebooks.api.TaskProblems;
import com.workshopcraft.simplebooks.api.TaskSpec;
import com.workshopcraft.simplebooks.api.TaskType;
import net.minecraft.server.level.ServerPlayer;

/** Dev runs only: "simplebooks:dev_height" is done once a member stands at or above y = count. */
public final class DevHeightTask implements TaskType, SimpleBooksPlugin {
    public static final String ID = "simplebooks:dev_height";

    @Override
    public void register() {
        if (SimpleBooksApi.taskType(ID) == null) SimpleBooksApi.registerTaskType(ID, this);
    }

    @Override
    public TaskSpec parse(JsonObject json, TaskProblems problems) {
        TaskSpec s = TaskSpec.of(json);
        if (s.count() < 1) problems.error("count", "must be at least 1");
        return s;
    }

    @Override
    public com.workshopcraft.simplebooks.api.Display display(TaskSpec spec) {
        return new com.workshopcraft.simplebooks.api.Display("minecraft:feather", "Climb to y = " + spec.count());
    }

    @Override
    public long poll(ServerPlayer player, TaskSpec spec) {
        return player.getBlockY() >= spec.count() ? spec.count() : 0;
    }
}
