package br.com.lesnik.fcontrol;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExampleController {

    @GetMapping("/ok")
    public ResponseEntity<String> sayOk() {
        return ResponseEntity.ok("Tudo certo!");
    }

    @PostMapping("/echo")
    public ResponseEntity<String> echo(@RequestBody String value){
        return ResponseEntity.ok(value);
    }
}
