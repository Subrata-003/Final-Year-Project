import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Adaptive Stimuli Algorithm Simulation (FYP Implementation)
 * Based on: "Adaptive Collective Responses to Local Stimuli in Anonymous Dynamic Networks"
 *
 * Paper Reference: Algorithm 1 (Page 6)
 * Visualization Reference: Figure 6 (Page 21)
 */
public class AdaptiveStimuliSim extends JFrame {

    // --- Simulation Parameters ---
    private static final int GRID_SIZE = 30; // 30x30 Lattice
    private static final int WINDOW_SIZE = 800;
    private static final int DELAY = 5; // Simulation speed (ms delay between ticks)
    private static final int DELTA = 4; // Max degree (4 for Grid, 6 for Triangular Lattice)
    private static final double P_PROB = 0.05; // Probability p (must be < 1/w)

    // --- Core State ---
    private Agent[][] grid;
    private Timer timer;
    private Random random = new Random();
    private SimulationPanel canvas;
    private JLabel statsLabel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new AdaptiveStimuliSim().setVisible(true);
        });
    }

    public AdaptiveStimuliSim() {
        setTitle("Adaptive Stimuli Algorithm - FYP Simulation");
        setSize(WINDOW_SIZE, WINDOW_SIZE + 100);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Initialize Simulation
        initGrid();

        // UI Components
        canvas = new SimulationPanel();
        add(canvas, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        JButton btnReset = new JButton("Reset");
        JButton btnClearFood = new JButton("Remove All Food");
        JToggleButton btnPause = new JToggleButton("Pause");
        statsLabel = new JLabel("Ticks: 0 | Aware: 0");

        btnReset.addActionListener(e -> {
            initGrid();
            canvas.repaint();
        });
        
        btnClearFood.addActionListener(e -> {
            for (int r = 0; r < GRID_SIZE; r++) {
                for (int c = 0; c < GRID_SIZE; c++) {
                    grid[r][c].isWitness = false;
                }
            }
            canvas.repaint();
        });

        btnPause.addActionListener(e -> {
            if (btnPause.isSelected()) timer.stop();
            else timer.start();
        });

        controlPanel.add(btnReset);
        controlPanel.add(btnClearFood);
        controlPanel.add(btnPause);
        controlPanel.add(statsLabel);
        add(controlPanel, BorderLayout.SOUTH);

        // Simulation Loop (Random Sequential Scheduler)
        timer = new Timer(DELAY, e -> performStep());
        timer.start();
    }

    private void initGrid() {
        grid = new Agent[GRID_SIZE][GRID_SIZE];
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                grid[r][c] = new Agent(r, c);
            }
        }
    }

    /**
     * The Main Scheduler Loop.
     * The paper assumes a Random Sequential Scheduler:
     * "Chooses an agent uniformly at random to be activated" (Page 3).
     */
    private void performStep() {
        // 1. Pick one random agent
        int r = random.nextInt(GRID_SIZE);
        int c = random.nextInt(GRID_SIZE);
        Agent u = grid[r][c];

        // 2. Execute Algorithm 1
        runAlgorithm1(u);

        // 3. Update Stats & UI
        canvas.repaint();
        updateStats();
    }

    /**
     * ALGORITHM 1: Adaptive Stimuli Algorithm
     * Implements the pseudo-code from Page 6 exactly.
     */
    private void runAlgorithm1(Agent u) {
        // Identify Neighbors (Graph Topology)
        List<Agent> neighbors = getNeighbors(u);
        List<Agent> awareNeighbors = new ArrayList<>();
        for (Agent v : neighbors) {
            if (v.isAwareState()) awareNeighbors.add(v);
        }

        // --- Logic Block Matching Pseudo-code ---

        // Line 4: If u observes stimulus (isWitness) but state is not a Witness State
        if (u.isWitness && !u.isWitnessState()) {
            // Line 5: With probability p, become AWARE_WITNESS
            if (random.nextDouble() < P_PROB) {
                u.state = AgentState.A_W;
            }
        }
        // Line 6: Else if u is NOT witness but is in a Witness State (Stimulus Disappeared)
        else if (!u.isWitness && u.isWitnessState()) {
            // Line 7-8: Broadcast All-Clear to AWARE neighbors
            for (Agent v : awareNeighbors) {
                v.state = AgentState.A_C; // v.state <- A_{C}
            }
            // Line 9: u becomes All-Clear
            u.state = AgentState.A_C;
        }
        // Line 10: Else (Standard State Transitions)
        else {
            switch (u.state) {
                // Line 12: Case U (Unaware)
                case U:
                    // Line 13: Check if exists neighbor v in Aware State with Alert Token
                    for (Agent v : neighbors) {
                        if (v.hasAlertToken()) {
                            // Line 14: v consumes alert token (A_S \ {A})
                            v.consumeAlertToken();
                            // Line 15: u becomes Aware (A_empty)
                            u.state = AgentState.A_EMPTY;
                            break; // Process one interaction per activation
                        }
                    }
                    break;

                // Line 16: Case A_{A} or A_{A,W} (Aware with Alert Token)
                case A_A:
                case A_AW:
                    // Line 17: Random value x in [0, 1]
                    // Line 18: d_max random walk condition (x <= d_G(u) / Delta)
                    // We use Delta = 4 (Grid)
                    double probMove = (double) neighbors.size() / DELTA;
                    
                    if (random.nextDouble() <= probMove) {
                        // Line 19: Pick random neighbor v
                        Agent v = neighbors.get(random.nextInt(neighbors.size()));

                        // Line 20: If v is AWARE but has NO Token and NO All-Clear
                        // (State is A_empty or A_W)
                        if (v.state == AgentState.A_EMPTY || v.state == AgentState.A_W) {
                            // Line 21: u sends alert token (u loses A)
                            u.removeAlertToken();
                            // Line 22: v receives alert token (v gains A)
                            v.addAlertToken();
                        }
                    }
                    break;

                // Line 24: Case A_{W} (Witness without Token)
                case A_W:
                    // With probability p, generate alert token
                    if (random.nextDouble() < P_PROB) {
                        u.state = AgentState.A_AW;
                    }
                    break;

                // Line 26: Case A_{C} (All-Clear)
                case A_C:
                    // Line 27: Broadcast All-Clear to all aware neighbors
                    for (Agent v : awareNeighbors) {
                        v.state = AgentState.A_C;
                    }
                    // Line 28: u becomes Unaware
                    u.state = AgentState.U;
                    break;
                    
                case A_EMPTY:
                    // Does nothing specific passively, waits to receive token or clear
                    break;
            }
        }
    }

    private List<Agent> getNeighbors(Agent u) {
        List<Agent> list = new ArrayList<>();
        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};

        for (int i = 0; i < 4; i++) {
            int nr = u.r + dr[i];
            int nc = u.c + dc[i];
            // Check bounds (Grid topology)
            if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
                list.add(grid[nr][nc]);
            }
        }
        return list;
    }

    private void updateStats() {
        long awareCount = 0;
        long tickCount = 0; // Just for visual liveness
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (grid[r][c].isAwareState()) awareCount++;
            }
        }
        statsLabel.setText(String.format("Simulation Running... | Aware Agents: %d / %d", awareCount, GRID_SIZE*GRID_SIZE));
    }

    // --- Enums and Classes ---

    enum AgentState {
        U,          // Unaware
        A_EMPTY,    // Aware (No flags)
        A_A,        // Aware + Alert Token
        A_W,        // Aware + Witness
        A_AW,       // Aware + Alert Token + Witness
        A_C         // Aware + All-Clear Token
    }

    class Agent {
        int r, c;
        boolean isWitness = false; // The environmental stimulus
        AgentState state = AgentState.U;

        public Agent(int r, int c) {
            this.r = r;
            this.c = c;
        }

        // --- Helper predicates for readability based on Algorithm 1 sets ---
        
        // Checks if state is in {A_W, A_AW}
        boolean isWitnessState() {
            return state == AgentState.A_W || state == AgentState.A_AW;
        }

        // Checks if state is any AWARE state (A_empty, A_A, A_W, A_AW, A_C)
        boolean isAwareState() {
            return state != AgentState.U;
        }

        // Checks if state has Alert Token {A_A, A_AW}
        boolean hasAlertToken() {
            return state == AgentState.A_A || state == AgentState.A_AW;
        }

        boolean hasAllClearToken() {
            return state == AgentState.A_C;
        }

        // State transitions for Token Passing
        void consumeAlertToken() {
            if (state == AgentState.A_A) state = AgentState.A_EMPTY;
            if (state == AgentState.A_AW) state = AgentState.A_W;
        }

        void removeAlertToken() {
            consumeAlertToken();
        }

        void addAlertToken() {
            if (state == AgentState.A_EMPTY) state = AgentState.A_A;
            if (state == AgentState.A_W) state = AgentState.A_AW;
        }
    }

    // --- Visualization Panel ---
    class SimulationPanel extends JPanel {
        public SimulationPanel() {
            // Add Mouse Listener for interacting with Food
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int width = getWidth();
                    int height = getHeight();
                    int cellW = width / GRID_SIZE;
                    int cellH = height / GRID_SIZE;

                    int c = e.getX() / cellW;
                    int r = e.getY() / cellH;

                    if (r >= 0 && r < GRID_SIZE && c >= 0 && c < GRID_SIZE) {
                        grid[r][c].isWitness = !grid[r][c].isWitness; // Toggle Food
                        repaint();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int cellW = width / GRID_SIZE;
            int cellH = height / GRID_SIZE;

            for (int r = 0; r < GRID_SIZE; r++) {
                for (int c = 0; c < GRID_SIZE; c++) {
                    Agent agent = grid[r][c];
                    int x = c * cellW;
                    int y = r * cellH;

                    // Draw Agent Base Color
                    g2.setColor(getColorForState(agent.state));
                    g2.fillOval(x + 2, y + 2, cellW - 4, cellH - 4);

                    // Draw Witness/Food Indicator (Ring)
                    if (agent.isWitness) {
                        g2.setColor(Color.GREEN);
                        g2.setStroke(new BasicStroke(3));
                        g2.drawOval(x + 2, y + 2, cellW - 4, cellH - 4);
                    }
                    
                    // Optional: Draw 'x' for Alert Token (visual aid)
                    if (agent.hasAlertToken()) {
                        g2.setColor(Color.BLACK);
                        g2.drawString("!", x + cellW/2 - 2, y + cellH/2 + 4);
                    }
                }
            }
            
            // Draw Legend
            drawLegend(g2);
        }

        private Color getColorForState(AgentState state) {
            // Colors based on Paper Figure 6 description
            switch (state) {
                case U: return new Color(255, 255, 200); // Yellow (Unaware)
                case A_EMPTY: return new Color(255, 100, 100); // Light Red (Aware)
                case A_A: 
                case A_AW: return new Color(180, 0, 0); // Dark Red (Has Token)
                case A_W: return new Color(255, 100, 100); // Red (Witness but no token yet)
                case A_C: return new Color(128, 0, 128); // Purple (All Clear)
                default: return Color.GRAY;
            }
        }

        private void drawLegend(Graphics2D g2) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            int y = 20;
            int x = 20;
            
            drawLegendItem(g2, "Unaware (U)", new Color(255, 255, 200), x, y); y += 20;
            drawLegendItem(g2, "Aware (A)", new Color(255, 100, 100), x, y); y += 20;
            drawLegendItem(g2, "Alert Token", new Color(180, 0, 0), x, y); y += 20;
            drawLegendItem(g2, "All Clear", new Color(128, 0, 128), x, y); y += 20;
            
            // Food Legend
            g2.setColor(Color.GREEN);
            g2.setStroke(new BasicStroke(3));
            g2.drawOval(x, y, 12, 12);
            g2.setColor(Color.BLACK);
            g2.drawString("Food / Stimulus", x + 20, y + 10);
        }
        
        private void drawLegendItem(Graphics2D g, String text, Color c, int x, int y) {
            g.setColor(c);
            g.fillOval(x, y, 12, 12);
            g.setColor(Color.BLACK);
            g.drawString(text, x + 20, y + 10);
        }
    }
}