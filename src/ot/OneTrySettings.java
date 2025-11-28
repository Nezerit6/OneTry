package ot;

import arc.Core;
import arc.scene.ui.*;
import arc.scene.ui.layout.Table;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;

import static arc.Core.bundle;
import static mindustry.Vars.*;

public class OneTrySettings {
    public static final String KEY_DIFFICULTY_MODE = "ot-difficulty-mode";
    public static final String KEY_DELETE_SAVES = "ot-delete-saves";
    public static final String KEY_WARNING_SHOWN = "ot-warning-shown";
    public static final String KEY_DEATH_UNLOCKED = "ot-death-unlocked";

    public static final String MODE_NORMAL = "normal";
    public static final String MODE_HARDCORE = "hardcore";
    public static final String MODE_ULTRA = "ultra";
    public static final String MODE_DEATH = "death";

    private static int ultraTapCounter = 0;
    private static boolean settingsInitialized = false;

    public static void init() {
        initializeDefaults();
        ui.settings.addCategory(bundle.get("onetry.settings.title"), "onetry-icon",
                t -> buildSettingsUIForMenu(t));
    }

    public static void initializeDefaults() {
        if (!settingsInitialized) {
            if (!Core.settings.has(KEY_DIFFICULTY_MODE))
                Core.settings.put(KEY_DIFFICULTY_MODE, MODE_NORMAL);
            if (!Core.settings.has(KEY_DELETE_SAVES))
                Core.settings.put(KEY_DELETE_SAVES, false);
            if (!Core.settings.has(KEY_WARNING_SHOWN))
                Core.settings.put(KEY_WARNING_SHOWN, false);
            if (!Core.settings.has(KEY_DEATH_UNLOCKED))
                Core.settings.put(KEY_DEATH_UNLOCKED, false);
            Core.settings.forceSave();
            settingsInitialized = true;
        }
    }

    public static String getDifficultyMode() {
        return Core.settings.getString(KEY_DIFFICULTY_MODE, MODE_NORMAL);
    }

    public static boolean isNormalMode() { return getDifficultyMode().equals(MODE_NORMAL); }
    public static boolean isHardcoreMode() { return getDifficultyMode().equals(MODE_HARDCORE); }
    public static boolean isUltraMode() { return getDifficultyMode().equals(MODE_ULTRA); }
    public static boolean isDeathMode() { return getDifficultyMode().equals(MODE_DEATH); }
    public static boolean isDeathUnlocked() { return Core.settings.getBool(KEY_DEATH_UNLOCKED, false); }
    public static boolean hasShownWarning() { return Core.settings.getBool(KEY_WARNING_SHOWN, false); }

    public static void unlockDeathMode() {
        Core.settings.put(KEY_DEATH_UNLOCKED, true);
        Core.settings.forceSave();
    }

    public static void markWarningShown() {
        Core.settings.put(KEY_WARNING_SHOWN, true);
        Core.settings.forceSave();
    }

    public static void showDifficultyDialog() {
        Core.app.post(() -> {
            BaseDialog dialog = new BaseDialog(bundle.get("onetry.settings.title"));
            dialog.setFillParent(false);
            dialog.cont.table(t -> buildSettingsUI(t)).width(600f).pad(10f).row();
            dialog.addCloseButton();
            dialog.show();
        });
    }

    private static void buildSettingsUIForMenu(SettingsTable table) {
        buildDifficultyUI(table, true);
    }

    private static void buildSettingsUI(Table content) {
        buildDifficultyUI(content, false);
    }

    private static void buildDifficultyUI(Table table, boolean isMenu) {
        if (isMenu) table.clear();
        else table.clear();

        String currentMode = getDifficultyMode();
        boolean deathUnlocked = isDeathUnlocked();
        final boolean[] isUpdating = {false};

        table.add("[accent]" + bundle.get("onetry.settings.diff.title"))
                .left().padTop(3f).padBottom(isMenu ? 0f : 10f).row();

        ButtonGroup<CheckBox> group = new ButtonGroup<>();
        group.setMinCheckCount(1);

        addModeCheckbox(table, group, MODE_NORMAL, currentMode, isUpdating, isMenu, false);
        addModeCheckbox(table, group, MODE_HARDCORE, currentMode, isUpdating, isMenu, false);

        CheckBox ultraBox = addModeCheckbox(table, group, MODE_ULTRA, currentMode, isUpdating, isMenu, true);
        ultraBox.clicked(() -> {
            if (isUpdating[0] || deathUnlocked) return;
            ultraTapCounter++;
            if (ultraTapCounter >= 10) {
                ultraTapCounter = 0;
                isUpdating[0] = true;
                unlockDeathMode();
                setMode(MODE_DEATH, true);
                ui.showInfoFade("[scarlet]" + bundle.get("onetry.settings.mode.death.unlocked", "DEATH MODE UNLOCKED!"));
                buildDifficultyUI(table, isMenu);
                isUpdating[0] = false;
            }
        });

        if (deathUnlocked) {
            addModeCheckbox(table, group, MODE_DEATH, currentMode, isUpdating, isMenu, true);
        }

        if (isMenu) {
            table.image(Styles.black6).height(4f).growX().padTop(8f).padBottom(8f).row();
        } else {
            table.add().height(16f).row();
        }

        CheckBox deleteSaves = new CheckBox(bundle.get("onetry.settings.delete.saves"));
        deleteSaves.setChecked(Core.settings.getBool(KEY_DELETE_SAVES, false));
        deleteSaves.setDisabled(isUltraMode() || isDeathMode());
        table.add(deleteSaves).left().padTop(isMenu ? 3f : 4f).row();

        deleteSaves.changed(() -> {
            if (!deleteSaves.isDisabled() && !isUpdating[0]) {
                Core.settings.put(KEY_DELETE_SAVES, deleteSaves.isChecked());
                Core.settings.forceSave();
            }
        });
    }

    private static CheckBox addModeCheckbox(Table table, ButtonGroup<CheckBox> group,
                                            String mode, String currentMode, boolean[] isUpdating, boolean isMenu, boolean forceDelete) {
        CheckBox box = new CheckBox(bundle.get("onetry.settings.mode." + mode));
        box.setChecked(currentMode.equals(mode));
        box.addListener(new Tooltip(t -> t.background(Styles.black6).margin(4f)
                .add(bundle.get("onetry.settings.mode." + mode + ".tip")).width(300f).wrap()));
        group.add(box);
        table.add(box).left().padTop(isMenu ? 3f : 4f).padBottom(isMenu ? 0f : 4f).row();

        box.changed(() -> {
            if (isUpdating[0] || !box.isChecked()) return;
            isUpdating[0] = true;
            ultraTapCounter = 0;
            setMode(mode, forceDelete);
            buildDifficultyUI(table, isMenu);
            isUpdating[0] = false;
        });

        return box;
    }

    private static void setMode(String mode, boolean forceDelete) {
        Core.settings.put(KEY_DIFFICULTY_MODE, mode);
        if (forceDelete || mode.equals(MODE_ULTRA) || mode.equals(MODE_DEATH)) {
            Core.settings.put(KEY_DELETE_SAVES, true);
        } else if (mode.equals(MODE_NORMAL)) {
            Core.settings.put(KEY_DELETE_SAVES, false);
        }
        Core.settings.forceSave();
    }
}