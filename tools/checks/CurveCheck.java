import com.hexhaki.fruit.NikaCurves;

public class CurveCheck {
    static int fails=0;
    static void check(boolean ok,String w){System.out.println((ok?"  PASS  ":"  FAIL  ")+w); if(!ok)fails++;}
    static boolean near(float a,float b,float eps){return Math.abs(a-b)<=eps;}

    public static void main(String[] x){
        // --- endpoints: a deformation that does not return to 0 leaves a broken body ---
        check(near(NikaCurves.elasticSettle(0f),0f,1e-6f), "elasticSettle(0) == 0");
        check(near(NikaCurves.elasticSettle(1f),1f,1e-6f), "elasticSettle(1) == 1");
        check(near(NikaCurves.strikeExtension(0f),0f,1e-4f), "strikeExtension(0) == 0 (rest)");
        check(near(NikaCurves.strikeExtension(1f),0f,1e-4f), "strikeExtension(1) == 0 (returns to normal model)");
        check(near(NikaCurves.groundCompression(0f),0f,1e-6f), "groundCompression(0) == 0");
        check(near(NikaCurves.groundCompression(1f),0f,1e-6f), "groundCompression(1) == 0 (fully temporary)");

        // --- anticipation really pulls back ---
        float minA=1f; for(int i=0;i<=22;i++){ float t=i/100f; minA=Math.min(minA,NikaCurves.strikeExtension(t)); }
        check(minA < -0.15f, "wind-up pulls the fist behind the shoulder (min="+String.format("%.3f",minA)+")");

        // --- impact reaches full reach ---
        float atImpact=NikaCurves.strikeExtension(0.55f);
        check(near(atImpact,1f,0.02f), "full reach at the impact frame ("+String.format("%.3f",atImpact)+")");

        // --- overshoot exists (elastic recoil, not a linear retract) ---
        float maxE=0f; for(int i=55;i<=100;i++){ maxE=Math.max(maxE,NikaCurves.strikeExtension(i/100f)); }
        check(maxE>1.0f, "follow-through overshoots past full reach (max="+String.format("%.3f",maxE)+")");

        // --- bounded: nothing may fly off to absurd extension ---
        boolean bounded=true; for(int i=0;i<=1000;i++){ float v=NikaCurves.strikeExtension(i/1000f); bounded &= v>-0.5f && v<1.5f; }
        check(bounded,"strikeExtension stays within [-0.5,1.5] across the whole move");

        // --- volume preservation: 4x stretch must halve the cross-section ---
        check(near(NikaCurves.perpendicularForStretch(4f),0.5f,1e-5f),"4x stretch -> 0.5x cross-section");
        check(near(NikaCurves.perpendicularForStretch(1f),1f,1e-6f),"rest stretch -> unchanged cross-section");
        boolean vol=true; for(float s=1f;s<=12f;s+=0.25f){ float p=NikaCurves.perpendicularForStretch(s); vol &= near(s*p*p,1f,1e-3f); }
        check(vol,"volume s*p^2 == 1 holds across 1x..12x stretch");

        // --- ripple causality and decay ---
        check(near(NikaCurves.ripple(10f,0.1f,4f,1.5f),0f,1e-9f),"ripple is 0 ahead of the wavefront");
        float early=Math.abs(NikaCurves.ripple(1f,0.30f,4f,1.5f));
        float late =Math.abs(NikaCurves.ripple(1f,2.00f,4f,1.5f));
        check(late<early,"ripple decays with time ("+String.format("%.5f",early)+" -> "+String.format("%.5f",late)+")");
        boolean rb=true; for(int d=0;d<40;d++) for(int a=0;a<40;a++) rb &= Math.abs(NikaCurves.ripple(d*0.5f,a*0.1f,4f,1.5f))<=1.0f;
        check(rb,"ripple magnitude never exceeds 1");

        // --- ground compression: dimples down then springs back over the top ---
        float peak=0f,trough=0f;
        for(int i=0;i<=1000;i++){ float v=NikaCurves.groundCompression(i/1000f); peak=Math.max(peak,v); trough=Math.min(trough,v); }
        check(peak>0.9f,"compression reaches full depth (peak="+String.format("%.3f",peak)+")");
        check(trough<-0.02f,"surface rebounds past flat before settling (trough="+String.format("%.3f",trough)+")");

        // --- smoothstep sanity ---
        check(near(NikaCurves.smoothstep(0f),0f,1e-6f)&&near(NikaCurves.smoothstep(1f),1f,1e-6f),"smoothstep endpoints");
        check(near(NikaCurves.smootherstep(0.5f),0.5f,1e-6f),"smootherstep is symmetric at midpoint");

        // --- clamping: out-of-range input must not produce NaN/garbage ---
        boolean safe=true;
        for(float t : new float[]{-5f,-0.1f,1.1f,7f}){
            safe &= Float.isFinite(NikaCurves.strikeExtension(t)) && Float.isFinite(NikaCurves.elasticSettle(t))
                 && Float.isFinite(NikaCurves.groundCompression(t));
        }
        check(safe,"out-of-range progress is clamped, never NaN");

        System.out.println(fails==0?"\nALL CHECKS PASSED":"\n"+fails+" CHECK(S) FAILED");
        if(fails>0) System.exit(1);
    }
}
