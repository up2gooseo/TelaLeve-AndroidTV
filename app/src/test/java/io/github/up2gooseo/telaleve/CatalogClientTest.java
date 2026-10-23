package io.github.up2gooseo.telaleve;

import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class CatalogClientTest {
    @Test public void distingueAssinaturaAppleTvDeAluguel() {
        Map<String, Integer> ids = new LinkedHashMap<>();
        CatalogClient.putProvider(ids, "apple tv", 10);
        CatalogClient.putProvider(ids, "apple tv+", 20);
        CatalogClient.putProvider(ids, "amazon prime video", 30);
        CatalogClient.putProvider(ids, "netflix", 40);
        CatalogClient.putProvider(ids, "hbo max", 50);
        assertEquals(Integer.valueOf(20), ids.get("APPLE"));
        assertEquals(Integer.valueOf(30), ids.get("PRIME"));
        assertFalse(ids.containsValue(10));
        assertEquals("40|30|20|50", CatalogClient.buildProviderFilter("TODOS", ids));
    }

    @Test public void filtraSomenteProvedorSelecionado() {
        Map<String, Integer> ids = new LinkedHashMap<>();
        ids.put("NETFLIX", 8);
        ids.put("PRIME", 9);
        assertEquals("9", CatalogClient.buildProviderFilter("PRIME", ids));
        assertEquals("", CatalogClient.buildProviderFilter("CRUNCHYROLL", ids));
    }
}
