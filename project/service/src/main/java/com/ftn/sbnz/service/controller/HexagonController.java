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

import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.service.service.HexagonService;

@RestController
@RequestMapping("/api/hexagons")
public class HexagonController {

    private final HexagonService service;

    public HexagonController(HexagonService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Hexagon> create(@RequestBody Hexagon hexagon) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(hexagon));
    }

    @GetMapping
    public List<Hexagon> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Hexagon> getById(@PathVariable int id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Hexagon> updateById(@PathVariable int id, @RequestBody Hexagon hexagon) {
        return service.updateById(id, hexagon)
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
