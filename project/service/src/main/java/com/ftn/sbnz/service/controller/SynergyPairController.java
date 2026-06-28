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

import com.ftn.sbnz.model.SynergyPair;
import com.ftn.sbnz.service.service.SynergyPairService;

@RestController
@RequestMapping("/api/synergy-pairs")
public class SynergyPairController {

    private final SynergyPairService service;

    public SynergyPairController(SynergyPairService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SynergyPair> create(@RequestBody SynergyPair synergyPair) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(synergyPair));
    }

    @GetMapping
    public List<SynergyPair> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SynergyPair> getById(@PathVariable int id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<SynergyPair> updateById(@PathVariable int id, @RequestBody SynergyPair synergyPair) {
        return service.updateById(id, synergyPair)
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
