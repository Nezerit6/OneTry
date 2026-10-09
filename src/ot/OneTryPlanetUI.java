package ot;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Button;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;

import static arc.Core.bundle;

public class OneTryPlanetUI {
    private static final String name = "onetry-difficulty";

    public static void init() {
        Events.on(EventType.ClientLoadEvent.class, e -> Vars.ui.planet.shown(OneTryPlanetUI::attach));
    }

    private static void attach() {
        if (Vars.ui.planet.find(name) != null) return;

        Button vanilla = findButton(Vars.ui.planet, Core.bundle.get("campaign.difficulty"));
        if (vanilla == null || !(vanilla.parent instanceof Table parent)) return;

        Cell<?> cell = parent.getCell(vanilla);
        if (cell == null) return;

        Table group = new Table();
        group.visibility = vanilla.visibility;
        cell.setElement(group).size(208f, 88f);
        group.add(vanilla).size(208f, 40f).row();

        Table holder = new Table() {
            @Override
            public void draw() {
                super.draw();
                if (OneTrySettings.hasShownIntro()) return;

                float pulse = Mathf.absin(Time.globalTime, 9f, 1f);
                float stroke = Scl.scl(2f);
                Draw.color(Pal.accent, 0.35f + 0.65f * pulse);
                Lines.stroke(stroke);
                Lines.rect(x - stroke, y - stroke, width + stroke * 2f, height + stroke * 2f);

                float size = Scl.scl(24f);
                float slide = Scl.scl(6f) * Mathf.absin(Time.globalTime, 6f, 1f);
                Draw.rect(Icon.left.getRegion(), x + width + Scl.scl(10f) + size / 2f + slide,
                        y + height / 2f, size, size);
                Draw.reset();
            }
        };
        holder.name = name;
        holder.button(bundle.get("onetry.title"), Icon.warningSmall, () -> {
            if (OneTrySettings.hasShownIntro()) {
                OneTrySettings.showDifficultyDialog();
            } else {
                OneTrySettings.markIntroShown();
                OneTryIntro.show();
            }
        }).margin(12f).size(208f, 40f);

        group.add(holder).size(208f, 40f).padTop(8f);
    }

    private static Button findButton(Group group, String label) {
        for (Element child : group.getChildren()) {
            if (child instanceof Button button && hasLabel(button, label)) return button;
            if (child instanceof Group inner) {
                Button found = findButton(inner, label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean hasLabel(Group group, String label) {
        for (Element child : group.getChildren()) {
            if (child instanceof Label text && text.getText() != null
                    && text.getText().toString().equals(label)) return true;
            if (child instanceof Group inner && hasLabel(inner, label)) return true;
        }
        return false;
    }
}