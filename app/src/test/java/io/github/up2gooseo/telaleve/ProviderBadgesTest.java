package io.github.up2gooseo.telaleve;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProviderBadgesTest {
    @Test public void nomesDeAssinaturaSaoNormalizados() {
        assertEquals("Netflix", CatalogClient.canonicalProvider("Netflix Standard with Ads"));
        assertEquals("Prime Video", CatalogClient.canonicalProvider("Amazon Prime Video"));
        assertEquals("Disney+", CatalogClient.canonicalProvider("Disney Plus"));
        assertEquals("HBO Max", CatalogClient.canonicalProvider("Max"));
        assertEquals("Apple TV+", CatalogClient.canonicalProvider("Apple TV+"));
        assertEquals("Crunchyroll", CatalogClient.canonicalProvider("Crunchyroll"));
    }

    @Test public void servicoDesconhecidoPreservaNomeOriginal() {
        assertEquals("Telecine", CatalogClient.canonicalProvider("Telecine"));
        assertEquals("", CatalogClient.canonicalProvider(""));
        assertEquals("", CatalogClient.canonicalProvider(null));
    }
}
