package ot;

import arc.Core;
import arc.Events;
import arc.scene.Element;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.GameStats;
import mindustry.game.Saves.SaveSlot;
import mindustry.io.JsonIO;
import mindustry.io.SaveMeta;
import mindustry.type.Planet;
import mindustry.type.Sector;
import mindustry.ui.dialogs.GameOverDialog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class OneTryGameOver {
    private static Method rebuild;

    public static void init() {
        Events.on(EventType.ClientLoadEvent.class, e -> Vars.ui.restart.shown(OneTryGameOver::patch));
    }

    private static void patch() {
        if (!Vars.state.isCampaign() || Vars.net.client() || !OneTrySettings.isRunLost()) return;

        Sector sector = Vars.state.getSector();
        SaveSlot current = Vars.control.saves.getCurrent();
        String sectorTitle = sector == null ? null : Core.bundle.format("sector.lost", sector.name());
        String sectorTime = current == null ? null : "[accent]" + current.getPlayTime();

        GameStats live = Vars.state.stats;
        GameStats total = new GameStats();
        long time = Vars.control.saves.getTotalPlaytime();
        int captured = 0;
        add(total, live);

        for (Planet planet : Vars.content.planets()) {
            for (Sector other : planet.sectors) {
                if (other.info.wasCaptured || other.isCaptured()) captured++;
                if (other == sector || other.save == null || other.save.meta == null) continue;
                SaveMeta meta = other.save.meta;
                time += meta.timePlayed;
                String json = meta.tags == null ? null : meta.tags.get("stats");
                if (json == null) continue;
                try {
                    add(total, JsonIO.read(GameStats.class, json));
                } catch (Throwable ignored) {
                }
            }
        }

        try {
            if (rebuild == null) {
                rebuild = GameOverDialog.class.getDeclaredMethod("rebuild");
                rebuild.setAccessible(true);
            }
            Vars.state.stats = total;
            rebuild.invoke(Vars.ui.restart);
        } catch (Exception e) {
            Log.err("[OneTry] could not show campaign stats", e);
        } finally {
            Vars.state.stats = live;
        }

        addSectors(captured);

        Vars.ui.restart.buttons.clear();
        Vars.ui.restart.buttons.button("@menu", Vars.ui.restart::hide).size(170f, 60f);

        String title = Core.bundle.get("onetry.gameover.title");
        String playtime = "[accent]" + Strings.formatMillis(time);
        Vars.ui.restart.forEach(element -> {
            if (!(element instanceof Label label)) return;
            String text = label.getText().toString();
            if (text.equals(sectorTitle)) label.setText(title);
            else if (text.equals(sectorTime)) label.setText(playtime);
        });
    }

    private static void addSectors(int captured) {
        Element[] sample = {null};
        Vars.ui.restart.forEach(element -> {
            if (sample[0] == null && element.getClass().getSimpleName().equals("StatLabel")) sample[0] = element;
        });
        if (sample[0] == null || !(sample[0].parent instanceof Table stats)) return;

        try {
            Constructor<?> make = sample[0].getClass().getDeclaredConstructor(String.class, int.class, float.class);
            make.setAccessible(true);
            Element row = (Element) make.newInstance(Core.bundle.get("onetry.stat.sectors"), captured, 0.3f);
            stats.add(row).top().pad(5).growX().height(50).row();
        } catch (Exception e) {
            Log.err("[OneTry] could not add sector stat", e);
        }
    }

    private static void add(GameStats to, GameStats from) {
        if (from == null) return;
        to.enemyUnitsDestroyed += from.enemyUnitsDestroyed;
        to.wavesLasted += from.wavesLasted;
        to.buildingsBuilt += from.buildingsBuilt;
        to.buildingsDeconstructed += from.buildingsDeconstructed;
        to.buildingsDestroyed += from.buildingsDestroyed;
        to.unitsCreated += from.unitsCreated;
    }
}