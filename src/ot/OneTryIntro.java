package ot;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import static arc.Core.bundle;

public class OneTryIntro {
    public static void show() {
        Core.app.post(() -> {
            BaseDialog dialog = new BaseDialog(bundle.get("onetry.title"));
            dialog.setFillParent(false);
            dialog.closeOnBack();

            float width = OneTrySettings.cardWidth();
            Table cont = dialog.cont;
            cont.margin(8f);

            cont.table(head -> {
                head.image(Icon.warning).size(40f).color(Pal.remove).padRight(12f);
                head.add(bundle.get("onetry.intro.tagline")).style(Styles.outlineLabel).color(Pal.remove)
                        .width(width - 70f).wrap().left();
            }).width(width).padBottom(10f).row();

            cont.add(bundle.get("onetry.intro.text")).width(width).wrap().labelAlign(Align.center)
                    .padBottom(10f).row();

            line(cont, "onetry.stake.title", "onetry.intro.stake", Color.orange, width);
            line(cont, "onetry.industry.title", "onetry.intro.industry", Pal.accent, width);

            dialog.buttons.defaults().size(220f, 58f).pad(4f);
            dialog.buttons.button(bundle.get("onetry.intro.later"), Icon.cancel, dialog::hide);
            if (Core.graphics.isPortrait()) dialog.buttons.row();
            dialog.buttons.button(bundle.get("onetry.intro.open"), Icon.settings, () -> {
                dialog.hide();
                OneTrySettings.showDifficultyDialog();
            });

            dialog.show();
        });
    }

    private static void line(Table table, String titleKey, String textKey, Color color, float width) {
        table.table(Styles.grayPanel, row -> {
            row.image(Tex.whiteui, color).growY().width(5f);
            row.table(info -> {
                info.left();
                info.add(bundle.get(titleKey)).color(color).left().row();
                info.add(bundle.get(textKey)).color(Color.lightGray).width(width - 60f).wrap().left().padTop(2f);
            }).grow().pad(8f).padLeft(10f);
        }).width(width).padTop(6f).row();
    }
}