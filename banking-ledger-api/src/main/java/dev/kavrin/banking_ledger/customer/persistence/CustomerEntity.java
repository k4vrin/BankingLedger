package dev.kavrin.banking_ledger.customer.persistence;

import dev.kavrin.banking_ledger.customer.domain.model.CustomerStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "customers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_customers_external_ref",
                        columnNames = "external_customer_reference"
                ),
                @UniqueConstraint(
                        name = "uk_customers_email",
                        columnNames = "email"
                )
        }
)
@Setter
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CustomerEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(
            name = "external_customer_reference",
            nullable = false,
            length = 100
    )
    private String externalCustomerReference;

    @Column(
            name = "full_name",
            nullable = false
    )
    private String fullName;

    @Column(name = "email", length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CustomerStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
