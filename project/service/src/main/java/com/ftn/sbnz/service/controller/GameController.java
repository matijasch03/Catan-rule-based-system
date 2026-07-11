package com.ftn.sbnz.service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ftn.sbnz.service.dto.BoardStateDto;
import com.ftn.sbnz.service.service.GameActionException;
import com.ftn.sbnz.service.service.GameService;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/state")
    public BoardStateDto state() {
        return gameService.state();
    }

    @PostMapping("/new")
    public BoardStateDto newGame(@RequestBody(required = false) NewGameRequest req) {
        return gameService.newGame(req == null || req.autoOpponents);
    }

    @PostMapping("/place")
    public ResponseEntity<?> place(@RequestBody PlaceRequest req) {
        try {
            return ResponseEntity.ok(gameService.place(req.nodeId, req.edgeId));
        } catch (GameActionException ex) {
            return ResponseEntity.status(ex.getStatus()).body(ex.getMessage());
        }
    }

    @PostMapping("/endTurn")
    public ResponseEntity<?> endTurn() {
        try {
            return ResponseEntity.ok(gameService.endTurn());
        } catch (GameActionException ex) {
            return ResponseEntity.status(ex.getStatus()).body(ex.getMessage());
        }
    }

    @PostMapping("/build")
    public ResponseEntity<?> build(@RequestBody BuildRequest req) {
        try {
            return ResponseEntity.ok(gameService.build(req.action, req.nodeId, req.edgeId));
        } catch (GameActionException ex) {
            return ResponseEntity.status(ex.getStatus()).body(ex.getMessage());
        }
    }

    @PostMapping("/trade")
    public ResponseEntity<?> trade(@RequestBody(required = false) TradeRequest req) {
        try {
            return ResponseEntity.ok(gameService.offerTrade(req));
        } catch (GameActionException ex) {
            return ResponseEntity.status(ex.getStatus()).body(ex.getMessage());
        }
    }

    public static class PlaceRequest {
        public int nodeId;
        public int edgeId;
    }

    public static class NewGameRequest {
        public boolean autoOpponents = true;
    }

    public static class BuildRequest {
        public String action;
        public Integer nodeId;
        public Integer edgeId;
    }

    public static class TradeRequest {
        public String title;
        public boolean bankTrade;
        public Integer opponentId;
        public String wantedResource;
        public String offeredResource;
        public int offeredAmount = 1;
    }
}
