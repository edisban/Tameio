package com.tameio;

import org.springframework.boot.SpringApplication;

public class TestTameioCoreApplication {

	public static void main(String[] args) {
		SpringApplication.from(TameioCoreApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
