package com.ftn.sbnz.service.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.service.board.BoardGenerator;
import com.ftn.sbnz.service.dto.HexDto;
import com.ftn.sbnz.service.service.HexagonService;

@RestController
@RequestMapping("/api/board")
public class BoardController {

    private final HexagonService hexagonService;
    private final BoardGenerator boardGenerator;

    public BoardController(HexagonService hexagonService, BoardGenerator boardGenerator) {
        this.hexagonService = hexagonService;
        this.boardGenerator = boardGenerator;
    }

    // Current board as flat tiles, ordered by id for a stable layout.
    @GetMapping
    public List<HexDto> getBoard() {
        return toDtos(hexagonService.getAll());
    }

    // Reassign resources and number tokens to the existing hexes (Reload button).
    @PostMapping("/reshuffle")
    public List<HexDto> reshuffle() {
        return toDtos(boardGenerator.reshuffle());
    }

    private List<HexDto> toDtos(List<Hexagon> hexagons) {
        return hexagons.stream()
                .sorted(Comparator.comparingInt(Hexagon::getId))
                .map(HexDto::new)
                .toList();
    }
}
