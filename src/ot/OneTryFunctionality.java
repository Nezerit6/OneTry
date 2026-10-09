package ot;

import arc.Core;
import arc.Events;
import arc.struct.IntFloatMap;
import arc.struct.ObjectFloatMap;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.TechTree;
import mindustry.core.GameState;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType;
import mindustry.game.Saves.SaveSlot;
import mindustry.gen.*;
import mindustry.type.Planet;
import mindustry.type.Sector;
import mindustry.ui.dialogs.BaseDialog;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OneTryFunctionality {
    private final Set<String> respawnBlocked = ConcurrentHashMap.newKeySet();
    private final Map<String, Unit> lastUnit = new ConcurrentHashMap<>();
    private final ObjectFloatMap<String> graceTime = new ObjectFloatMap<>();
    private final Map<Integer, Float> previousHealth = new ConcurrentHashMap<>();
    private final IntFloatMap coreHealth = new IntFloatMap();
    private SaveSlot lostSlot;

    public OneTryFunctionality() {
        setupListeners();
    }

    private static boolean ignored(Player p) {
        return Vars.net.client() && !p.isLocal();
    }

    private static boolean campaignRun() {
        return Vars.state.isCampaign() && !Vars.net.client();
    }

    private void setupListeners() {
        Events.run(EventType.Trigger.update, () -> {
            if (OneTrySettings.isSafe() || !Vars.state.isCampaign()) return;

            Groups.player.each(p -> {
                if (ignored(p)) return;

                String uuid = p.uuid();
                if (respawnBlocked.contains(uuid)) {
                    if (!Vars.net.client()) {
                        p.deathTimer = 0f;
                        if (p.unit() != null) Core.app.post(p::clearUnit);
                    }
                    return;
                }

                Unit unit = p.unit();
                if (unit != null && unit.isValid()) {
                    lastUnit.put(uuid, unit);
                    graceTime.remove(uuid, 0f);
                    return;
                }

                Unit last = lastUnit.get(uuid);
                if (last == null) return;

                if (last.dead()) {
                    forget(uuid);
                    triggerPermadeath(p, last);
                    return;
                }

                float waited = graceTime.get(uuid, 0f) + Time.delta;
                if (!Vars.net.client() || !last.isAdded() || waited > 60f) {
                    forget(uuid);
                } else {
                    graceTime.put(uuid, waited);
                }
            });

            if (Vars.net.client()) return;

            if (OneTrySettings.isTerminal()) {
                Vars.state.rules.defaultTeam.cores().each(core -> {
                    float last = coreHealth.get(core.pos(), -1f);
                    if (last >= 0f && core.health < last) {
                        core.kill();
                    } else {
                        coreHealth.put(core.pos(), core.health);
                    }
                });
            }

            if (OneTrySettings.isBrittle() || OneTrySettings.isTerminal()) {
                Groups.player.each(p -> {
                    Unit u = p.unit();
                    if (u == null) return;
                    if (respawnBlocked.contains(p.uuid())) {
                        previousHealth.remove(u.id);
                        return;
                    }

                    Healthc target = u;
                    if (u instanceof BlockUnitc bu && bu.tile() != null) target = bu.tile();

                    Float prevHealth = previousHealth.get(u.id);
                    if (prevHealth != null && OneTrySettings.isTerminal() && target.health() < prevHealth) {
                        target.kill();
                    } else if (prevHealth != null && prevHealth <= target.maxHealth() && target.health() < prevHealth) {
                        float extra = (prevHealth - target.health())
                                * (OneTrySettings.incomingDamageMultiplier() - 1f);
                        if (extra > 0f) {
                            if (target instanceof Building b) {
                                b.damage(extra);
                            } else {
                                target.health(target.health() - extra);
                                if (target.health() <= 0f) target.kill();
                            }
                        }
                    }

                    previousHealth.put(u.id, target.health());
                });
            }
        });

        Events.on(EventType.UnitChangeEvent.class, e -> {
            if (Vars.net.client() || OneTrySettings.isSafe() || !Vars.state.isCampaign() || e.player == null) return;
            if (e.unit != null && respawnBlocked.contains(e.player.uuid())) Core.app.post(e.player::clearUnit);
        });

        Events.on(EventType.PlayerLeave.class, e -> {
            if (e.player != null) forget(e.player.uuid());
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            Core.app.post(OneTrySettings::shareRules);
            Core.app.post(this::clearAllPlayerState);
            previousHealth.clear();
            coreHealth.clear();
            if (!Vars.headless) Core.app.post(this::lockRunIfNeeded);
        });

        Events.on(EventType.GameOverEvent.class, e -> {
            if (Vars.headless || !OneTrySettings.isTerminal() || !campaignRun() || e.winner == Vars.player.team()) return;
            lostSlot = Vars.control.saves.getCurrent();
            OneTrySettings.markRunLost();
        });

        Events.on(EventType.ClientLoadEvent.class, e -> {
            if (Vars.headless) return;

            Vars.ui.restart.hidden(() -> {
                if (OneTrySettings.isRunLost() && Vars.state.isCampaign()) {
                    Core.app.post(() -> {
                        Vars.ui.planet.hide();
                        exitToMenu();
                    });
                }
            });

            if (OneTrySettings.isRunLost()) finishLostRun();
        });

        Events.on(EventType.StateChangeEvent.class, e -> {
            if (e.to == GameState.State.menu) {
                Core.app.post(this::clearAllPlayerState);
                previousHealth.clear();
                coreHealth.clear();
                if (OneTrySettings.isRunLost()) Core.app.post(this::finishLostRun);
            }
        });
    }

    private void triggerPermadeath(Player p, Unit unit) {
        Log.info("[OneTry] Player @ died.", p.name);

        respawnBlocked.add(p.uuid());
        if (!Vars.net.client()) p.deathTimer = 0f;
        if (Vars.headless || !p.isLocal()) return;

        if (Vars.net.client()) {
            showDeathScreen(true);
        } else if (OneTrySettings.wipeOnDeath()) {
            lostSlot = Vars.control.saves.getCurrent();
            OneTrySettings.markRunLost();
            OneTryDeathScene.play(unit);
        } else {
            showDeathScreen(false);
        }
    }

    private void exitToMenu() {
        if (Vars.net.client()) Vars.netClient.disconnectQuietly();
        if (Vars.ui.paused.checkPlaytest()) return;
        Vars.logic.reset();
    }

    private void showDeathScreen(boolean guest) {
        Core.app.post(() -> {
            BaseDialog dialog = new BaseDialog(Core.bundle.get("onetry.death.title")) {
                @Override
                public void hide() {
                    if (guest) super.hide();
                }
            };

            float width = Math.min(Core.graphics.getWidth() / Scl.scl(1.05f) - Scl.scl(28f), 500f);
            String text = Core.bundle.get("onetry.death.text");
            if (guest) text += "\n" + Core.bundle.get("onetry.death.guest");
            else if (OneTrySettings.wipeOnDeath()) text += "\n" + Core.bundle.get("onetry.death.wipe");

            Table cont = dialog.cont;
            cont.defaults().pad(10).width(width);
            cont.add(text).wrap().left().row();

            dialog.buttons.clear();
            if (guest) dialog.buttons.button(Core.bundle.get("onetry.death.spectate"), dialog::hide).size(180f, 55f).pad(12);
            dialog.buttons.button(Core.bundle.get("onetry.death.confirm"), () -> {
                if (guest) {
                    dialog.remove();
                    exitToMenu();
                    return;
                }
                boolean lost = OneTrySettings.isRunLost();
                SaveSlot current = Vars.control.saves.getCurrent();

                dialog.remove();
                exitToMenu();

                if (lost) return;
                if (OneTrySettings.wipeOnDeath() && current != null) current.delete();
                Core.app.post(() -> Core.app.post(OneTrySettings::showDifficultyDialog));
            }).size(180f, 55f).pad(12);

            dialog.setFillParent(false);
            dialog.show();
        });
    }

    private void lockRunIfNeeded() {
        if (OneTrySettings.isTerminal() && campaignRun() && !OneTrySettings.isRunLost()) OneTrySettings.lockRun();
    }

    private void finishLostRun() {
        if (!OneTrySettings.isRunLost() || !Vars.state.isMenu()) return;

        SaveSlot slot = lostSlot;
        lostSlot = null;
        wipeProgress(slot);
        OneTrySettings.clearRunLost();
        OneTrySettings.unlockRun();
        Core.settings.forceSave();
        Core.app.post(() -> Core.app.post(OneTrySettings::showDifficultyDialog));
    }

    private void wipeProgress(SaveSlot current) {
        Vars.content.each(c -> { if (c instanceof UnlockableContent uc) uc.clearUnlock(); });
        for (TechTree.TechNode node : TechTree.all) node.reset();
        Vars.universe.clearLoadoutInfo();
        Core.settings.remove("unlocks");

        for (Planet planet : Vars.content.planets()) {
            for (Sector sector : planet.sectors) {
                sector.clearInfo();
                if (sector.save != null) {
                    sector.save.delete();
                    sector.save = null;
                }
            }
        }
        for (SaveSlot slot : Vars.control.saves.getSaveSlots().copy()) {
            if (slot.isSector()) slot.delete();
        }
        if (current != null) current.delete();

        clearAllPlayerState();
        Log.info("[OneTry] Progress has been wiped.");
    }

    private void forget(String uuid) {
        lastUnit.remove(uuid);
        graceTime.remove(uuid, 0f);
    }

    private void clearAllPlayerState() {
        respawnBlocked.clear();
        lastUnit.clear();
        graceTime.clear();
    }
}