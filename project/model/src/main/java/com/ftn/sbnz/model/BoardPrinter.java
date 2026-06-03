package com.ftn.sbnz.model;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BoardPrinter {
    public static void main(String[] args) {
        List<List<Hexagon>> board = BoardGenerator.generateBoard();
        printBoard(board);

        // generate and print nodes for verification
        List<Node> nodes = BoardGenerator.generateNodes(board);
        // sort by id for stable output
        Collections.sort(nodes, Comparator.comparingInt(Node::getId));
        System.out.println("\nNodes (id,orientation,score,adjHexCount):");
        for (Node n : nodes) {
            System.out.println(n.getId() + " " + n.getOrientation() + " score=" + n.getScore() + " adj=" + n.getAdjacentHexagons().size());
        }
    }

    public static void printBoard(List<List<Hexagon>> board) {
        for (int r = 0; r < board.size(); r++) {
            List<Hexagon> row = board.get(r);
            // indent to make hex grid shape
            int indent = Math.abs((board.size()/2) - r);
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < indent; i++) line.append("      ");
            for (Hexagon h : row) {
                int rotatedQ = h.getQ() + h.getR();
                int rotatedR = -h.getR();
                String res = (h.getField() == null) ? "null" : h.getField().name();
                line.append(String.format("[%2d (%d,%d) %s] ", h.getId(), rotatedQ, rotatedR, res));
            }
            System.out.println(line.toString());
        }
        // show center tile info
        Hexagon center = findCenter(board);
        if (center != null) {
            int centerRotatedQ = center.getQ() + center.getR();
            int centerRotatedR = -center.getR();
            System.out.println("\nCenter tile id=" + center.getId() + " q=" + centerRotatedQ + " r=" + centerRotatedR + " field=" + center.getField());
        }
    }

    private static Hexagon findCenter(List<List<Hexagon>> board) {
        for (List<Hexagon> row : board) {
            for (Hexagon h : row) {
                if (h.getQ() == 0 && h.getR() == 0) return h;
            }
        }
        return null;
    }

}
