package com.ftn.sbnz.service.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.service.repository.HexagonRepository;

@Service
public class HexagonService {

    private final HexagonRepository repository;

    public HexagonService(HexagonRepository repository) {
        this.repository = repository;
    }

    public Hexagon create(Hexagon hexagon) {
        return repository.save(hexagon);
    }

    public List<Hexagon> getAll() {
        return repository.findAll();
    }

    public Optional<Hexagon> getById(int id) {
        return repository.findById(id);
    }

    public Optional<Hexagon> updateById(int id, Hexagon data) {
        return repository.findById(id).map(existing -> {
            existing.setQ(data.getQ());
            existing.setR(data.getR());
            existing.setField(data.getField());
            existing.setDots(data.getDots());
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
