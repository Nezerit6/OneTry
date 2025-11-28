package ot;

import arc.Core;
import arc.Events;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.ctype.UnlockableContent;
import mindustry.ui.dialogs.BaseDialog;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static arc.Core.bundle;

public class OneTryFunctionality {
    private final Map<String, Integer> deathCount = new ConcurrentHashMap<>();
    private final Set<String> respawnBlocked = ConcurrentHashMap.newKeySet();
    private final Map<String, Boolean> wasAlive = new ConcurrentHashMap<>();
    private final Map<Integer, Float> previousHealth = new ConcurrentHashMap<>();
    private Field deathTimerField;

    public OneTryFunctionality() {
        try {
            deathTimerField = Player.class.getDeclaredField("deathTimer");
            deathTimerField.setAccessible(true);
        } catch (Throwable t) {
            Log.warn("[OneTry] Can't access Player.deathTimer via reflection.");
        }
        setupListeners();
    }

    private void setupListeners() {
        Events.run(EventType.Trigger.update, () -> {
            if (OneTrySettings.isNormalMode()) return;

            Groups.player.each(p -> {
                String uuid = p.uuid();
                boolean alive = p.unit() != null && !p.dead();

                if (wasAlive.getOrDefault(uuid, false) && !alive && !respawnBlocked.contains(uuid)) {
                    triggerPermadeath(p);
                }

                if (respawnBlocked.contains(uuid)) {
                    setDeathTimer(p, 0f);
                    if (p.unit() != null) Core.app.post(p::clearUnit);
                }

                wasAlive.put(uuid, alive);
            });

            if (OneTrySettings.isDeathMode()) {
                Groups.unit.each(u -> {
                    if (u.isPlayer()) {
                        u.health = Math.min(u.health, 1f);
                        u.maxHealth = 1f;
                    }
                });

                Groups.build.each(b -> {
                    if (b.block != null && b.block.name.contains("core")) {
                        b.health = Math.min(b.health, 1f);
                        b.maxHealth = 1f;
                    }
                });
            }

            if (OneTrySettings.isUltraMode() || OneTrySettings.isDeathMode()) {
                Groups.player.each(p -> {
                    if (!respawnBlocked.contains(p.uuid()) && p.unit() != null) {
                        Unit u = p.unit();
                        Float prevHealth = previousHealth.get(u.id);

                        if (prevHealth != null && u.health < prevHealth) {
                            float damage = prevHealth - u.health;
                            u.health = prevHealth - (damage * 100f);
                            if (u.health <= 0) u.kill();
                        }

                        previousHealth.put(u.id, u.health);
                    } else if (p.unit() != null) {
                        previousHealth.remove(p.unit().id);
                    }
                });
            }
        });

        Events.on(EventType.UnitChangeEvent.class, e -> {
            if (OneTrySettings.isNormalMode() || e.player == null) return;

            if (respawnBlocked.contains(e.player.uuid()) && e.unit != null) {
                Core.app.post(() -> e.player.clearUnit());
                Call.infoMessage(e.player.con, "[scarlet]" + bundle.get("onetry.respawn.blocked"));
            }
        });

        Events.on(EventType.PlayerJoin.class, e -> {
            if (e.player != null) clearPlayerState(e.player.uuid(), e.player);
        });

        Events.on(EventType.PlayerLeave.class, e -> {
            if (e.player != null) {
                String uuid = e.player.uuid();
                wasAlive.remove(uuid);
                deathCount.remove(uuid);
                respawnBlocked.remove(uuid);
            }
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            Core.app.post(this::clearAllPlayerState);
            previousHealth.clear();
        });

        Events.on(EventType.StateChangeEvent.class, e -> {
            if (Vars.state.getState() == GameState.State.menu) {
                Core.app.post(this::clearAllPlayerState);
                previousHealth.clear();
            }
        });
    }

    private void triggerPermadeath(Player p) {
        int deaths = deathCount.getOrDefault(p.uuid(), 0) + 1;
        deathCount.put(p.uuid(), deaths);
        Log.info("[OneTry] Player @ died. Count: @", p.name, deaths);

        respawnBlocked.add(p.uuid());
        setDeathTimer(p, 0f);

        Call.infoMessage(p.con, "[scarlet]" + bundle.get("onetry.death.title") + "!\n[lightgray]" + bundle.get("onetry.respawn.blocked"));
        showDeathScreen();
    }

    private void setDeathTimer(Player p, float time) {
        if (deathTimerField != null) {
            try {
                deathTimerField.setFloat(p, time);
            } catch (Throwable t) {
                Log.warn("[OneTry] Failed to set deathTimer for @", p.name);
            }
        }
    }

    public void showComplexityWarning() {
        if (OneTrySettings.hasShownWarning()) return;
        Core.app.post(() -> Vars.ui.showCustomConfirm(
                bundle.get("onetry.warning.title"),
                bundle.get("onetry.warning.text"),
                bundle.get("onetry.warning.accept"),
                bundle.get("onetry.settings.button"),
                OneTrySettings::markWarningShown,
                () -> { OneTrySettings.showDifficultyDialog(); OneTrySettings.markWarningShown(); }
        ));
    }

    private void showDeathScreen() {
        Core.app.post(() -> {
            BaseDialog dialog = new BaseDialog(bundle.get("onetry.death.title")) {
                @Override
                public void hide() {
                }
            };

            Table cont = dialog.cont;
            cont.defaults().pad(10).width(500f);
            cont.add(bundle.get("onetry.death.text")).wrap().left().row();

            dialog.buttons.clear();
            dialog.buttons.button(bundle.get("onetry.death.confirm"), () -> {
                boolean wipeOccurred = OneTrySettings.isUltraMode() || OneTrySettings.isDeathMode() ||
                        Core.settings.getBool(OneTrySettings.KEY_DELETE_SAVES, false);

                if (wipeOccurred) {
                    wipeProgress();
                    Core.settings.put(OneTrySettings.KEY_WARNING_SHOWN, false);
                }

                Time.runTask(5f, () -> {
                    Vars.state.set(GameState.State.menu);
                    Core.settings.forceSave();
                    Core.app.post(() -> Core.app.post(OneTrySettings::showDifficultyDialog));
                });

                dialog.remove();
            }).size(180f, 55f).pad(12);

            dialog.setFillParent(false);
            dialog.show();
        });
    }

    private void wipeProgress() {
        Vars.content.each(c -> { if (c instanceof UnlockableContent uc) uc.clearUnlock(); });
        if (Vars.state.isCampaign()) Vars.universe.clearLoadoutInfo();
        if (Vars.state.getSector() != null) Vars.state.getSector().clearInfo();
        if (Vars.control.saves.getCurrent() != null) Vars.control.saves.getCurrent().delete();
        Vars.content.planets().each(planet -> planet.sectors.each(sector -> sector.clearInfo()));
        clearAllPlayerState();
        Log.info("[OneTry] Progress has been wiped.");
    }

    private void clearPlayerState(String uuid, Player p) {
        respawnBlocked.remove(uuid);
        deathCount.remove(uuid);
        wasAlive.remove(uuid);
        if (p != null) {
            setDeathTimer(p, 0f);
            Core.app.post(() -> { if (p.unit() != null) p.clearUnit(); });
        }
    }

    private void clearAllPlayerState() {
        respawnBlocked.clear();
        deathCount.clear();
        wasAlive.clear();
        Groups.player.each(p -> setDeathTimer(p, 0f));
        Log.info("[OneTry] Internal player state cleared.");
    }
}