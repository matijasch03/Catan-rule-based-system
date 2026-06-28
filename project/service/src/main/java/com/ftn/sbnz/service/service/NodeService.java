package com.ftn.sbnz.service.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.service.repository.NodeRepository;

@Service
public class NodeService {

    private final NodeRepository repository;

    public NodeService(NodeRepository repository) {
        this.repository = repository;
    }

    public Node create(Node node) {
        return repository.save(node);
    }

    public List<Node> getAll() {
        return repository.findAll();
    }

    public Optional<Node> getById(int id) {
        return repository.findById(id);
    }

    public Optional<Node> updateById(int id, Node data) {
        return repository.findById(id).map(existing -> {
            existing.setOrientation(data.getOrientation());
            existing.setSettlement(data.getSettlement());
            existing.setScore(data.getScore());
            existing.setOwner(data.getOwner());
            existing.setAdjacentHexagons(data.getAdjacentHexagons());
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
