package ot;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.struct.ObjectMap;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.graphics.Pal;
import mindustry.world.Block;
import mindustry.world.meta.Stats;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.Separator;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.blocks.units.UnitFactory;

public class OneTryIndustry {
    public static final String KEY_INDUSTRY = "ot-industry";

    public static final String FULL = "full";
    public static final String SLOW = "slow";
    public static final String MEAGER = "meager";
    public static final String DEAD = "dead";

    public static final String[] tiers = {FULL, SLOW, MEAGER};

    private static final ObjectMap<Block, float[]> baseTimes = new ObjectMap<>();
    private static boolean scaled;

    public static void init() {
        for (Block block : Vars.content.blocks()) {
            float[] times = readTimes(block);
            if (times != null) baseTimes.put(block, times);
        }

        Events.on(EventType.WorldLoadEvent.class, e -> Core.app.post(OneTryIndustry::apply));
        Events.on(EventType.StateChangeEvent.class, e -> {
            if (e.to == GameState.State.menu) restore();
        });
    }

    public static void initializeDefaults() {
        if (!Core.settings.has(KEY_INDUSTRY)) Core.settings.put(KEY_INDUSTRY, FULL);
    }

    public static String get() {
        String tier = OneTrySettings.guest()
                ? Vars.state.rules.tags.get(OneTrySettings.TAG_INDUSTRY, FULL)
                : Core.settings.getString(KEY_INDUSTRY, FULL);
        if (tier.equals(SLOW) || tier.equals(MEAGER)) return tier;
        if (tier.equals(DEAD)) return MEAGER;
        return FULL;
    }


    public static float speedMultiplier() {
        return speedMultiplierOf(get());
    }

    public static float speedMultiplierOf(String tier) {
        switch (tier) {
            case SLOW: return 0.7f;
            case MEAGER: return 0.45f;
            case DEAD: return 0.25f;
            default: return 1f;
        }
    }

    public static String stats(String tier) {
        if (tier.equals(FULL)) return Core.bundle.get("onetry.stat.nomodifiers");
        int percent = Math.round((1f - speedMultiplierOf(tier)) * 100f);
        return OneTrySettings.statLine("onetry.stat.factory", "-" + percent + "%");
    }

    public static Color color(String tier) {
        switch (tier) {
            case SLOW: return Color.orange;
            case MEAGER: return Pal.remove;
            case DEAD: return Color.scarlet;
            default: return Pal.heal;
        }
    }

    public static void set(String tier) {
        Core.settings.put(KEY_INDUSTRY, tier);
        Core.settings.forceSave();
        apply();
    }

    private static float[] readTimes(Block block) {
        if (block instanceof GenericCrafter crafter) return new float[]{crafter.craftTime};
        if (block instanceof Separator separator) return new float[]{separator.craftTime};
        if (block instanceof Reconstructor reconstructor) return new float[]{reconstructor.constructTime};
        if (block instanceof UnitFactory factory) {
            float[] times = new float[factory.plans.size];
            for (int i = 0; i < times.length; i++) times[i] = factory.plans.get(i).time;
            return times;
        }
        return null;
    }

    private static void writeTimes(Block block, float[] base, float divisor) {
        if (block instanceof GenericCrafter crafter) crafter.craftTime = base[0] / divisor;
        else if (block instanceof Separator separator) separator.craftTime = base[0] / divisor;
        else if (block instanceof Reconstructor reconstructor) reconstructor.constructTime = base[0] / divisor;
        else if (block instanceof UnitFactory factory) {
            for (int i = 0; i < base.length && i < factory.plans.size; i++) {
                factory.plans.get(i).time = base[i] / divisor;
            }
        }
        Stats fresh = new Stats();
        fresh.useCategories = block.stats.useCategories;
        block.stats = fresh;
    }

    public static void apply() {
        if (!Vars.state.isGame()) return;

        float mult = Vars.state.isCampaign() ? speedMultiplier() : 1f;
        if (mult == 1f) {
            restore();
            return;
        }

        for (ObjectMap.Entry<Block, float[]> entry : baseTimes) writeTimes(entry.key, entry.value, mult);
        scaled = true;
    }

    private static void restore() {
        if (!scaled) return;
        for (ObjectMap.Entry<Block, float[]> entry : baseTimes) writeTimes(entry.key, entry.value, 1f);
        scaled = false;
    }
}