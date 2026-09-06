package com.hexhaki.client;

import com.hexhaki.network.msg.S2CEntityState;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RemoteHakiStates {
    public record State(boolean armament,boolean acoc,boolean ryuo,int armamentMastery,int conquerorMastery,long touched){}
    private static final Map<Integer,State> STATES=new ConcurrentHashMap<>();
    private RemoteHakiStates(){}
    public static void accept(S2CEntityState m){STATES.put(m.entityId(),new State(m.armament(),m.acoc(),m.ryuo(),m.armamentMastery(),m.conquerorMastery(),System.currentTimeMillis()));}
    public static State get(int id){State state=STATES.get(id);if(state!=null&&System.currentTimeMillis()-state.touched()>3000){STATES.remove(id,state);return null;}return state;}
    public static void clear(){STATES.clear();}
}
