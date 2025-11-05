import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.Timer;

/**
 * AdaptiveStimuliSim.java
 *
 * Simple interactive Java Swing simulation of a simplified Adaptive Stimuli Algorithm.
 *
 * Controls:
 * - Click a cell to toggle stimulus (witness) on/off.
 * - Start / Pause button
 * - "p" slider controls probability p of generating alert tokens (0..1)
 * - Speed slider controls ticks-per-second
 *
 * This is a simplified demo implementing core behaviors:
 *   - Witnesses probabilistically generate alert tokens that move among Aware agents
 *     and convert Unaware neighbors to Aware.
 *   - When a witness is removed, it starts an all-clear broadcast through Aware agents
 *     to reset them to Unaware.
 *
 * Based on the Adaptive Stimuli Algorithm (paper provided). :contentReference[oaicite:1]{index=1}
 *
 * Author: (ChatGPT generated)
 */
public class AdaptiveStimuliSim {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new SimWindow(40, 60); // rows, cols -> change for larger/smaller grid
        });
    }
}

/* --- Agent model & states --- */
enum AgentState {
    U,          // Unaware
    A_EMPTY,    // Aware, no alert / witness / all-clear
    A_ALERT,    // Aware and holding an alert token
    A_WITNESS,  // Aware and witness (occupies stimulus)
    A_ALLCLEAR  // Aware and currently broadcasting all-clear (will become U)
}

class Agent {
    AgentState state = AgentState.U;
    boolean isWitness = false; // independent sense of whether stimulus is physically at this cell
    // (in demo, isWitness == (state == A_WITNESS) but kept separate for clarity)
}

/* --- Main GUI / Simulation window --- */
class SimWindow extends JFrame {
    final int rows, cols;
    final Agent[][] grid;
    final GridPanel gridPanel;
    final Random rnd = new Random();

    // simulation parameters (tweakable via UI)
    volatile double p = 0.15; // probability witness generates alert token on activation
    volatile int tickDelayMs = 80; // delay between activation ticks (speed)
    volatile boolean running = false;

    // Swing Timer for scheduling ticks on EDT
    Timer timer;

    // UI controls
    JButton startPauseBtn = new JButton("Start");
    JSlider pSlider; // 0..100 map to 0..1
    JSlider speedSlider;

