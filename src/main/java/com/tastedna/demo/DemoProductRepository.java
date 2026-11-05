package com.tastedna.demo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

// 데모 카탈로그만 조회하며 TasteDNA 분석 서비스와 결합하지 않는다.
public interface DemoProductRepository extends JpaRepository<DemoProduct, String> {
    List<DemoProduct> findAllByOrderByDisplayOrderAsc();
    List<DemoProduct> findByImageUrlInOrderByProductIdAsc(java.util.Collection<String> imageUrls);
}

