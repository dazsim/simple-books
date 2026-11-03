package com.workshopcraft.simplebooks.api;

/** Where a task type reports bad fields. field is the JSON key, for example "machine". */
public interface TaskProblems {
    void error(String field, String message);

    void warn(String field, String message);
}
