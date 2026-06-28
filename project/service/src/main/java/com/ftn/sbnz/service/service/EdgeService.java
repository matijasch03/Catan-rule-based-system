package com.ftn.sbnz.service.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ftn.sbnz.model.Edge;
import com.ftn.sbnz.service.repository.EdgeRepository;

@Service
public class EdgeService {

    private final EdgeRepository repository;

    public EdgeService(EdgeRepository repository) {
        this.repository = repository;
    }

    public Edge create(Edge edge) {
        return repository.save(edge);
    }

    public List<Edge> getAll() {
        return repository.findAll();
    }

    public Optional<Edge> getById(int id) {
        return repository.findById(id);
    }

    public Optional<Edge> updateById(int id, Edge data) {
        return repository.findById(id).map(existing -> {
            existing.setNode1(data.getNode1());
            existing.setNode2(data.getNode2());
            existing.setOwner(data.getOwner());
            return repository.save(existing);
        });
    }

    public boolean deleteById(int id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }
}
