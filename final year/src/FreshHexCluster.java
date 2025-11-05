import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * FreshHexCluster.java
 *
 * Connected tessellation of 20 flat-top hexagons (sharing vertices),
 * deduplicated holes, and random placement of 20 agents. GUI is cleaned
 * up — no numbering, minimal styling, soft colors for a fresh look.
 *
 * Compile:
 *   javac FreshHexCluster.java
 * Run:
 *   java FreshHexCluster
 *
 * Click "Regenerate" to randomly re-place 20 agents.
 */
public class FreshHexCluster {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClusterFrame());
    }
}

class ClusterFrame extends JFrame {
    final int hexCount = 20;
    final int gridCols = 5;  // arrangement: cols x rows >= hexCount
    final int gridRows = 4;  // 5 * 4 = 20
    final int hexRadius = 44; // size of hex
    final int horizSpacing;
    final int vertSpacing;

    final List<Hex> hexes = new ArrayList<>();
    final List<Hole> holes = new ArrayList<>();
    final Map<String,Integer> holeIndexByXY = new HashMap<>();
    final Set<Integer> agentHoleIndices = new HashSet<>();
    final Random rnd = new Random();

    final DrawPanel drawPanel;

    ClusterFrame() {
        super("Hex Cluster — Fresh View");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        horizSpacing = (int)Math.round(1.5 * hexRadius);
        vertSpacing = (int)Math.round(Math.sqrt(3) * hexRadius);

        buildHexGridAndHoles();

        drawPanel = new DrawPanel();
        int width = gridCols * horizSpacing + hexRadius*2 + 120;
        int height = gridRows * vertSpacing + hexRadius*2 + 120;
        drawPanel.setPreferredSize(new Dimension(width, height));

        JButton regen = new JButton("Regenerate");
        regen.setFocusPainted(false);
        regen.addActionListener(e -> {
            randomPlaceAgents(20);
            drawPanel.repaint();
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        top.setBackground(new Color(248, 249, 250));
        regen.setBackground(new Color(30, 144, 255));
        regen.setForeground(Color.WHITE);
        regen.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        top.add(regen);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(drawPanel), BorderLayout.CENTER);

        randomPlaceAgents(20);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    void buildHexGridAndHoles() {
        hexes.clear();
        holes.clear();
        holeIndexByXY.clear();

        int idx = 0;
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                if (idx >= hexCount) break;
                int cx = 70 + c * horizSpacing;
                int cy = 70 + r * vertSpacing + (c % 2 == 1 ? vertSpacing/2 : 0);
                Hex h = new Hex(idx, cx, cy, hexRadius);
                hexes.add(h);
                for (int v = 0; v < 6; v++) {
                    Point p = h.vertex(v);
                    String key = p.x + "," + p.y;
                    if (!holeIndexByXY.containsKey(key)) {
                        int holeIndex = holes.size();
                        holes.add(new Hole(h.index, v, p.x, p.y));
                        holeIndexByXY.put(key, holeIndex);
                    }
                }
                idx++;
            }
        }

        // assign vertex -> global hole indices for each hex
        for (Hex h : hexes) {
            h.vertexHoleIndices = new int[6];
            for (int v = 0; v < 6; v++) {
                Point p = h.vertex(v);
                String key = p.x + "," + p.y;
                Integer gi = holeIndexByXY.get(key);
                h.vertexHoleIndices[v] = gi;
            }
        }
    }

    void randomPlaceAgents(int k) {
        agentHoleIndices.clear();
        if (k <= 0 || holes.isEmpty()) return;
        k = Math.min(k, holes.size());
        while (agentHoleIndices.size() < k) {
            int pick = rnd.nextInt(holes.size());
            agentHoleIndices.add(pick);
        }
    }

    // ----- helper classes -----
    static class Hex {
        final int index;
        final int cx, cy, r;
        int[] vertexHoleIndices;
        Hex(int index, int cx, int cy, int r) {
            this.index = index; this.cx = cx; this.cy = cy; this.r = r;
        }
        Point vertex(int v) {
            double angle_deg = 60 * v;
            double angle_rad = Math.toRadians(angle_deg);
            int x = cx + (int)Math.round(r * Math.cos(angle_rad));
            int y = cy + (int)Math.round(r * Math.sin(angle_rad));
            return new Point(x, y);
        }
        Polygon polygon() {
            Polygon p = new Polygon();
            for (int v = 0; v < 6; v++) {
                Point pt = vertex(v);
                p.addPoint(pt.x, pt.y);
            }
            return p;
        }
    }
    static class Hole {
        final int hexIndex, vertexIndex;
        final int x, y;
        Hole(int hexIndex, int vertexIndex, int x, int y) {
            this.hexIndex = hexIndex; this.vertexIndex = vertexIndex;
            this.x = x; this.y = y;
        }
    }

    // ----- drawing panel -----
    class DrawPanel extends JPanel {
        final int holeRadius = 6;
        final int agentRadius = 10;

        DrawPanel() {
            setBackground(new Color(250, 251, 253));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    // optional: clicking toggles regenerate if clicked near bottom-right
                    // (kept minimal intentionally)
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // soft shadow + hex fills
            for (Hex h : hexes) {
                Polygon poly = h.polygon();

                // subtle drop shadow
                g.setColor(new Color(220, 224, 235, 90));
                g.translate(3, 3);
                g.fillPolygon(poly);
                g.translate(-3, -3);

                // gentle gradient fill using two colors
                Paint old = g.getPaint();
                Point p0 = h.vertex(0);
                Point p3 = h.vertex(3);
                GradientPaint gp = new GradientPaint(h.cx, h.cy - h.r/2, new Color(245, 248, 250),
                        h.cx, h.cy + h.r/2, new Color(235, 241, 246));
                g.setPaint(gp);
                g.fillPolygon(poly);
                g.setPaint(old);

                // subtle border
                g.setColor(new Color(120, 130, 150, 200));
                g.setStroke(new BasicStroke(1.4f));
                g.drawPolygon(poly);
            }

            // draw holes (small outlined dots)
            g.setColor(new Color(110, 120, 140, 200));
            for (Hole hole : holes) {
                int x = hole.x, y = hole.y;
                g.setStroke(new BasicStroke(1.2f));
                g.drawOval(x - holeRadius, y - holeRadius, holeRadius*2, holeRadius*2);
            }

            // draw agents: modern circular marker with small white center
            for (Integer gi : agentHoleIndices) {
                if (gi < 0 || gi >= holes.size()) continue;
                Hole hole = holes.get(gi);
                int ax = hole.x, ay = hole.y;

                // outer halo
                g.setColor(new Color(30, 136, 229, 160)); // semi-transparent blue
                g.fillOval(ax - agentRadius - 4, ay - agentRadius - 4, (agentRadius+4)*2, (agentRadius+4)*2);

                // agent body
                g.setColor(new Color(21, 101, 192)); // deeper blue
                g.fillOval(ax - agentRadius, ay - agentRadius, agentRadius*2, agentRadius*2);

                // small center highlight
                g.setColor(Color.WHITE);
                g.fillOval(ax - 4, ay - 4, 8, 8);
            }
        }
    }
}
