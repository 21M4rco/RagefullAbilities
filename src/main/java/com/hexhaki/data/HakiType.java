package com.hexhaki.data;

public enum HakiType {
    ARMAMENT("Armament"),
    OBSERVATION("Observation"),
    CONQUEROR("Conqueror's");

    public final String display;
    HakiType(String display) { this.display = display; }
}
