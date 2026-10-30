import dev.riptide.knifelab.core.InspectAnimation;
import dev.riptide.knifelab.core.KnifeSettings;
import dev.riptide.knifelab.core.KnifeType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class CoreTests {
    private static int passed = 0;

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("FAILED: " + name);
        }
        passed++;
        System.out.println("ok - " + name);
    }

    public static void main(String[] args) throws Exception {
        // Knife types
        check(KnifeType.byId("karambit").orElseThrow() == KnifeType.KARAMBIT, "byId finds karambit");
        check(KnifeType.byId("nope").isEmpty(), "byId rejects unknown id");
        check(KnifeType.BUTTERFLY.modelKey().equals("riptide_knife_lab:butterfly"), "model key format");

        // Defaults and swap policy
        KnifeSettings d = new KnifeSettings();
        check(d.getSelectedKnife() == KnifeType.BUTTERFLY, "default knife is butterfly");
        check(d.replacesItem("minecraft:iron_sword"), "default replaces iron sword");
        check(!d.replacesItem("minecraft:stick"), "non-sword items are never replaced");

        d.setReplacementEnabled(false);
        check(!d.replacesItem("minecraft:iron_sword"), "master switch off disables replacement");
        d.setReplacementEnabled(true);

        d.setSwordEnabled("minecraft:iron_sword", false);
        check(!d.replacesItem("minecraft:iron_sword"), "per-sword toggle off");
        check(d.replacesItem("minecraft:diamond_sword"), "other swords still replaced");

        boolean rejected = false;
        try {
            d.setSwordEnabled("minecraft:stick", true);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "rejects non-sword ids");

        // Persistence
        Path dir = Files.createTempDirectory("knifelab");
        Path file = dir.resolve("riptide-knife-lab.properties");
        KnifeSettings s = new KnifeSettings();
        s.setSelectedKnife(KnifeType.M9_BAYONET);
        s.setInspectAnimationEnabled(false);
        s.setSwordEnabled("minecraft:iron_sword", false);
        s.save(file);

        KnifeSettings r = KnifeSettings.load(file);
        check(r.getSelectedKnife() == KnifeType.M9_BAYONET, "round trip keeps knife");
        check(!r.isInspectAnimationEnabled(), "round trip keeps inspect flag");
        check(r.getReplaceableSwords().size() == 5 && !r.getReplaceableSwords().contains("minecraft:iron_sword"),
                "round trip keeps sword list");
        check(KnifeSettings.load(dir.resolve("missing.properties")).getSelectedKnife() == KnifeType.BUTTERFLY,
                "missing file gives defaults");

        Files.writeString(file, "selectedKnife=bogus\nreplaceableSwords=minecraft:stick,minecraft:diamond_sword\n");
        KnifeSettings bad = KnifeSettings.load(file);
        check(bad.getSelectedKnife() == KnifeType.BUTTERFLY, "unknown knife falls back to default");
        check(bad.getReplaceableSwords().equals(Set.of("minecraft:diamond_sword")), "invalid sword ids are dropped");

        // Inspect gate
        check(!InspectAnimation.canStart(false, true, false, false, false), "no inspect without a knife");
        check(!InspectAnimation.canStart(true, false, false, false, false), "no inspect when disabled");
        check(!InspectAnimation.canStart(true, true, true, false, false), "no inspect while a screen is open");
        check(!InspectAnimation.canStart(true, true, false, true, false), "no inspect while busy");
        check(!InspectAnimation.canStart(true, true, false, false, true), "no restart while running");
        check(InspectAnimation.canStart(true, true, false, false, false), "inspect allowed when ready");

        // Inspect lifecycle
        InspectAnimation a = new InspectAnimation();
        check(!a.isActive() && a.swingAngle(0.5f) == 0f, "idle animation has no swing");
        a.start();
        check(a.isActive(), "start activates");
        for (int i = 0; i < InspectAnimation.DURATION_TICKS - 1; i++) {
            a.tick();
        }
        check(a.isActive(), "still active one tick before the end");
        a.tick();
        check(!a.isActive(), "ends after the full duration");

        a.start();
        for (int i = 0; i < InspectAnimation.DURATION_TICKS / 2; i++) {
            a.tick();
        }
        check(Math.abs(a.swingAngle(0f) - InspectAnimation.MAX_SWING_RADIANS) < 1e-5f, "swing peaks at midpoint");
        check(a.progress(100f) == 1f && a.progress(0f) <= 1f, "progress is clamped");
        a.cancel();
        check(!a.isActive(), "cancel stops the animation");

        System.out.println(passed + " core checks passed");
    }
}
