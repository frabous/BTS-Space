package fr.frabous.stellar.client;

import fr.frabous.stellar.api.SupernovaTiming;

/** The light flash is silent. Atmospheric pressure arrives later on the shared, condensed timeline. */
final class SupernovaAudioTiming {
    private SupernovaAudioTiming() { }
    /** Network time corrections must not rewind sounds already heard or recreate their loops. */
    static final class Clock {
        private double age=-1;
        private boolean pressure;

        double advance(double worldAge) {
            double previous=age<0 ? Math.nextDown(worldAge-1) : age;
            age=Math.max(age,worldAge);
            pressure=SupernovaAudioTiming.pressureDue(previous,age);
            return age;
        }

        boolean pressureDue() { return pressure; }
    }

    static boolean pressureDue(double previous, double age) {
        return previous<SupernovaTiming.AFTERMATH && age>=SupernovaTiming.AFTERMATH
                && age-SupernovaTiming.AFTERMATH<=1;
    }
    static double rumble(double age) {
        return 0.32*SupernovaVisuals.smooth((age-SupernovaTiming.AFTERMATH)/7)
                *(1-SupernovaVisuals.smooth((age-565)/75));
    }
    static double wind(double age) {
        return 0.24*SupernovaVisuals.smooth((age-492)/18)
                *(1-SupernovaVisuals.smooth((age-590)/50));
    }
    static double openness(double shelter) { return 1-Math.max(0,Math.min(1,shelter)); }
    static double muffled(double shelter, boolean underwater) {
        return Math.max(0,Math.min(1,shelter))*(underwater?0.18:0.36);
    }
}
