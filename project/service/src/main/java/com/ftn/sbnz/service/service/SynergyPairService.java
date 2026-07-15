package com.ftn.sbnz.service.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ftn.sbnz.model.SynergyPair;
import com.ftn.sbnz.service.repository.SynergyPairRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class SynergyPairService {

    private final SynergyPairRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public SynergyPairService(SynergyPairRepository repository) {
        this.repository = repository;
    }

    public SynergyPair create(SynergyPair synergyPair) {
        return repository.save(synergyPair);
    }

    public List<SynergyPair> getAll() {
        return repository.findAll();
    }

    public SynergyPair save(SynergyPair synergyPair) {
        return repository.save(synergyPair);
    }

    public Optional<SynergyPair> getById(int id) {
        return repository.findById(id);
    }

    public Optional<SynergyPair> updateById(int id, SynergyPair data) {
        return repository.findById(id).map(existing -> {
            existing.setNode1(data.getNode1());
            existing.setNode2(data.getNode2());
            existing.setDistance(data.getDistance());
            existing.setScore(data.getScore());
            existing.setCheckPoints(data.getCheckPoints());
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

    public void deleteAll() {
        repository.deleteAll();
        repository.flush();
        entityManager.clear();
    }
}
