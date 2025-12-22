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
import java.util.HashSet;
import java.util.Set;

/* =========================================================
   Adaptive Stimuli – Bug #1 + Bug #2 Fixed
   ========================================================= */

public class AdaptiveStimuli_FinalCorrect {
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
    int stimulusId = -1;
}

class Token {
    int r, c;
    int hopsLeft;
    int stimulusId;

    Token(int r, int c, int hops, int sid) {
        this.r = r;
        this.c = c;
        this.hopsLeft = hops;
        this.stimulusId = sid;
    }
}

/* ===================== SIM FRAME ===================== */

class SimFrame extends JFrame {

    final int rows, cols;
    final Cell[][] grid;
    final List<Token> tokens = new ArrayList<>();
    final Set<Integer> inactiveStimuli = new HashSet<>();

    final Random rnd = new Random();
    final GridPanel panel;
    Timer timer;

    double tokenGenProb = 0.15;
    int tokenMaxHops = 12;
    int delayMs = 80;

    int nextStimulusId = 1;

    SimFrame(int r, int c) {
        super("Adaptive Stimuli – Diagonal Adjacency Fixed");

        rows = r;
        cols = c;

        grid = new Cell[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                grid[i][j] = new Cell();

        panel = new GridPanel();
        panel.setPreferredSize(new Dimension(cols * 14, rows * 14));

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
                if (x >= 0 && x < cols && y >= 0 && y < rows)
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
            u.stimulusId = nextStimulusId++;
            u.state = State.AWARE;
        }
        else if (u.stimulus) {
            inactiveStimuli.add(u.stimulusId);
            u.stimulus = false;
            u.stimulusId = -1;
            u.state = State.ALL_CLEAR;
        }
        repaint();
    }

    /* ===================== SIMULATION ===================== */

    void step() {

        /* ---- TOKEN GENERATION ---- */
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                Cell u = grid[i][j];
                if (u.stimulus && rnd.nextDouble() < tokenGenProb) {
                    tokens.add(new Token(i, j, tokenMaxHops, u.stimulusId));
                }
            }
        }

        /* ---- TOKEN WALK ---- */
        List<Token> dead = new ArrayList<>();

        for (Token t : tokens) {

            if (inactiveStimuli.contains(t.stimulusId) || t.hopsLeft <= 0) {
                dead.add(t);
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
        tokens.removeAll(dead);

        /* ---- FLOOD ALL-CLEAR ---- */
        List<int[]> toPropagate = new ArrayList<>();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j].state == State.ALL_CLEAR) {
                    if (hasWitnessNeighbor(i, j)) {
                        grid[i][j].state = State.AWARE;
                    } else {
                        toPropagate.add(new int[]{i, j});
                    }
                }
            }
        }

        for (int[] pos : toPropagate) {
            int r0 = pos[0], c0 = pos[1];
            for (int[] nb : neighbors(r0, c0)) {
                Cell v = grid[nb[0]][nb[1]];
                if (v.state == State.AWARE) {
                    v.state = State.ALL_CLEAR;
                }
            }
            grid[r0][c0].state = State.UNAWARE;
        }

        repaint();
    }

    /* ===================== HELPERS ===================== */

    boolean hasWitnessNeighbor(int r, int c) {
        for (int[] nb : neighbors(r, c))
            if (grid[nb[0]][nb[1]].stimulus)
                return true;
        return false;
    }

    /* 🔑 FIX #2: 8-connected adjacency */
    List<int[]> neighbors(int r, int c) {
        List<int[]> list = new ArrayList<>();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr;
                int nc = c + dc;
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                    list.add(new int[]{nr, nc});
                }
            }
        }
        return list;
    }

    /* ===================== DRAWING ===================== */

    class GridPanel extends JPanel {
        final int cell = 14;

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    Cell u = grid[i][j];
                    if (u.stimulus) g.setColor(new Color(60, 170, 60));
                    else if (u.state == State.AWARE) g.setColor(new Color(255, 160, 160));
                    else if (u.state == State.ALL_CLEAR) g.setColor(new Color(160, 130, 210));
                    else g.setColor(new Color(255, 240, 170));

                    g.fillRect(j * cell, i * cell, cell - 1, cell - 1);
                }
            }

            g.setColor(new Color(180, 40, 40));
            for (Token t : tokens) {
                g.fillOval(t.c * cell + 4, t.r * cell + 4, 6, 6);
            }
        }
    }
}
