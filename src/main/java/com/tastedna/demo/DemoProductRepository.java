package com.tastedna.demo;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

// 데모 카탈로그만 조회하며 TasteDNA 분석 서비스와 결합하지 않는다.
public interface DemoProductRepository extends JpaRepository<DemoProduct, String> {
    List<DemoProduct> findAllByOrderByDisplayOrderAsc();
    @Query("select distinct p from DemoProduct p join p.styleCodes s where s in :codes order by p.productId")
    List<DemoProduct> findMatchingStyles(@Param("codes") Collection<String> codes);
}

