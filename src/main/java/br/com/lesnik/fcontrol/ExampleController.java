package br.com.lesnik.fcontrol;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExampleController {
    public ResponseEntity<String> sayOk() {
        return ResponseEntity.ok("Tudo certo!");
    }


}
