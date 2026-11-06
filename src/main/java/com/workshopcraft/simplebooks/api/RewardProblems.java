package com.workshopcraft.simplebooks.api;

/** Where a reward type reports bad fields. field is the JSON key, for example "area". */
public interface RewardProblems {
    void error(String field, String message);

    void warn(String field, String message);
}
