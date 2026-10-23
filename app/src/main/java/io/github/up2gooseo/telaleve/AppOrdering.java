package io.github.up2gooseo.telaleve;

import java.util.Locale;

/** Posições padrão dos aplicativos preferidos; -1 identifica os demais. */
public final class AppOrdering {
    private AppOrdering() {}

    public static int priority(String packageName, String title) {
        String pkg = packageName == null ? "" : packageName.toLowerCase(Locale.ROOT);
        String label = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);

        if (pkg.equals("com.google.android.youtube.tv") || label.equals("youtube")) return 0;
        if (pkg.equals("com.netflix.ninja") || pkg.equals("com.netflix.mediaclient")
                || label.equals("netflix")) return 1;
        if (pkg.equals("com.amazon.amazonvideo.livingroom")
                || pkg.equals("com.amazon.avod.thirdpartyclient")
                || label.equals("prime video") || label.equals("amazon prime video")) return 2;
        if (pkg.equals("com.disney.disneyplus")
                || label.equals("disney+") || label.equals("disney plus")) return 3;
        if (pkg.equals("com.apple.atve.androidtv.appletv")
                || label.equals("apple tv") || label.equals("apple tv+")) return 4;
        if (pkg.equals("com.wbd.stream") || pkg.equals("com.hbo.hbonow")
                || pkg.equals("com.hbo.hbomax") || label.equals("hbo max")
                || label.equals("max") || label.equals("hbo")) return 5;
        if (pkg.equals("com.crunchyroll.crunchyroid") || label.equals("crunchyroll")) return 6;
        return -1;
    }
}
