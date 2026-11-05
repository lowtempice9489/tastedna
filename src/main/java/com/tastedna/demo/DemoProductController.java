package com.tastedna.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import java.util.List;

// 로컬 전시용 API. 인증·주문·결제는 도입 쇼핑몰의 책임으로 남긴다.
@RestController
@Profile("local | member-auth")
@RequestMapping("/api/v1/demo/products")
public class DemoProductController {
    private final DemoProductRepository repository;
    public DemoProductController(DemoProductRepository repository) { this.repository = repository; }
    public record Product(String id, String name, String category, int price, String imageUrl, String description, boolean demo) {}
    @GetMapping
    public List<Product> list() {
        return repository.findAllByOrderByDisplayOrderAsc().stream()
                .map(p -> new Product(p.getProductId(), p.getName(), p.getCategory(), p.getPrice(), p.getImageUrl(), p.getDescription(), true)).toList();
    }
}

