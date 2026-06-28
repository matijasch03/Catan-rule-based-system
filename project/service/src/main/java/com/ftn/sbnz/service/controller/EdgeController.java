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

import com.ftn.sbnz.model.Edge;
import com.ftn.sbnz.service.service.EdgeService;

@RestController
@RequestMapping("/api/edges")
public class EdgeController {

    private final EdgeService service;

    public EdgeController(EdgeService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Edge> create(@RequestBody Edge edge) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(edge));
    }

    @GetMapping
    public List<Edge> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Edge> getById(@PathVariable int id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Edge> updateById(@PathVariable int id, @RequestBody Edge edge) {
        return service.updateById(id, edge)
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
