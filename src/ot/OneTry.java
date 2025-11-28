package ot;

import arc.util.Log;
import mindustry.mod.Mod;

public class OneTry extends Mod {

    private OneTryFunctionality functionality;

    @Override
    public void init() {
        Log.info("OneTry complete");

        OneTrySettings.initializeDefaults();
        OneTrySettings.init();

        functionality = new OneTryFunctionality();
        functionality.showComplexityWarning();

        Log.info("OneTry loaded - No respawn after death");
    }

}