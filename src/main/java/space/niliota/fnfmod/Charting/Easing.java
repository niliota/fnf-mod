package space.niliota.fnfmod.Charting;

// Easing curves, the same formulas as Flixel's FlxEase (used by Psych, Codename and V-Slice for their tweens).
// t goes from 0 (start of the tween) to 1 (end), the result is how far along the value is (0 to 1, "back" and "elastic" overshoot).
// Names are like FlxEase's: "linear", "quadIn", "sineInOut", "expoOut"... (case doesn't matter). Unknown names act like linear.
public class Easing {
    private static final double BACK = 1.70158;
    private static final double ELASTIC_PERIOD = 0.4;

    public static double apply(String name, double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        if (name == null) return t;

        switch (name.toLowerCase()) {
            case "quadin":     return t * t;
            case "quadout":    return -t * (t - 2);
            case "quadinout":  return t <= .5 ? t * t * 2 : 1 - (t - 1) * (t - 1) * 2;
            case "cubein":     return t * t * t;
            case "cubeout":    return 1 + (t - 1) * (t - 1) * (t - 1);
            case "cubeinout":  return t <= .5 ? t * t * t * 4 : 1 + (t - 1) * (t - 1) * (t - 1) * 4;
            case "quartin":    return t * t * t * t;
            case "quartout":   return 1 - Math.pow(t - 1, 4);
            case "quartinout": return t <= .5 ? t * t * t * t * 8 : (1 - Math.pow(t * 2 - 2, 4)) / 2 + .5;
            case "quintin":    return Math.pow(t, 5);
            case "quintout":   return Math.pow(t - 1, 5) + 1;
            case "quintinout": return t < .5 ? Math.pow(t * 2, 5) / 2 : (Math.pow(t * 2 - 2, 5) + 2) / 2;
            case "sinein":     return -Math.cos(Math.PI / 2 * t) + 1;
            case "sineout":    return Math.sin(Math.PI / 2 * t);
            case "sineinout":  return -Math.cos(Math.PI * t) / 2 + .5;
            case "circin":     return -(Math.sqrt(1 - t * t) - 1);
            case "circout":    return Math.sqrt(1 - (t - 1) * (t - 1));
            case "circinout":  return t <= .5 ? (Math.sqrt(1 - t * t * 4) - 1) / -2 : (Math.sqrt(1 - (t * 2 - 2) * (t * 2 - 2)) + 1) / 2;
            case "expoin":     return Math.pow(2, 10 * (t - 1));
            case "expoout":    return -Math.pow(2, -10 * t) + 1;
            case "expoinout":  return t < .5 ? Math.pow(2, 10 * (t * 2 - 1)) / 2 : (-Math.pow(2, -10 * (t * 2 - 1)) + 2) / 2;
            case "backin":     return t * t * ((BACK + 1) * t - BACK);
            case "backout":    return 1 - (t - 1) * (t - 1) * (-(BACK + 1) * (t - 1) - BACK);
            case "backinout": {
                double s = BACK * 1.525;
                t *= 2;
                if (t < 1) return t * t * ((s + 1) * t - s) / 2;
                t -= 2;
                return (t * t * ((s + 1) * t + s) + 2) / 2;
            }
            case "elasticin":  return -(Math.pow(2, 10 * (t - 1)) * Math.sin(((t - 1) - ELASTIC_PERIOD / 4) * (2 * Math.PI) / ELASTIC_PERIOD));
            case "elasticout": return Math.pow(2, -10 * t) * Math.sin((t - ELASTIC_PERIOD / 4) * (2 * Math.PI) / ELASTIC_PERIOD) + 1;
            case "elasticinout":
                return t < .5
                        ? -0.5 * (Math.pow(2, 10 * (t * 2 - 1)) * Math.sin(((t * 2 - 1) - ELASTIC_PERIOD / 4) * (2 * Math.PI) / ELASTIC_PERIOD))
                        : Math.pow(2, -10 * (t * 2 - 1)) * Math.sin(((t * 2 - 1) - ELASTIC_PERIOD / 4) * (2 * Math.PI) / ELASTIC_PERIOD) * 0.5 + 1;
            case "bouncein":   return 1 - bounceOut(1 - t);
            case "bounceout":  return bounceOut(t);
            case "bounceinout": return t < .5 ? (1 - bounceOut(1 - t * 2)) / 2 : (1 + bounceOut(t * 2 - 1)) / 2;
            case "smoothstepin":    return 2 * smoothStep(t / 2);
            case "smoothstepout":   return 2 * smoothStep(t / 2 + 0.5) - 1;
            case "smoothstepinout": return smoothStep(t);
            default:           return t; // "linear" and anything unknown
        }
    }

    private static double bounceOut(double t) {
        if (t < 1 / 2.75) return 7.5625 * t * t;
        if (t < 2 / 2.75) { t -= 1.5 / 2.75; return 7.5625 * t * t + .75; }
        if (t < 2.5 / 2.75) { t -= 2.25 / 2.75; return 7.5625 * t * t + .9375; }
        t -= 2.625 / 2.75;
        return 7.5625 * t * t + .984375;
    }

    private static double smoothStep(double t) {
        return t * t * (3 - 2 * t);
    }
}
