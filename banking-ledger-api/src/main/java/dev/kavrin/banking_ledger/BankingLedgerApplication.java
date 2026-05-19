package dev.kavrin.banking_ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BankingLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankingLedgerApplication.class, args);
	}

}
