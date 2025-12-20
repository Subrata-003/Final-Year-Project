import javax.swing.*;
import javax.swing.Timer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/* =========================================================
   Adaptive Stimuli – Token Walk Correct Implementation
   ========================================================= */

public class AdaptiveStimuli_TokenWalk {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SimFrame(35, 35));
    }
}

/* ===================== MODEL ===================== */

enum State {
    UNAWARE,
    AWARE,
    ALL_CLEAR
}

class Cell {
    State state = State.UNAWARE;
    boolean stimulus = false;
}

class Token {
    int r, c;
    int hopsLeft;

    Token(int r, int c, int hops) {
        this.r = r;
        this.c = c;
        this.hopsLeft = hops;
    }
}

/* ===================== SIM FRAME ===================== */

class SimFrame extends JFrame {

    final int rows, cols;
    final Cell[][] grid;
    final List<Token> tokens = new ArrayList<>();

    final Random rnd = new Random();
    final GridPanel panel;
    Timer timer;

    // Tunable parameters
    double tokenGenProb = 0.5;   // witness emits tokens repeatedly
    int tokenMaxHops = 10;       // true multi-hop walk
    int delayMs = 30;

    SimFrame(int r, int c) {
        super("Adaptive Stimuli – Multi-Hop Token Walk");

        rows = r;
        cols = c;

        grid = new Cell[rows][cols];
        for (int i=0;i<rows;i++)
            for (int j=0;j<cols;j++)
                grid[i][j] = new Cell();

        panel = new GridPanel();
        panel.setPreferredSize(new Dimension(cols*14, rows*14));

        JButton start = new JButton("Start / Pause");
        start.addActionListener(e -> toggle());

        JPanel top = new JPanel();
        top.add(start);

        add(top, BorderLayout.NORTH);
        add(panel, BorderLayout.CENTER);

        panel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int x = e.getX() / panel.cell;
                int y = e.getY() / panel.cell;
                if (x>=0 && x<cols && y>=0 && y<rows)
                    handleClick(y, x);
            }
        });

        timer = new Timer(delayMs, e -> step());

        pack();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    void toggle() {
        if (timer.isRunning()) timer.stop();
        else timer.start();
    }

    /* ===================== INPUT ===================== */

    void handleClick(int r, int c) {
        Cell u = grid[r][c];

        if (!u.stimulus && u.state == State.UNAWARE) {
            u.stimulus = true;
            u.state = State.AWARE;
        }
        else if (u.stimulus) {
            u.stimulus = false;
            u.state = State.ALL_CLEAR;
        }
        repaint();
    }

    /* ===================== SIMULATION ===================== */

    void step() {
        // 1️⃣ Witnesses generate tokens continuously
        for (int i=0;i<rows;i++) {
            for (int j=0;j<cols;j++) {
                if (grid[i][j].stimulus && rnd.nextDouble() < tokenGenProb) {
                    tokens.add(new Token(i, j, tokenMaxHops));
                }
            }
        }

        // 2️⃣ Move tokens (true random walks)
        List<Token> toRemove = new ArrayList<>();

        for (Token t : tokens) {
            if (t.hopsLeft <= 0) {
                toRemove.add(t);
                continue;
            }

            List<int[]> nbs = neighbors(t.r, t.c);
            Collections.shuffle(nbs, rnd);

            int[] nb = nbs.get(0);
            t.r = nb[0];
            t.c = nb[1];
            t.hopsLeft--;

            if (grid[t.r][t.c].state == State.UNAWARE) {
                grid[t.r][t.c].state = State.AWARE;
            }
        }

        tokens.removeAll(toRemove);

        // 3️⃣ All-clear propagation (unchanged & correct)
        int r = rnd.nextInt(rows);
        int c = rnd.nextInt(cols);
        Cell u = grid[r][c];

        if (u.state == State.ALL_CLEAR) {
            boolean blocked = hasWitnessNeighbor(r,c);
            if (!blocked) {
                for (int[] nb : neighbors(r,c)) {
                    Cell v = grid[nb[0]][nb[1]];
                    if (v.state == State.AWARE) {
                        v.state = State.ALL_CLEAR;
                    }
                }
                u.state = State.UNAWARE;
            } else {
                u.state = State.AWARE;
            }
        }

        repaint();
    }

    /* ===================== HELPERS ===================== */

    boolean hasWitnessNeighbor(int r, int c) {
        for (int[] nb : neighbors(r,c))
            if (grid[nb[0]][nb[1]].stimulus)
                return true;
        return false;
    }

    List<int[]> neighbors(int r, int c) {
        List<int[]> list = new ArrayList<>();
        if (r>0) list.add(new int[]{r-1,c});
        if (r<rows-1) list.add(new int[]{r+1,c});
        if (c>0) list.add(new int[]{r,c-1});
        if (c<cols-1) list.add(new int[]{r,c+1});
        return list;
    }

    /* ===================== DRAWING ===================== */

    class GridPanel extends JPanel {
        final int cell = 14;

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            for (int i=0;i<rows;i++) {
                for (int j=0;j<cols;j++) {
                    Cell u = grid[i][j];
                    if (u.stimulus) g.setColor(new Color(60,170,60));
                    else if (u.state == State.AWARE) g.setColor(new Color(255,160,160));
                    else if (u.state == State.ALL_CLEAR) g.setColor(new Color(160,130,210));
                    else g.setColor(new Color(255,240,170));

                    g.fillRect(j*cell, i*cell, cell-1, cell-1);
                }
            }

            // draw tokens
            g.setColor(new Color(180,40,40));
            for (Token t : tokens) {
                g.fillOval(t.c*cell+4, t.r*cell+4, 6, 6);
            }
        }
    }
}
