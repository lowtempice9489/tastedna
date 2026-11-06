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
        return catalog(false);
    }

    static List<DemoProduct> catalog(boolean expanded) throws Exception {
        String additions = Files.readString(MIGRATIONS.resolve("V21__expand_demo_catalog_to_30.sql"));
        if (expanded) additions += Files.readString(MIGRATIONS.resolve("V22__expand_demo_catalog_to_100.sql"));
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

    // V24의 명시적 상품 ID 매핑을 기존 카탈로그 대역에 적용한다.
    static List<DemoProduct> scopedCatalog() throws Exception {
        var catalog = catalog(true);
        String sql = Files.readString(MIGRATIONS.resolve("V24__register_demo_product_apparel_scopes.sql"));
        var updates = Pattern.compile("UPDATE td_demo_product SET apparel_scope = '(MENS|WOMENS|UNISEX)' WHERE product_id = '([^']+)' AND apparel_scope IS NULL;").matcher(sql);
        var ids = new HashSet<String>();
        while (updates.find()) {
            assertTrue(ids.add(updates.group(2)));
            String id = updates.group(2);
            var product = catalog.stream().filter(p -> p.getProductId().equals(id)).findFirst().orElseThrow();
            when(product.getApparelScope()).thenReturn(DemoProduct.ApparelScope.valueOf(updates.group(1)));
        }
        assertEquals(100, ids.size());
        return catalog;
    }

    @Test void scopeRegistrationPreservesCatalogAndHasExactDefinitionProvenance() throws Exception {
        var before = catalog(true);
        var after = scopedCatalog();
        assertEquals(100, after.size());
        assertEquals(29, after.stream().filter(p -> p.getApparelScope() == DemoProduct.ApparelScope.MENS).count());
        assertEquals(71, after.stream().filter(p -> p.getApparelScope() == DemoProduct.ApparelScope.WOMENS).count());
        String mens = Files.readString(MIGRATIONS.resolve("V13__add_mens_image_simple_definition.sql"));
        String womens = Files.readString(MIGRATIONS.resolve("V18__add_womens_simple_q05_q07.sql"))
                + Files.readString(MIGRATIONS.resolve("V19__add_womens_simple_q08_q10.sql"));
        for (int i = 0; i < before.size(); i++) {
            var a = before.get(i); var b = after.get(i);
            assertEquals(a.getProductId(), b.getProductId());
            assertEquals(a.getName(), b.getName());
            assertEquals(a.getCategory(), b.getCategory());
            assertEquals(a.getPrice(), b.getPrice());
            assertEquals(a.getImageUrl(), b.getImageUrl());
            assertEquals(a.getStyleCodes(), b.getStyleCodes());
            assertTrue(Files.isRegularFile(Path.of(b.getImageUrl().substring(8))));
            assertTrue((b.getApparelScope() == DemoProduct.ApparelScope.MENS ? mens : womens).contains(b.getImageUrl()));
            assertFalse((b.getApparelScope() == DemoProduct.ApparelScope.MENS ? womens : mens).contains(b.getImageUrl()));
        }
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

    @Test void expandedCatalogPreservesThirtyProductsAndListsOneHundred() throws Exception {
        var original = catalog();
        var expanded = catalog(true);
        assertEquals(100, expanded.size());
        var allowed = new HashSet<String>();
        ImageDefinitionTest.questions().get("Q02").path("options").forEach(o -> allowed.add(o.path("id").asString()));
        for (var p : expanded) {
            assertTrue(Files.isRegularFile(Path.of(p.getImageUrl().substring("/assets/".length()))), p.getImageUrl());
            assertTrue(allowed.containsAll(p.getStyleCodes()));
            assertTrue(p.getPrice() > 0);
            assertTrue(p.getDescription().contains("시연"));
        }
        for (int i = 0; i < 30; i++) {
            assertEquals(original.get(i).getProductId(), expanded.get(i).getProductId());
            assertEquals(original.get(i).getImageUrl(), expanded.get(i).getImageUrl());
            assertEquals(original.get(i).getStyleCodes(), expanded.get(i).getStyleCodes());
        }
        var repository = mock(DemoProductRepository.class);
        when(repository.findAllByOrderByDisplayOrderAsc()).thenReturn(expanded);
        var cards = new DemoProductController(repository).list();
        assertEquals(100, cards.size());
        assertTrue(cards.stream().allMatch(DemoProductController.Product::demo));
        assertEquals(expanded.stream().map(DemoProduct::getProductId).toList(),
                cards.stream().map(DemoProductController.Product::id).toList());
        String sql = Files.readString(MIGRATIONS.resolve("V22__expand_demo_catalog_to_100.sql"));
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
