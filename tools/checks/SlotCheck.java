import com.hexhaki.fruit.*;
import java.util.*;

public class SlotCheck {
    static int fails = 0;
    static void check(boolean ok, String what) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) fails++;
    }
    public static void main(String[] a) {
        List<NikaTechnique> keyed = new ArrayList<>();
        for (NikaTechnique t : NikaTechnique.values())
            if (t.activation() == NikaTechnique.Activation.KEYED) keyed.add(t);

        check(keyed.size() == 7, "exactly 7 keyed techniques (got " + keyed.size() + ")");

        // Every slot filled exactly once by a keyed technique.
        Map<NikaSlot, Integer> perSlot = new EnumMap<>(NikaSlot.class);
        for (NikaTechnique t : keyed) perSlot.merge(t.slot(), 1, Integer::sum);
        check(perSlot.size() == NikaSlot.values().length, "every slot has a keyed technique");
        boolean noDup = perSlot.values().stream().allMatch(v -> v == 1);
        check(noDup, "no slot is claimed twice (a key never fires two fruit moves)");

        // Contextual techniques must either share a real slot or be combo-driven (null slot).
        for (NikaTechnique t : NikaTechnique.values()) {
            if (t.activation() == NikaTechnique.Activation.CONTEXTUAL && t.slot() != null)
                check(NikaTechnique.keyedFor(t.slot()) != null,
                        t.display() + " shares a slot that has a keyed owner");
        }
        // Passives must never claim a binding.
        for (NikaTechnique t : NikaTechnique.values())
            if (t.activation() == NikaTechnique.Activation.PASSIVE)
                check(t.slot() == null, t.display() + " (passive) claims no binding");

        // keyedFor must agree with the declared slot.
        for (NikaTechnique t : keyed)
            check(NikaTechnique.keyedFor(t.slot()) == t, "keyedFor(" + t.slot() + ") == " + t.display());

        // Cooldown keys must be unique, or two moves would share a timer.
        Set<String> keys = new HashSet<>();
        boolean uniq = true;
        for (NikaTechnique t : NikaTechnique.values()) uniq &= keys.add(t.cooldownKey());
        check(uniq, "cooldown keys are unique across all techniques");

        // The two mandated moves must be keyed, and the ultimate must cost the most.
        check(NikaTechnique.BAJRANG_GUN.activation() == NikaTechnique.Activation.KEYED, "Bajrang Gun is keyed");
        check(NikaTechnique.DAWN_WHIP.activation() == NikaTechnique.Activation.KEYED, "Dawn Whip is keyed");
        int maxCd = 0;
        for (NikaTechnique t : NikaTechnique.values()) maxCd = Math.max(maxCd, t.cooldownTicks());
        check(NikaTechnique.BAJRANG_GUN.cooldownTicks() == maxCd, "Bajrang Gun has the longest cooldown");

        System.out.println(fails == 0 ? "\nALL CHECKS PASSED" : "\n" + fails + " CHECK(S) FAILED");
        if (fails > 0) System.exit(1);
    }
}
