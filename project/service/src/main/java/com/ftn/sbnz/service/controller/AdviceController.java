package com.ftn.sbnz.service.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ftn.sbnz.model.Advice;
import com.ftn.sbnz.service.service.AdviceService;

@RestController
@RequestMapping("/api/advices")
public class AdviceController {

    private final AdviceService service;

    public AdviceController(AdviceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Advice> create(@RequestBody Advice advice) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(advice));
    }

    @GetMapping
    public List<Advice> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Advice> getById(@PathVariable int id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Advice> updateById(@PathVariable int id, @RequestBody Advice advice) {
        return service.updateById(id, advice)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable int id) {
        return service.deleteById(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
