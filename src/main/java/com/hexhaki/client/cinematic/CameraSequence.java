package com.hexhaki.client.cinematic;

import java.util.List;

public record CameraSequence(String name, List<CameraKeyframe> frames) {
    public float duration(){return frames.isEmpty()?0:frames.get(frames.size()-1).time();}
}
