package io.github.up2gooseo.telaleve;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AppOrderingTest {
    @Test public void streamingsTemOrdemDefinidaPeloPacote() {
        String[] packages = {
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.apple.atve.androidtv.appletv",
            "com.wbd.stream",
            "com.crunchyroll.crunchyroid"
        };
        for (int i = 0; i < packages.length; i++) {
            assertEquals(i, AppOrdering.priority(packages[i], "Nome localizado"));
        }
    }

    @Test public void alternativasDoPrimeVideoEDaNetflixSaoReconhecidas() {
        assertEquals(2, AppOrdering.priority("com.amazon.avod.thirdpartyclient", "Prime"));
        assertEquals(1, AppOrdering.priority("com.netflix.mediaclient", "Netflix"));
    }

    @Test public void nomesSaoFallbackQuandoPacoteEVariacaoDaTv() {
        assertEquals(0, AppOrdering.priority("tv.vendor.youtube", "YouTube"));
        assertEquals(2, AppOrdering.priority("tv.vendor.prime", "Prime Video"));
        assertEquals(5, AppOrdering.priority("tv.vendor.max", "HBO Max"));
        assertEquals(6, AppOrdering.priority("tv.vendor.crunchy", "Crunchyroll"));
    }

    @Test public void outrosAppsNaoRecebemPrioridade() {
        assertEquals(-1, AppOrdering.priority("com.example.settings", "Configurações"));
        assertEquals(-1, AppOrdering.priority("com.google.android.youtube.tvmusic", "YouTube Music"));
    }
}
