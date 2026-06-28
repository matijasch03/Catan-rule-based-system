package com.ftn.sbnz.service.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ftn.sbnz.model.Advice;
import com.ftn.sbnz.service.repository.AdviceRepository;

@Service
public class AdviceService {

    private final AdviceRepository repository;

    public AdviceService(AdviceRepository repository) {
        this.repository = repository;
    }

    public Advice create(Advice advice) {
        return repository.save(advice);
    }

    public List<Advice> getAll() {
        return repository.findAll();
    }

    public Optional<Advice> getById(int id) {
        return repository.findById(id);
    }

    public Optional<Advice> updateById(int id, Advice data) {
        return repository.findById(id).map(existing -> {
            existing.setDescription(data.getDescription());
            existing.setSuccess(data.getSuccess());
            existing.setPlayer(data.getPlayer());
            existing.setLongestRoad(data.getLongestRoad());
            existing.setTargetNode(data.getTargetNode());
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
