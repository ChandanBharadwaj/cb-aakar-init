package studio.aakar.api.order.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A cart line frozen at checkout, including the version's assets so the studio can print from the order alone. */
@Entity
@Table(name = "order_items")
class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(name = "design_id", nullable = false)
    private UUID designId;
    @Column(name = "version_id", nullable = false)
    private UUID versionId;
    @Column(name = "version_no")
    private Integer versionNo;
    @Column(nullable = false)
    private String title;
    @Column(name = "specs_line")
    private String specsLine;
    @Column(name = "material_id", nullable = false)
    private String materialId;
    @Column(name = "material_name")
    private String materialName;
    @Column(nullable = false)
    private int qty;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unit_price", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> unitPrice;
    @Column(name = "line_total_paise", nullable = false)
    private long lineTotalPaise;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> assets;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected OrderItemEntity() {
    }

    OrderItemEntity(UUID orderId, UUID designId, UUID versionId, Integer versionNo, String title, String specsLine, String materialId,
            String materialName, int qty, Map<String, Object> unitPrice, long lineTotalPaise, Map<String, Object> assets, int sortOrder) {
        this.orderId = orderId;
        this.designId = designId;
        this.versionId = versionId;
        this.versionNo = versionNo;
        this.title = title;
        this.specsLine = specsLine;
        this.materialId = materialId;
        this.materialName = materialName;
        this.qty = qty;
        this.unitPrice = unitPrice;
        this.lineTotalPaise = lineTotalPaise;
        this.assets = assets;
        this.sortOrder = sortOrder;
    }

    UUID id() {
        return id;
    }

    UUID designId() {
        return designId;
    }

    UUID versionId() {
        return versionId;
    }

    Integer versionNo() {
        return versionNo;
    }

    String title() {
        return title;
    }

    String specsLine() {
        return specsLine;
    }

    String materialId() {
        return materialId;
    }

    String materialName() {
        return materialName;
    }

    int qty() {
        return qty;
    }

    Map<String, Object> unitPrice() {
        return unitPrice;
    }

    long lineTotalPaise() {
        return lineTotalPaise;
    }

    Map<String, Object> assets() {
        return assets;
    }
}