    SimWindow(int rows, int cols) {
        super("Adaptive Stimuli Simulation");
        this.rows = rows;
        this.cols = cols;
        grid = new Agent[rows][cols];
        for (int r=0;r<rows;r++) for (int c=0;c<cols;c++) grid[r][c]= new Agent();

        gridPanel = new GridPanel();
        gridPanel.setPreferredSize(new Dimension(cols*12 + 1, rows*12 + 1));
        add(new JScrollPane(gridPanel), BorderLayout.CENTER);

        // control panel
        JPanel control = new JPanel();
        control.setLayout(new FlowLayout(FlowLayout.LEFT));
        control.add(startPauseBtn);

        startPauseBtn.addActionListener(e -> {
            if (!running) startSimulation();
            else stopSimulation();
        });

        control.add(new JLabel("p (alert gen):"));
        pSlider = new JSlider(0, 100, (int)(p*100));
        pSlider.setPreferredSize(new Dimension(120, 20));
        pSlider.addChangeListener(e -> p = pSlider.getValue()/100.0);
        control.add(pSlider);

        control.add(new JLabel("Speed:"));
        speedSlider = new JSlider(1, 200, 100 - (tickDelayMs/2));
        speedSlider.setPreferredSize(new Dimension(120, 20));
        speedSlider.addChangeListener(e -> {
            // map slider (1..200) to delay 2ms..400ms (inverted to make intuitive)
            int val = speedSlider.getValue();
            int mapped = Math.max(2, 400 - val*2);
            tickDelayMs = mapped;
            if (timer != null) {
                timer.setDelay(tickDelayMs);
            }
        });
        control.add(speedSlider);

        JButton resetBtn = new JButton("Reset");
        resetBtn.addActionListener(e -> resetGrid());
        control.add(resetBtn);

        add(control, BorderLayout.NORTH);

        // mouse to toggle stimulus
        gridPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int cellW = gridPanel.getCellSize();
                int c = e.getX() / cellW;
                int r = e.getY() / cellW;
                if (r>=0 && r<rows && c>=0 && c<cols) {
                    toggleStimulus(r, c);
                }
            }
        });

        pack();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);

        // initial timer (not running)
        timer = new Timer(tickDelayMs, ev -> stepSimulation());
        timer.setRepeats(true);
    }

    void startSimulation() {
        running = true;
        startPauseBtn.setText("Pause");
        timer.setDelay(tickDelayMs);
        timer.start();
    }

    void stopSimulation() {
        running = false;
        startPauseBtn.setText("Start");
        timer.stop();
    }

    void resetGrid() {
        for (int r=0;r<rows;r++) for (int c=0;c<cols;c++) {
            grid[r][c] = new Agent();
        }
        repaint();
    }

    void toggleStimulus(int r, int c) {
        Agent a = grid[r][c];
        if (!a.isWitness) {
            a.isWitness = true;
            a.state = AgentState.A_WITNESS;
        } else {
            // remove stimulus -> becomes non-witness; algorithm: convert to A_ALLCLEAR to broadcast
            a.isWitness = false;
            // if witness removed, set to A_ALLCLEAR which will broadcast on its activation
            a.state = AgentState.A_ALLCLEAR;
        }
        repaint();
    }

    /* Random-sequential activation: pick one agent uniformly at random and run its logic */
    void stepSimulation() {
        // pick random agent
        int r = rnd.nextInt(rows);
        int c = rnd.nextInt(cols);
        Agent u = grid[r][c];

        // 1. Non-matching witness flag / special priority
        if (u.isWitness && !(u.state==AgentState.A_WITNESS || u.state==AgentState.A_ALERT)) {
            // u is witness but flag not set: with prob p become A_WITNESS
            if (rnd.nextDouble() < p) {
                u.state = AgentState.A_WITNESS;
            }
            repaintCell(r,c);
            return;
        } else if (!u.isWitness && (u.state==AgentState.A_WITNESS || u.state==AgentState.A_ALERT)) {
            // u had witness flag but is no longer a witness -> become U and broadcast all-clear to aware neighbors
            // in this demo we set to A_ALLCLEAR and let A_ALLCLEAR broadcast on activation (but to be immediate, do here)
            u.state = AgentState.A_ALLCLEAR;
            // broadcast to Aware neighbors immediately (imitate algorithm lines 6-9)
            for (int[] nb : neighbors(r,c)) {
                Agent v = grid[nb[0]][nb[1]];
                if (isAwareState(v.state)) {
                    v.state = AgentState.A_ALLCLEAR;
                }
            }
            u.state = AgentState.U; // original witness becomes U (algorithm sets u.state <- U after broadcast)
            repaint();
            return;
        }

        // 2. State-specific behavior
        switch (u.state) {
            case U:
                // If any Aware neighbor has an alert token, consume it and become A_empty
                for (int[] nb : neighbors(r,c)) {
                    Agent v = grid[nb[0]][nb[1]];
                    if (v.state == AgentState.A_ALERT || v.state == AgentState.A_WITNESS && v.state != AgentState.A_ALLCLEAR && v.state != AgentState.A_EMPTY) {
                        // consume alert token if neighbor has A_ALERT OR A_WITNESS that also acts as alert generator?
                        if (v.state == AgentState.A_ALERT) {
                            v.state = AgentState.A_EMPTY; // token consumed
                            u.state = AgentState.A_EMPTY;
                            break;
                        }
                    }
                }
                break;

            case A_EMPTY:
                // do nothing unless an Aware neighbor transfers an alert token to us (handled above in U)
                break;

            case A_ALERT:
                // dmax-random walk
                List<int[]> nbs = new ArrayList<>(neighbors(r, c));
                Collections.shuffle(nbs, rnd);
                int pick = rnd.nextInt(nbs.size() + 1); // +1 = possibility of staying
                if (pick < nbs.size()) {
                    int[] nb = nbs.get(pick);
                    Agent v = grid[nb[0]][nb[1]];
                    if (isAwareNoAlert(v.state)) {
                        // move alert token to neighbor v
                        v.state = AgentState.A_ALERT;
                        if (u.isWitness) u.state = AgentState.A_WITNESS;
                        else u.state = AgentState.A_EMPTY;
                    }
                }
                break;

            case A_WITNESS:
                // With probability p, generate alert token (switch to A_ALERT)
                if (rnd.nextDouble() < p) {
                    u.state = AgentState.A_ALERT;
                }
                break;

            case A_ALLCLEAR:
                // broadcast all-clear to aware neighbors, then become Unaware
                for (int[] nb : neighbors(r,c)) {
                    Agent v = grid[nb[0]][nb[1]];
                    if (isAwareState(v.state)) {
                        v.state = AgentState.A_ALLCLEAR;
                    }
                }
                u.state = AgentState.U;
                break;
        }

        // repaint local cells
        repaint();
    }

    // helper: return 4-neighbors (up/down/left/right) (can be changed to 8-neighbors or triangular later)
    List<int[]> neighbors(int r, int c) {
        List<int[]> res = new ArrayList<>();
        if (r>0) res.add(new int[]{r-1,c});
        if (r<rows-1) res.add(new int[]{r+1,c});
        if (c>0) res.add(new int[]{r,c-1});
        if (c<cols-1) res.add(new int[]{r,c+1});
        return res;
    }

    boolean isAwareState(AgentState s) {
        return s==AgentState.A_EMPTY || s==AgentState.A_ALERT || s==AgentState.A_WITNESS || s==AgentState.A_ALLCLEAR;
    }
    boolean isAwareNoAlert(AgentState s) {
        return s==AgentState.A_EMPTY || s==AgentState.A_WITNESS;
    }

    void repaintCell(int r, int c) {
        gridPanel.repaint();
    }

    /* --- UI: grid painter --- */
    class GridPanel extends JPanel {
        int baseCell = 12;
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int cell = getCellSize();
            for (int i=0;i<rows;i++) for (int j=0;j<cols;j++) {
                Agent a = grid[i][j];
                int x = j*cell, y = i*cell;
                // background by state
                switch (a.state) {
                    case U: g.setColor(new Color(0xFFF2A8)); break; // yellowish
                    case A_EMPTY: g.setColor(new Color(0xFF9E9E)); break; // light red
                    case A_ALERT: g.setColor(new Color(0xC62828)); break; // dark red
                    case A_WITNESS: g.setColor(new Color(0x2E7D32)); break; // green (witness)
                    case A_ALLCLEAR: g.setColor(new Color(0x7E57C2)); break; // purple
                    default: g.setColor(Color.LIGHT_GRAY);
                }
                g.fillRect(x, y, cell-1, cell-1);

                // small overlays
                if (grid[i][j].isWitness) {
                    g.setColor(Color.YELLOW);
                    g.drawOval(x+2, y+2, cell-6, cell-6);
                }
                // grid lines
                g.setColor(Color.DARK_GRAY);
                g.drawRect(x, y, cell-1, cell-1);
            }
        }

        int getCellSize() {
            return baseCell;
        }

        @Override
        public Dimension getPreferredSize() {
            int s = getCellSize();
            return new Dimension(cols*s + 1, rows*s + 1);
        }
    }
}


