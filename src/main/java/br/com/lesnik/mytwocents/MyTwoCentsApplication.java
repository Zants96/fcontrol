package br.com.lesnik.mytwocents;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class MyTwoCentsApplication {

	public static void main(String[] args) {
		// Encaminhando a execução prioritária para a Window do Desktop
		Application.launch(MyTwoCentsDesktopApp.class, args);
	}
}
