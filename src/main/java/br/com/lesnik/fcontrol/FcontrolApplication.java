package br.com.lesnik.fcontrol;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FcontrolApplication {

	public static void main(String[] args) {
		// Encaminhando a execução prioritária para a Window do Desktop
		Application.launch(FControlDesktopApp.class, args);
	}
}
