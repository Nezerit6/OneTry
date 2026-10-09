package ot;

import arc.Core;
import arc.Events;
import arc.func.Boolp;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Musics;
import mindustry.gen.Unit;

public class OneTryDeathScene {
    private static final float impactTime = 2.42f;
    private static final float statsDelay = 2.5f;

    private static final Boolp lock = OneTryDeathScene::active;
    private static final Vec2 velocity = new Vec2(), focus = new Vec2(), offset = new Vec2();

    private static Unit falling;
    private static float startElevation, clock, after, angle, direction;
    private static boolean active, crashed, owned;

    public static void init() {
        Events.run(EventType.Trigger.update, OneTryDeathScene::update);
        Events.run(EventType.Trigger.preDraw, OneTryDeathScene::preDraw);
        Events.on(EventType.ResetEvent.class, e -> end());
    }

    public static boolean active() {
        return active;
    }

    public static void play(Unit unit) {
        if (active || Vars.headless) return;

        active = true;
        crashed = false;
        clock = after = 0f;

        falling = unit != null && unit.isAdded() && unit.type.flying ? unit : null;
        if (unit != null) focus.set(unit.x, unit.y);
        else focus.set(Core.camera.position);
        offset.set(Core.camera.position).sub(focus);

        if (falling != null) {
            startElevation = Math.max(falling.elevation, 0.5f);
            velocity.set(falling.vel);
            angle = falling.rotation;
            direction = Mathf.randomSign();
        }

        if (!Vars.control.input.inputLocks.contains(lock)) Vars.control.input.addLock(lock);

        owned = true;
        Vars.control.sound.stop();
        Musics.land.stop();
        Musics.land.setLooping(false);
        Musics.land.setVolume(Core.settings.getInt("musicvol", 100) / 100f);
        Musics.land.play();
    }

    private static void update() {
        if (owned) {
            if (active || Musics.land.isPlaying()) Vars.control.sound.stop();
            else owned = false;
        }

        if (!active) return;
        if (Vars.state.isMenu()) {
            end();
            return;
        }

        boolean paused = Vars.state.isPaused();

        if (!crashed) {
            Musics.land.pause(paused);
            if (Musics.land.isPlaying()) {
                clock = Math.max(clock, Musics.land.getPosition());
            } else if (!paused) {
                clock += Time.delta / 60f;
            }

            if (falling != null && falling.isAdded()) {
                float progress = Mathf.clamp(clock / impactTime);
                falling.elevation = Math.max(startElevation * (1f - Interp.pow2In.apply(progress)), 0.12f);
                falling.health = 0f;
                falling.vel.set(velocity);
                if (!paused) angle += Mathf.lerp(3f, 16f, progress) * direction * Time.delta;
                falling.rotation = angle;
                focus.set(falling.x, falling.y);
            }

            if (clock >= impactTime) crash();
            return;
        }

        if (!paused) after += Time.delta / 60f;
        if (after >= statsDelay) finish();
    }

    private static void crash() {
        crashed = true;
        Musics.land.pause(false);
        if (falling != null && falling.isAdded()) {
            focus.set(falling.x, falling.y);
            falling.rotation = angle;
            falling.elevation = 0f;
        }
        falling = null;
    }

    private static void preDraw() {
        if (!active) return;
        if (falling != null && falling.isAdded()) {
            falling.rotation = angle;
            focus.set(falling.x, falling.y);
        }
        Core.camera.position.set(focus).add(offset);
    }

    private static void finish() {
        end();
        Vars.ui.restart.show(Vars.state.rules.waveTeam);
    }

    private static void end() {
        active = false;
        falling = null;
    }
}