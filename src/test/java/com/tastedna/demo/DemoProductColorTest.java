package com.tastedna.demo;

import com.tastedna.session.ImageDefinitionTest;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class DemoProductColorTest {
    @Test void colorsUseOnlyExplicitCatalogNamesAndApprovedQ01Terms() throws Exception {
        var catalog = DemoCatalogTest.scopedCatalog();
        assertEquals(100, catalog.size());
        Map<String, String> vocabulary = new HashMap<>();
        for (var option : ImageDefinitionTest.questions().get("Q01").path("options"))
            for (String term : option.path("label").asString().split("·"))
                vocabulary.put(term, option.path("id").asString());
        Map<String, String> expected = new HashMap<>();
        for (var product : catalog) {
            String code = vocabulary.get(product.getName().split(" ")[0]);
            if (code != null) expected.put(product.getProductId(), code);
        }
        String sql = Files.readString(DemoCatalogTest.MIGRATIONS.resolve("V25__register_demo_product_colors.sql"));
        var inserts = Pattern.compile("INSERT INTO td_demo_product_color \\(product_id,color_code\\) VALUES \\('([^']+)','([^']+)'\\);").matcher(sql);
        Map<String, String> actual = new HashMap<>();
        while (inserts.find()) assertNull(actual.put(inserts.group(1), inserts.group(2)));
        assertEquals(expected, actual);
        assertEquals(70, actual.size());
        assertEquals(30, catalog.size() - actual.size());
        assertFalse(actual.containsKey("look-25")); // 블루그레이를 블루 또는 그레이로 추측하지 않는다.
        assertEquals("B", actual.get("look-07"));
        assertEquals("D", actual.get("look-73"));
        String executable = sql.replaceAll("(?m)^--.*$", "");
        assertFalse(Pattern.compile("(?i)\\b(UPDATE|DELETE|DROP|ALTER)\\b").matcher(executable).find());
        assertTrue(executable.contains("REFERENCES td_demo_product(product_id)"));
        assertTrue(executable.contains("PRIMARY KEY (product_id, color_code)"));
        var allowed = new HashSet<String>();
        var codes = Pattern.compile("'([A-O])'").matcher(executable.substring(0, executable.indexOf("INSERT")));
        while (codes.find()) allowed.add(codes.group(1));
        assertEquals(new HashSet<>(vocabulary.values()), allowed);
    }

    @Test void jpaCollectionMatchesMigrationAndUnregisteredProductIsEmpty() throws Exception {
        var field = DemoProduct.class.getDeclaredField("colorCodes");
        assertNotNull(field.getAnnotation(ElementCollection.class));
        assertEquals("td_demo_product_color", field.getAnnotation(CollectionTable.class).name());
        assertEquals("product_id", field.getAnnotation(CollectionTable.class).joinColumns()[0].name());
        assertEquals("color_code", field.getAnnotation(Column.class).name());
        assertEquals(1, field.getAnnotation(Column.class).length());
        assertTrue(new DemoProduct().getColorCodes().isEmpty());
    }
}
