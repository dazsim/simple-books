package com.workshopcraft.simplebooks.quest;

import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.api.SimpleBooksPlugin;

/** Runs plugins handed over by the loader (Fabric entrypoint or IMC), and the dev sample types. */
public final class QuestPlugins {
    private QuestPlugins() {}

    public static void run(Iterable<?> plugins) {
        for (Object o : plugins) {
            if (o instanceof String name) {
                try {
                    o = Class.forName(name).getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    SimpleBooks.LOG.error("Could not load Simple Books plugin {}: {}", name, e.toString());
                    continue;
                }
            }
            if (!(o instanceof SimpleBooksPlugin p)) {
                SimpleBooks.LOG.warn("Ignoring Simple Books plugin {}, it is not a SimpleBooksPlugin", o);
                continue;
            }
            try {
                p.register();
            } catch (Exception e) {
                SimpleBooks.LOG.error("Simple Books plugin {} failed: {}", p.getClass().getName(), e.toString());
            }
        }
        if (SimpleBooks.dev) {
            new DevHeightTask().register();
            new DevTestReward().register();
        }
    }
}
