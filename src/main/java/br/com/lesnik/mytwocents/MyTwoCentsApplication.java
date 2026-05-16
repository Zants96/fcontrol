package br.com.lesnik.mytwocents;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyTwoCentsApplication {

	public static void main(String[] args) {
		// Encaminhando a execução prioritária para a Window do Desktop
		Application.launch(MyTwoCentsDesktopApp.class, args);
	}
}
