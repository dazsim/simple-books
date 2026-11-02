package com.workshopcraft.simplebooks.quest;

import com.workshopcraft.simplebooks.SimpleBooks;

import java.util.ArrayList;
import java.util.List;

/** Validator output, kept per book so /sbquests validate can show it again. */
public final class Problems {
    public enum Level { ERROR, WARN, INFO }

    public record Problem(Level level, String book, String file, String ptr, String msg) {
        @Override
        public String toString() {
            return file + (ptr.isEmpty() ? "" : " " + ptr) + ": " + msg;
        }
    }

    private final List<Problem> list = new ArrayList<>();
    private final boolean log;

    public Problems(boolean log) {
        this.log = log;
    }

    public boolean logging() {
        return log;
    }

    public void add(Level level, String book, String file, String ptr, String msg) {
        Problem p = new Problem(level, book, file, ptr, msg);
        list.add(p);
        if (!log) return;
        switch (level) {
            case ERROR -> SimpleBooks.LOG.error("Quest {}", p);
            case WARN -> SimpleBooks.LOG.warn("Quest {}", p);
            case INFO -> SimpleBooks.LOG.info("Quest {}", p);
        }
    }

    public void addAll(Problems other) {
        list.addAll(other.list);
    }

    public List<Problem> all() {
        return list;
    }

    public long count(Level level, String book) {
        return list.stream().filter(p -> p.level == level && (book == null || p.book.equals(book))).count();
    }
}
