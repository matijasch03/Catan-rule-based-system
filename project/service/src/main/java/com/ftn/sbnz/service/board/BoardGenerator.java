package com.ftn.sbnz.service.board;

import java.util.Comparator;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.service.service.HexagonService;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BoardGenerator implements CommandLineRunner {

    private final HexagonService hexagonService;
    private final BoardTopologyBuilder topologyBuilder;
    private final BoardTileShuffler tileShuffler;

    public BoardGenerator(HexagonService hexagonService, BoardTopologyBuilder topologyBuilder,
                          BoardTileShuffler tileShuffler) {
        this.hexagonService = hexagonService;
        this.topologyBuilder = topologyBuilder;
        this.tileShuffler = tileShuffler;
    }

    @Override
    public void run(String... args) {
        List<Hexagon> existing = hexagonService.getAll();
        if (existing.isEmpty()) {
            topologyBuilder.createBoard();
            return;
        }
        reshuffle(existing);
    }

    public List<Hexagon> reshuffle() {
        List<Hexagon> hexagons = hexagonService.getAll();
        reshuffle(hexagons);
        return hexagons;
    }

    private void reshuffle(List<Hexagon> hexagons) {
        hexagons.sort(Comparator.comparingInt(Hexagon::getId));
        tileShuffler.assignFieldsAndDots(hexagons);
        for (Hexagon hexagon : hexagons) {
            hexagonService.create(hexagon);
        }
    }
}
