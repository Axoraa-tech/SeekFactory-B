package seekfactory.axoraa;

import org.springframework.boot.SpringApplication;

public class TestAxoraaApplication {

	public static void main(String[] args) {
		SpringApplication.from(AxoraaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
