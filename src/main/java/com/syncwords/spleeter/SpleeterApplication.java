package com.syncwords.spleeter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

@SpringBootApplication
public class SpleeterApplication{

	@Bean(name="tmpFolder")
	public TmpFolder tmpFolder(@Value("${tmp-folder}") String argumentValue) {
		return new TmpFolder(argumentValue);
	}

	public static void main(String[] args) {
		SpringApplication.run(SpleeterApplication.class, args);
	}

}
