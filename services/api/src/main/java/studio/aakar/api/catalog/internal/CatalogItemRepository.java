package studio.aakar.api.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CatalogItemRepository extends JpaRepository<CatalogItemEntity, String> {

    @Query("""
            select i from CatalogItemEntity i
            where (:category is null or i.category = :category)
              and (:q is null or lower(i.name) like lower(concat('%', :q, '%'))
                   or lower(coalesce(i.description, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(i.specsLine, '')) like lower(concat('%', :q, '%')))
            order by i.available desc, i.basePricePaise asc, i.name asc
            """)
    List<CatalogItemEntity> search(@Param("category") String category, @Param("q") String q);
}
