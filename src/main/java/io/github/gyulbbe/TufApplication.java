package io.github.gyulbbe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(exclude = {
        SecurityAutoConfiguration.class,
        org.springframework.ai.vectorstore.oracle.autoconfigure.OracleVectorStoreAutoConfiguration.class
})
public class TufApplication {

    public static void main(String[] args) {
        SpringApplication.run(TufApplication.class, args);
    }

}
