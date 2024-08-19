package fr.recia.mce.api.escomceapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "fr.recia.mce.api.escomceapi.*")
@EnableConfigurationProperties
public class MceProjectApplication {

	public static void main(String[] args) {
		SpringApplication.run(MceProjectApplication.class, args);
	}

}
