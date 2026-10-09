package ot;

import arc.Core;
import arc.func.Cons;
import arc.func.Func;
import arc.graphics.Color;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Scl;
import arc.struct.Seq;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import static arc.Core.bundle;
import static mindustry.Vars.*;

public class OneTrySettings {
    public static final String KEY_STAKE = "ot-stake";
    public static final String KEY_WIPE_PROGRESS = "ot-wipe-progress";
    public static final String KEY_INTRO_SHOWN = "ot-intro-shown";
    public static final String KEY_RUN_LOCKED = "ot-run-locked";
    public static final String KEY_RUN_LOST = "ot-run-lost";

    public static final String SAFE = "safe";
    public static final String MORTAL = "mortal";
    public static final String BRITTLE = "brittle";
    public static final String TERMINAL = "terminal";

    public static final String[] stakes = {SAFE, MORTAL, BRITTLE};

    public static final String TAG_STAKE = "onetry-stake";
    public static final String TAG_WIPE = "onetry-wipe";
    public static final String TAG_INDUSTRY = "onetry-industry";

    private static boolean settingsInitialized;

    public static void initializeDefaults() {
        if (!settingsInitialized) {
            if (!Core.settings.has(KEY_STAKE)) Core.settings.put(KEY_STAKE, SAFE);
            if (!Core.settings.has(KEY_WIPE_PROGRESS)) Core.settings.put(KEY_WIPE_PROGRESS, false);
            if (!Core.settings.has(KEY_INTRO_SHOWN)) Core.settings.put(KEY_INTRO_SHOWN, false);
            if (!Core.settings.has(KEY_RUN_LOCKED)) Core.settings.put(KEY_RUN_LOCKED, false);
            if (!Core.settings.has(KEY_RUN_LOST)) Core.settings.put(KEY_RUN_LOST, false);
            OneTryIndustry.initializeDefaults();
            Core.settings.forceSave();
            settingsInitialized = true;
        }
    }

    public static String getStake() {
        String stake = guest() ? state.rules.tags.get(TAG_STAKE, SAFE) : Core.settings.getString(KEY_STAKE, SAFE);
        if (stake.equals(MORTAL) || stake.equals(BRITTLE)) return stake;
        if (stake.equals(TERMINAL)) return BRITTLE;
        return SAFE;
    }

    public static boolean isSafe() { return getStake().equals(SAFE); }
    public static boolean isBrittle() { return getStake().equals(BRITTLE); }
    public static boolean isTerminal() { return getStake().equals(TERMINAL); }
    public static boolean hasShownIntro() { return Core.settings.getBool(KEY_INTRO_SHOWN, false); }

    public static void markIntroShown() {
        Core.settings.put(KEY_INTRO_SHOWN, true);
        Core.settings.forceSave();
    }

    public static boolean isWipeForced() { return isBrittle() || isTerminal(); }

    public static boolean wipeOnDeath() {
        if (guest()) return isWipeForced() || state.rules.tags.getBool(TAG_WIPE);
        return isWipeForced() || Core.settings.getBool(KEY_WIPE_PROGRESS, false);
    }

    public static boolean guest() {
        return net.client();
    }

    public static void shareRules() {
        if (guest() || state.isMenu()) return;
        state.rules.tags.put(TAG_STAKE, getStake());
        state.rules.tags.put(TAG_WIPE, String.valueOf(wipeOnDeath()));
        state.rules.tags.put(TAG_INDUSTRY, OneTryIndustry.get());
        if (net.server()) Call.setRules(state.rules);
    }

    public static float oreRichness() {
        return oreRichnessOf(getStake());
    }

    public static float oreRichnessOf(String stake) {
        switch (stake) {
            case TERMINAL: return 0.35f;
            case BRITTLE: return 0.6f;
            case MORTAL: return 1f;
            default: return 0f;
        }
    }

    public static float incomingDamageMultiplier() {
        return isBrittle() ? 10f : 1f;
    }

    public static String statLine(String nameKey, String value) {
        return "[lightgray]" + bundle.get(nameKey) + ":[] [negstat]" + value + "[]";
    }

    public static String stakeStats(String stake) {
        if (stake.equals(SAFE)) return bundle.get("onetry.stat.nomodifiers");

        StringBuilder text = new StringBuilder();
        float richness = oreRichnessOf(stake);
        String ore = bundle.get("onetry.stat.ore.finite");
        if (richness < 1f) ore += ", -" + Math.round((1f - richness) * 100f) + "%";
        text.append(statLine("onetry.stat.ore", ore));

        if (stake.equals(BRITTLE)) {
            text.append("\n").append(statLine("onetry.stat.damage.you", "x10"));
        } else if (stake.equals(TERMINAL)) {
            text.append("\n").append(statLine("onetry.stat.damage.you", "x\u221E"));
            text.append("\n").append(statLine("onetry.stat.damage.cores", "x\u221E"));
        }

        text.append("\n").append(statLine("onetry.stat.respawn", bundle.get("onetry.stat.no")));

        boolean forced = stake.equals(BRITTLE) || stake.equals(TERMINAL);
        text.append("\n").append(statLine("onetry.stat.wipe",
                bundle.get(forced ? "onetry.stat.wipe.always" : "onetry.stat.wipe.optional")));

        return text.toString();
    }

    public static boolean isRunLocked() {
        return isTerminal() && Core.settings.getBool(KEY_RUN_LOCKED, false);
    }

    public static void lockRun() {
        if (!isTerminal() || isRunLocked()) return;
        Core.settings.put(KEY_RUN_LOCKED, true);
        Core.settings.forceSave();
    }

