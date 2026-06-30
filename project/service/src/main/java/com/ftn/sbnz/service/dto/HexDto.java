package com.ftn.sbnz.service.dto;

import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.model.Resource;

// Flat view of a hexagon for the board UI, avoiding the Hexagon<->Node cycle.
public class HexDto {
    private int id;
    private int q;
    private int r;
    private Resource field;
    private int dots;

    public HexDto() {
    }

    public HexDto(Hexagon h) {
        this.id = h.getId();
        this.q = h.getQ();
        this.r = h.getR();
        this.field = h.getField();
        this.dots = h.getDots();
    }

    public int getId() {
        return id;
    }

    public int getQ() {
        return q;
    }

    public int getR() {
        return r;
    }

    public Resource getField() {
        return field;
    }

    public int getDots() {
        return dots;
    }
}
