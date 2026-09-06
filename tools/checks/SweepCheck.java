import com.hexhaki.fruit.NikaSweep;

public class SweepCheck {
    static int fails=0;
    static void check(boolean ok,String w){System.out.println((ok?"  PASS  ":"  FAIL  ")+w); if(!ok)fails++;}

    // A player-sized target standing at x=10, 0.6 wide, 1.8 tall.
    static final double MNX=9.7,MNY=0.0,MNZ=-0.3,MXX=10.3,MXY=1.8,MXZ=0.3;

    static double sweep(double ax,double ay,double az,double bx,double by,double bz,double r){
        return NikaSweep.sweep(ax,ay,az,bx,by,bz,r,MNX,MNY,MNZ,MXX,MXY,MXZ);
    }

    public static void main(String[] x){
        // THE headline case: a fist that is BEFORE the target at tick N and PAST it at tick N+1.
        // A naive endpoint-only test finds nothing at either tick.
        double t = sweep(0,1.0,0, 20,1.0,0, 0.25);
        check(NikaSweep.hit(t), "fast fist crossing the target in one tick registers (t="+String.format("%.4f",t)+")");
        boolean endpointsMiss =
            NikaSweep.distanceSqToBox(0,1.0,0, MNX,MNY,MNZ,MXX,MXY,MXZ) > 0.25*0.25 &&
            NikaSweep.distanceSqToBox(20,1.0,0, MNX,MNY,MNZ,MXX,MXY,MXZ) > 0.25*0.25;
        check(endpointsMiss, "...and an endpoint-only test would indeed have missed it");

        // Entry fraction should land just before the near face at x=9.7-0.25=9.45 -> 9.45/20
        check(Math.abs(t-9.45/20.0)<1e-9, "entry fraction is the near inflated face ("+String.format("%.5f",t)+")");
        double hx = NikaSweep.lerp(0,20,t);
        check(Math.abs(hx-9.45)<1e-9, "impact point sits on the fist's path, not at the target centre");

        // Clean misses
        check(!NikaSweep.hit(sweep(0,1.0,5, 20,1.0,5, 0.25)), "sweep offset in Z misses");
        check(!NikaSweep.hit(sweep(0,5.0,0, 20,5.0,0, 0.25)), "sweep passing overhead misses");
        check(!NikaSweep.hit(sweep(0,1.0,0, 5,1.0,0, 0.25)),  "sweep stopping short misses");
        check(!NikaSweep.hit(sweep(12,1.0,0, 20,1.0,0, 0.25)),"sweep starting past the target misses");

        // Radius matters: an enlarged fist connects where a small one does not.
        check(!NikaSweep.hit(sweep(0,1.0,1.0, 20,1.0,1.0, 0.25)), "thin fist misses a 1.0 offset");
        check(NikaSweep.hit(sweep(0,1.0,1.0, 20,1.0,1.0, 1.0)),   "enlarged fist connects at the same offset");

        // Already overlapping at the start of the tick -> immediate contact.
        double t0 = sweep(10,1.0,0, 12,1.0,0, 0.25);
        check(NikaSweep.hit(t0) && t0==0.0, "a sweep starting inside the target reports t=0");

        // Stationary fist inside/outside.
        check(NikaSweep.hit(sweep(10,1.0,0, 10,1.0,0, 0.25)), "stationary fist inside the target hits");
        check(!NikaSweep.hit(sweep(0,1.0,0, 0,1.0,0, 0.25)),  "stationary fist away from the target misses");

        // Diagonal / descending sweep, as Bajrang Gun's fist comes down.
        check(NikaSweep.hit(sweep(10,40,0, 10,0.5,0, 3.0)), "colossal descending fist connects on the way down");

        // Monotonicity: growing the radius must never turn a hit into a miss.
        boolean mono=true;
        for(double r=0.1;r<=3.0;r+=0.1){
            if(NikaSweep.hit(sweep(0,1.0,0.8, 20,1.0,0.8, r))){
                for(double r2=r;r2<=3.0;r2+=0.1) mono &= NikaSweep.hit(sweep(0,1.0,0.8, 20,1.0,0.8, r2));
                break;
            }
        }
        check(mono,"a larger fist never loses a hit a smaller one landed");

        // Entry fraction always within [0,1] when a hit is reported.
        boolean bounded=true;
        for(double sx=-5;sx<=25;sx+=0.7) for(double sy=-2;sy<=6;sy+=0.7){
            double v=sweep(sx,sy,0, sx+14,sy+1,0, 0.4);
            if(NikaSweep.hit(v)) bounded &= v>=0.0 && v<=1.0;
        }
        check(bounded,"reported entry fraction is always within [0,1]");

        // Arc subdivision for Dawn Whip's curved foot path.
        int n = NikaSweep.arcSubdivisions(11.0, Math.PI, 0.15);
        check(n>1 && n<=64, "a half-circle whip arc at r=11 subdivides into "+n+" steps");
        check(NikaSweep.arcSubdivisions(11.0, Math.PI, 0.02) > n, "a tighter tolerance subdivides further");
        check(NikaSweep.arcSubdivisions(0,0,0.1)==1, "a degenerate arc needs a single step");

        System.out.println(fails==0?"\nALL CHECKS PASSED":"\n"+fails+" CHECK(S) FAILED");
        if(fails>0) System.exit(1);
    }
}
