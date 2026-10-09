package ot;

import arc.util.Log;
import mindustry.Vars;
import mindustry.mod.Mod;
import ot.graphics.draw3d.Draw3D;
import ot.world.FiniteOres;

public class OneTry extends Mod {

    @Override
    public void init() {
        OneTrySettings.initializeDefaults();
        FiniteOres.init();
        OneTryIndustry.init();
        new OneTryFunctionality();

        if (!Vars.headless) {
            Draw3D.init();
            OneTryPlanetUI.init();
            OneTryDeathScene.init();
            OneTryGameOver.init();
        }

        Log.info("[OneTry] loaded");
    }

}