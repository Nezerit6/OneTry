package ot.graphics.draw3d;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import mindustry.game.EventType.*;

import static arc.Core.*;
import static mindustry.Vars.*;

/**
 * Pseudo-3D perspective projection by MEEPofFaith
 * (https://github.com/MEEPofFaith/prog-mats-java, GPL-3.0).
 */

public class Perspective{
    private static final Vec2 offsetPos = new Vec2();
    private static final Vec3 scalingPos = new Vec3();
    private static final float groundTolerance = 0.001f;
    public static float viewportOffset = 80f;
    public static float fov = 60f;
    public static float fadeDst = 1024f;

    private static float lastScale, lastFov;
    private static float cameraZ;
    private static final Vec2 viewportSize = new Vec2();

    static{
        if(!headless){
            Events.run(Trigger.preDraw, () -> {
                if(renderer.getDisplayScale() != lastScale || fov != lastFov){
                    lastScale = renderer.getDisplayScale();
                    lastFov = fov;
                    cameraZ = calcCameraZ();
                    updateViewportSize();
                }
            });

            Events.on(ResizeEvent.class, e -> app.post(() -> {
                cameraZ = calcCameraZ();
                updateViewportSize();
            }));
        }
    }

    public static Vec2 drawPos(float x, float y, float z){
        if(z <= groundTolerance) return offsetPos.set(x, y);

        float vw = viewportSize.x, vh = viewportSize.y;
        float cx = camera.position.x, cy = camera.position.y;
        Vec3 scaled = scaleToViewport(x, y, z);

        offsetPos.set(scaled.x / vw * camera.width, scaled.y / vh * camera.height).add(cx, cy);
        return offsetPos;
    }

    public static float scale(float x, float y, float z){
        if(z <= groundTolerance) return 1f;

        float cx = camera.position.x, cy = camera.position.y;
        float cz = cameraZ;

        x -= cx;
        y -= cy;
        float zz = cz - z;

        float px = x / zz * cz;
        float py = y / zz * cz;

        float vx = x / zz * viewportOffset;
        float vy = y / zz * viewportOffset;

        float d1 = dst(vx, vy, cz - viewportOffset, x, y, z);
        float d2 = dst(vx, vy, cz - viewportOffset, px, py, 0);

        return 1f + (1 / viewportSize.x * camera.width - 1) * (1f - d1/d2);
    }

    public static float alpha(float x, float y, float z){
        if(z <= groundTolerance) return 1f;

        float vz = viewportZ();

        float dst = dstToViewport(x, y, z);
        float fade = Math.min(fadeDst, vz);

        if(dst > fade){
            return 1f;
        }else if(z > vz){
            return 0f;
        }else{
            return Interp.pow5In.apply(Mathf.clamp(dst / fade));
        }
    }

    public static float viewportZ(){
        return Math.max(cameraZ - viewportOffset, 0f);
    }

    public static Vec3 scaleToViewport(float x, float y, float z){
        if(z <= groundTolerance) return scalingPos.set(x, y, 0);

        float cx = camera.position.x, cy = camera.position.y;

        x -= cx;
        y -= cy;
        float zz = cameraZ - z;

        return scalingPos.set(x / zz * viewportOffset, y / zz * viewportOffset, viewportZ());
    }

    public static float dstToViewport(float x, float y, float z){
        Vec3 scaled = scaleToViewport(x, y, z);
        return scaled.dst(x - camera.position.x, y - camera.position.y, z);
    }

    private static void updateViewportSize(){
        float v1 = (float)(Math.tan(fov / 2f * Mathf.degRad) * viewportOffset * 2f);
        if(camera.width >= camera.height){
            float v2 = v1 * (camera.height / camera.width);
            viewportSize.set(v1, v2);
        }else{
            float v2 = v1 * (camera.width / camera.height);
            viewportSize.set(v2, v1);
        }
    }

    private static float calcCameraZ(){
        float width = Math.max(camera.width, camera.height) / 2f;
        return (float)(width / Math.tan(fov / 2f * Mathf.degRad));
    }

    private static float dst(float x1, float y1, float z1, float x2, float y2, float z2){
        float xd = x2 - x1;
        float yd = y2 - y1;
        float zd = z2 - z1;
        return Mathf.sqrt(xd * xd + yd * yd + zd * zd);
    }
}