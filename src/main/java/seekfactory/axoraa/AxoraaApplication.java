package seekfactory.axoraa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class AxoraaApplication {

	public static void main(String[] args) {
		SpringApplication.run(AxoraaApplication.class, args);
	}

}
