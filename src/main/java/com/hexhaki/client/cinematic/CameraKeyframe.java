package com.hexhaki.client.cinematic;

import net.minecraft.world.phys.Vec3;

public record CameraKeyframe(float time, Vec3 offset, float yaw, float pitch, float roll, float fovDelta, float shake, float letterbox, Easing easing) {
    public enum Easing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, CUBIC, QUART, EXPO }
    public static double ease(Easing e,double t){
        t=Math.max(0,Math.min(1,t));
        return switch(e){
            case LINEAR -> t;
            case EASE_IN -> t*t;
            case EASE_OUT -> 1-(1-t)*(1-t);
            case EASE_IN_OUT -> t<.5?2*t*t:1-Math.pow(-2*t+2,2)/2;
            case CUBIC -> t*t*(3-2*t);
            case QUART -> t<.5?8*Math.pow(t,4):1-Math.pow(-2*t+2,4)/2;
            case EXPO -> t==0?0:(t==1?1:(t<.5?Math.pow(2,20*t-10)/2:(2-Math.pow(2,-20*t+10))/2));
        };
    }
}
