package dev.kavrin.banking_ledger.account.persistence;

import dev.kavrin.banking_ledger.account.domain.model.AccountCategory;
import dev.kavrin.banking_ledger.account.domain.model.AccountStatus;
import dev.kavrin.banking_ledger.account.domain.model.AccountType;
import dev.kavrin.banking_ledger.customer.persistence.CustomerEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_accounts_account_number",
                        columnNames = "account_number"
                ),

                @UniqueConstraint(
                        name = "uk_accounts_id_currency",
                        columnNames = {"id", "currency_code"}
                )
        }

)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AccountEntity {
    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "customer_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_accounts_customer")
    )
    private CustomerEntity customer;

    @Column(name = "account_number", nullable = false, length = 34)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_category", nullable = false, length = 20)
    private AccountCategory accountCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AccountStatus status;

    @Column(
            name = "currency_code",
            nullable = false,
            length = 3
    )
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currencyCode;

    @Column(
            name = "available_balance_minor",
            nullable = false
    )
    private long availableBalanceMinor;

    @Column(
            name = "ledger_balance_minor",
            nullable = false
    )
    private long ledgerBalanceMinor;

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
