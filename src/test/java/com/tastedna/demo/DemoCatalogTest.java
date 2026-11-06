package com.tastedna.demo;

import com.tastedna.session.ImageDefinitionTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoCatalogTest {
    static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    static List<DemoProduct> catalog() throws Exception {
        String additions = Files.readString(MIGRATIONS.resolve("V21__expand_demo_catalog_to_30.sql"));
        String sql = Files.readString(MIGRATIONS.resolve("V15__create_demo_store_catalog.sql")) + additions;
        Map<String, Set<String>> styles = new HashMap<>();
        var tags = Pattern.compile("INSERT INTO td_demo_product_style .*?VALUES \\('([^']+)','([^']+)'\\);").matcher(additions);
        while (tags.find()) assertTrue(styles.computeIfAbsent(tags.group(1), ignored -> new TreeSet<>()).add(tags.group(2)));
        var rows = Pattern.compile("INSERT INTO td_demo_product \\(.*?\\) VALUES \\('([^']+)','([^']+)','([^']+)',(\\d+),'([^']+)','([^']+)',(\\d+)\\);").matcher(sql);
        var result = new ArrayList<DemoProduct>();
        var ids = new HashSet<String>();
        var orders = new HashSet<Integer>();
        while (rows.find()) {
            assertTrue(ids.add(rows.group(1)), "Duplicate product ID");
            assertTrue(orders.add(Integer.parseInt(rows.group(7))), "Duplicate display order");
            var p = mock(DemoProduct.class);
            when(p.getProductId()).thenReturn(rows.group(1));
            when(p.getName()).thenReturn(rows.group(2));
            when(p.getCategory()).thenReturn(rows.group(3));
            when(p.getPrice()).thenReturn(Integer.parseInt(rows.group(4)));
            when(p.getImageUrl()).thenReturn(rows.group(5));
            when(p.getDescription()).thenReturn(rows.group(6));
            when(p.getStyleCodes()).thenReturn(styles.getOrDefault(rows.group(1), Set.of()));
            result.add(p);
        }
        assertTrue(ids.containsAll(styles.keySet()));
        return result;
    }

    @Test void catalogHasThirtyUniqueProductsWithExistingImagesAndApprovedCodes() throws Exception {
        var products = catalog();
        assertEquals(30, products.size());
        var allowed = new HashSet<String>();
        ImageDefinitionTest.questions().get("Q02").path("options").forEach(o -> allowed.add(o.path("id").asString()));
        for (var p : products) {
            assertTrue(p.getImageUrl().startsWith("/assets/gender/"));
            assertTrue(Files.isRegularFile(Path.of(p.getImageUrl().substring("/assets/".length()))), p.getProductId());
            assertTrue(allowed.containsAll(p.getStyleCodes()));
            assertTrue(p.getPrice() > 0);
            assertTrue(p.getDescription().contains("시연"));
        }
        assertEquals(5, products.stream().filter(p -> p.getStyleCodes().isEmpty()).count());
        String sql = Files.readString(MIGRATIONS.resolve("V21__expand_demo_catalog_to_30.sql"));
        assertFalse(sql.contains("UPDATE "));
        assertFalse(sql.contains("DELETE "));
    }

    @Test void listControllerReturnsAllThirtyDemoCardsInRepositoryOrder() throws Exception {
        var repository = mock(DemoProductRepository.class);
        var products = catalog();
        when(repository.findAllByOrderByDisplayOrderAsc()).thenReturn(products);
        var cards = new DemoProductController(repository).list();
        assertEquals(30, cards.size());
        for (int i = 0; i < cards.size(); i++) {
            assertEquals(products.get(i).getProductId(), cards.get(i).id());
            assertEquals(products.get(i).getImageUrl(), cards.get(i).imageUrl());
            assertEquals(products.get(i).getName(), cards.get(i).name());
            assertTrue(cards.get(i).demo());
        }
    }
}
