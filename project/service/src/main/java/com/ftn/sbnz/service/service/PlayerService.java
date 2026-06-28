package com.ftn.sbnz.service.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ftn.sbnz.model.Player;
import com.ftn.sbnz.service.repository.PlayerRepository;

@Service
public class PlayerService {

    private final PlayerRepository repository;

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }

    public Player create(Player player) {
        return repository.save(player);
    }

    public List<Player> getAll() {
        return repository.findAll();
    }

    public Optional<Player> getById(int id) {
        return repository.findById(id);
    }

    public Optional<Player> updateById(int id, Player data) {
        return repository.findById(id).map(existing -> {
            existing.setScore(data.getScore());
            existing.setResources(data.getResources());
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
