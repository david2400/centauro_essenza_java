package com.essenza.draco.modules.shipping_logistics.dispatch.infrastructure.outbound.persistence.mysql.shop;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.List;

@SuperBuilder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "dispatch_products")
public class DispatchProductEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_dispatch_product")
    private Long id;

    @Column(name = "guide_number", nullable = false)
    private String guideNumber;

    @Column(nullable = false)
    private String address;

    @Column(name = "estimated_delivery_date", nullable = false)
    private LocalDate estimatedDeliveryDate;

    @Column(name = "real_delivery_date")
    private LocalDate realDeliveryDate;

    @Column(name = "deparment_origin", nullable = false)
    private String departmentOrigin;

    @Column(name = "city_origin", nullable = false)
    private String cityOrigin;

    @Column(name = "deparment_destination", nullable = false)
    private String departmentDestination;

    @Column(name = "city_destination", nullable = false)
    private String cityDestination;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    // Sin asociación JPA hacia la orden (módulo sales): se referencia solo por id (Fase 1, fronteras entre módulos).

    @OneToMany(mappedBy = "dispatchProduct", fetch = FetchType.LAZY)
    private List<DispatchDetailEntity> dispatchDetail;

    @OneToMany(mappedBy = "dispatchProduct", fetch = FetchType.LAZY)
    private List<TrackingEntity> tracking;


}

