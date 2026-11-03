package com.workshopcraft.simplebooks.api;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

/** A task type added by another mod. Register it on both sides so the book can show it. */
public interface TaskType {
    /** Read and check the task's fields. Report problems and return null to skip the quest. */
    TaskSpec parse(JsonObject json, TaskProblems problems);

    /** The count to reach. */
    default long target(TaskSpec spec) {
        return spec.count();
    }

    default Display display(TaskSpec spec) {
        return new Display(spec.string("icon", ""), spec.string("text", spec.type()));
    }

    /**
     * For state tasks: the player's current amount, called once a second while the task is open and added up
     * over online team members. Return -1 (the default) for event tasks that use SimpleBooksApi.progress.
     */
    default long poll(ServerPlayer player, TaskSpec spec) {
        return -1;
    }
}