    public static void unlockRun() {
        Core.settings.put(KEY_RUN_LOCKED, false);
        Core.settings.forceSave();
    }

    public static boolean isRunLost() { return Core.settings.getBool(KEY_RUN_LOST, false); }

    public static void markRunLost() {
        Core.settings.put(KEY_RUN_LOST, true);
        Core.settings.forceSave();
    }

    public static void clearRunLost() {
        Core.settings.put(KEY_RUN_LOST, false);
        Core.settings.forceSave();
    }

    public static Color stakeColor(String stake) {
        switch (stake) {
            case MORTAL: return Color.orange;
            case BRITTLE: return Pal.remove;
            case TERMINAL: return Color.scarlet;
            default: return Pal.heal;
        }
    }

    public static float cardWidth() {
        return Math.min(Core.graphics.getWidth() / Scl.scl(1.05f) - Scl.scl(48f), 520f);
    }

    public static void showDifficultyDialog() {
        Core.app.post(() -> {
            BaseDialog dialog = new BaseDialog(bundle.get("onetry.title"));
            dialog.setFillParent(false);
            dialog.cont.pane(t -> buildUI(t)).scrollX(false)
                    .maxHeight(Core.graphics.getHeight() / Scl.scl(1f) - 160f).pad(10f).row();
            dialog.addCloseButton();
            dialog.hidden(OneTrySettings::shareRules);
            dialog.show();
        });
    }

    private static void buildUI(Table table) {
        table.clear();
        table.top();

        boolean locked = isRunLocked();
        float width = cardWidth();

        if (locked) {
            table.table(Styles.black6, note -> {
                note.image(Icon.lock).size(26f).color(Pal.remove).padRight(10f);
                note.add(bundle.get("onetry.stake.locked")).color(Pal.remove)
                        .width(width - 80f).wrap().left();
            }).width(width).pad(6f).padBottom(6f).row();
        }

        axis(table, width, "onetry.stake", stakes, getStake(), locked,
                stake -> {
                    setStake(stake);
                    Core.app.post(() -> buildUI(table));
                },
                OneTrySettings::stakeColor);

        CheckBox wipe = new CheckBox(bundle.get("onetry.wipe"));
        wipe.setChecked(wipeOnDeath());
        wipe.setDisabled(() -> isSafe() || isWipeForced() || locked);
        wipe.changed(() -> {
            if (!wipe.isDisabled()) {
                Core.settings.put(KEY_WIPE_PROGRESS, wipe.isChecked());
                Core.settings.forceSave();
            }
        });
        table.add(wipe).left().padTop(10f).row();

        axis(table, width, "onetry.industry", OneTryIndustry.tiers, OneTryIndustry.get(), false,
                tier -> {
                    OneTryIndustry.set(tier);
                    Core.app.post(() -> buildUI(table));
                },
                OneTryIndustry::color);
    }

    private static void axis(Table table, float width, String prefix, String[] tiers, String current,
                             boolean locked, Cons<String> onSelect, Func<String, Color> colors) {
        table.add("[accent]" + bundle.get(prefix + ".title")).left().padTop(14f).padBottom(6f).row();

        Seq<String> visible = new Seq<>();
        visible.addAll(tiers);

        int perRow = width < 420f ? 2 : visible.size;
        float buttonWidth = (width - (perRow - 1) * 6f) / perRow;

        table.table(rows -> {
            for (int i = 0; i < visible.size; i++) {
                String tier = visible.get(i);
                boolean selected = current.equals(tier);

                TextButton button = new TextButton(bundle.get(prefix + "." + tier), Styles.flatTogglet);
                button.setChecked(selected);
                button.setDisabled(() -> locked);
                button.getLabel().setWrap(true);
                button.getLabel().setAlignment(Align.center);
                button.getLabelCell().grow().pad(4f);

                if (!locked) {
                    button.clicked(() -> {
                        if (!selected) onSelect.get(tier);
                    });
                }

                rows.add(button).size(buttonWidth, 56f).pad(3f);
                if (i % perRow == perRow - 1) rows.row();
            }
        }).width(width).row();

        float textWidth = width - 40f;
        float height = 0f;
        for (String tier : visible) height = Math.max(height, textHeight(details(prefix, tier), textWidth));

        final float panelHeight = height + 18f;
        table.table(Styles.black6, info -> {
            info.image(Tex.whiteui, colors.get(current)).growY().width(5f);
            info.add(details(prefix, current)).color(Color.lightGray)
                    .width(textWidth).wrap().labelAlign(Align.topLeft).growY().top().pad(8f).padLeft(10f);
        }).width(width).height(panelHeight).padTop(6f).row();
    }

    private static String details(String prefix, String tier) {
        String stats = prefix.equals("onetry.industry")
                ? OneTryIndustry.stats(tier) : stakeStats(tier);
        return bundle.get(prefix + "." + tier + ".tip") + "\n\n" + stats;
    }

    private static float textHeight(String text, float width) {
        Label label = new Label(text);
        label.setWrap(true);
        label.setWidth(width);
        label.layout();
        return label.getPrefHeight();
    }

    private static void setStake(String stake) {
        if (isRunLocked()) return;

        boolean wasForced = isWipeForced();
        Core.settings.put(KEY_STAKE, stake);
        if (isWipeForced()) {
            Core.settings.put(KEY_WIPE_PROGRESS, true);
        } else if (wasForced || stake.equals(SAFE)) {
            Core.settings.put(KEY_WIPE_PROGRESS, false);
        }
        Core.settings.forceSave();
    }
}