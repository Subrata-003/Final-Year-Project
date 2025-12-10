import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * FreshHexCluster.java
 *
 * Perimeter adjacency + MST realization + redundancy (as before).
 * NEW FEATURE: clicking an active node blooms (highlights) all its adjacent active nodes.
 *
 * Compile:
 *   javac FreshHexCluster.java
 * Run:
 *   java FreshHexCluster
 */
public class FreshHexCluster {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClusterFrame::new);
    }
}

class ClusterFrame extends JFrame {
    final int hexCount = 20;
    final int gridCols = 5;
    final int gridRows = 4;
    final int hexRadius = 44;
    final int horizSpacing;
    final int vertSpacing;

    final List<Hex> hexes = new ArrayList<>();
    final List<Hole> holes = new ArrayList<>();
    final Map<String,Integer> holeIndexByXY = new HashMap<>();

    final Set<Integer> blueAgents = new HashSet<>();
    final Set<Integer> greenAgents = new HashSet<>();
    final List<int[]> realizedEdges = new ArrayList<>();

    // perimeter adjacency (vertex i connected to i+1 for each hex)
    Map<Integer, List<Integer>> holeAdj = new HashMap<>();

    // bloom state
    final Set<Integer> bloomedNodes = new HashSet<>();
    final int BLOOM_DURATION_MS = 1800;

    final Random rnd = new Random();
    final DrawPanel drawPanel;

    ClusterFrame() {
        super("Hex Cluster — Click-to-Bloom Active Neighbors");
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
        regen.setBackground(new Color(30, 144, 255));
        regen.setForeground(Color.WHITE);
        regen.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        regen.addActionListener(e -> {
            randomPlaceAgents(20);
            greenAgents.clear();
            realizedEdges.clear();
            bloomedNodes.clear();
            drawPanel.repaint();
        });

        JButton connect = new JButton("Connect Blues (MST + Redundancy)");
        connect.setFocusPainted(false);
        connect.setBackground(new Color(46, 204, 113));
        connect.setForeground(Color.WHITE);
        connect.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        connect.addActionListener(e -> {
            realizeMSTWithRedundancy();
            drawPanel.repaint();
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        top.setBackground(new Color(248, 249, 250));
        top.add(regen);
        top.add(connect);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(drawPanel), BorderLayout.CENTER);

        randomPlaceAgents(40);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ---------------- Build hex grid and holes ----------------
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
        // assign global indices for hex vertices
        for (Hex h : hexes) {
            h.vertexHoleIndices = new int[6];
            for (int v = 0; v < 6; v++) {
                Point p = h.vertex(v);
                h.vertexHoleIndices[v] = holeIndexByXY.get(p.x + "," + p.y);
            }
        }

        // build perimeter adjacency immediately
        buildPerimeterAdj();
    }

    void buildPerimeterAdj() {
        holeAdj.clear();
        for (int i = 0; i < holes.size(); i++) holeAdj.put(i, new ArrayList<>());
        for (Hex h : hexes) {
            int[] v = h.vertexHoleIndices;
            for (int i = 0; i < 6; i++) {
                int a = v[i], b = v[(i+1)%6];
                if (!holeAdj.get(a).contains(b)) holeAdj.get(a).add(b);
                if (!holeAdj.get(b).contains(a)) holeAdj.get(b).add(a);
            }
        }
    }

    void randomPlaceAgents(int k) {
        blueAgents.clear();
        if (holes.isEmpty()) return;
        k = Math.min(k, holes.size());
        while (blueAgents.size() < k) blueAgents.add(rnd.nextInt(holes.size()));
    }

    // ---------------- Main algorithm (unchanged core, plus uses holeAdj) ----------------
    void realizeMSTWithRedundancy() {
        greenAgents.clear();
        realizedEdges.clear();

        if (blueAgents.size() <= 1) {
            JOptionPane.showMessageDialog(this, "Not enough blues to connect.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // holeAdj already built by buildPerimeterAdj()

        // Step 1: Euclidean MST on blue terminals (Kruskal)
        List<Integer> bluesList = new ArrayList<>(blueAgents);
        int m = bluesList.size();
        List<MSEdge> allBlueEdges = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            for (int j = i+1; j < m; j++) {
                int u = bluesList.get(i), v = bluesList.get(j);
                double w = distance(holes.get(u).x, holes.get(u).y, holes.get(v).x, holes.get(v).y);
                allBlueEdges.add(new MSEdge(u, v, w));
            }
        }
        Collections.sort(allBlueEdges);
        UnionFind uf = new UnionFind(holes.size());
        List<MSEdge> mstEdges = new ArrayList<>();
        for (MSEdge e : allBlueEdges) {
            if (uf.find(e.u) != uf.find(e.v)) {
                uf.union(e.u, e.v);
                mstEdges.add(e);
                if (mstEdges.size() == m-1) break;
            }
        }

        // Step 2: Realize MST edges as shortest paths on holeAdj
        for (MSEdge e : mstEdges) {
            List<Integer> path = shortestPathOnHoles(e.u, e.v, holeAdj);
            if (path == null) continue;
            addPathAsGreensAndEdges(path);
        }

        // Step 3: Add redundancy (connect each blue to its two nearest blues)
        for (int u : bluesList) {
            PriorityQueue<MSEdge> pq = new PriorityQueue<>();
            for (int v : bluesList) if (v != u) {
                double w = distance(holes.get(u).x, holes.get(u).y, holes.get(v).x, holes.get(v).y);
                pq.add(new MSEdge(u, v, w));
            }
            int added = 0;
            while (!pq.isEmpty() && added < 2) {
                MSEdge cand = pq.poll();
                List<Integer> path = shortestPathOnHoles(cand.u, cand.v, holeAdj);
                if (path != null) {
                    addPathAsGreensAndEdges(path);
                    added++;
                }
            }
        }

        // Step 4: Ensure adjacent active holes have the perimeter edge realized.
        Set<Integer> active = new HashSet<>();
        active.addAll(blueAgents);
        active.addAll(greenAgents);
        for (int a : holeAdj.keySet()) {
            for (int b : holeAdj.get(a)) {
                if (a < b && active.contains(a) && active.contains(b)) {
                    realizedEdges.add(new int[]{a, b});
                }
            }
        }

        // Deduplicate realizedEdges
        Set<Long> seen = new HashSet<>();
        List<int[]> uniq = new ArrayList<>();
        for (int[] e : realizedEdges) {
            int a = Math.min(e[0], e[1]), b = Math.max(e[0], e[1]);
            long key = (((long)a) << 32) | (b & 0xffffffffL);
            if (!seen.contains(key)) {
                seen.add(key);
                uniq.add(new int[]{a, b});
            }
        }
        realizedEdges.clear();
        realizedEdges.addAll(uniq);

        JOptionPane.showMessageDialog(this,
                "Added " + greenAgents.size() + " green agents. Realized edges: " + realizedEdges.size(),
                "Result", JOptionPane.INFORMATION_MESSAGE);
    }

    void addPathAsGreensAndEdges(List<Integer> path) {
        for (int idx : path) if (!blueAgents.contains(idx)) greenAgents.add(idx);
        for (int i = 0; i + 1 < path.size(); i++) realizedEdges.add(new int[]{path.get(i), path.get(i+1)});
    }

    // Dijkstra on holeAdj with Euclidean weights
    List<Integer> shortestPathOnHoles(int src, int tgt, Map<Integer, List<Integer>> holeAdj) {
        final double INF = Double.POSITIVE_INFINITY;
        int n = holes.size();
        double[] dist = new double[n];
        int[] parent = new int[n];
        Arrays.fill(dist, INF);
        Arrays.fill(parent, -1);
        PriorityQueue<Pair> pq = new PriorityQueue<>();
        dist[src] = 0;
        pq.add(new Pair(src, 0));
        while (!pq.isEmpty()) {
            Pair p = pq.poll();
            int u = p.node;
            double dcur = p.dist;
            if (dcur > dist[u]) continue;
            if (u == tgt) break;
            for (int v : holeAdj.get(u)) {
                double w = distance(holes.get(u).x, holes.get(u).y, holes.get(v).x, holes.get(v).y);
                if (dist[v] > dist[u] + w) {
                    dist[v] = dist[u] + w;
                    parent[v] = u;
                    pq.add(new Pair(v, dist[v]));
                }
            }
        }
        if (src == tgt) return Collections.singletonList(src);
        if (parent[tgt] == -1) return null;
        LinkedList<Integer> path = new LinkedList<>();
        int cur = tgt;
        path.addFirst(cur);
        while (cur != src) {
            cur = parent[cur];
            if (cur == -1) return null;
            path.addFirst(cur);
        }
        return path;
    }

    // ---------------- Utilities & classes ----------------
    static double distance(int x1, int y1, int x2, int y2) {
        double dx = x1 - x2, dy = y1 - y2;
        return Math.hypot(dx, dy);
    }

    static class Hex {
        final int index, cx, cy, r;
        int[] vertexHoleIndices;
        Hex(int index, int cx, int cy, int r) {
            this.index = index; this.cx = cx; this.cy = cy; this.r = r;
        }
        Point vertex(int v) {
            double angle = Math.toRadians(60 * v);
            int x = cx + (int)Math.round(r * Math.cos(angle));
            int y = cy + (int)Math.round(r * Math.sin(angle));
            return new Point(x, y);
        }
    }

    static class Hole {
        final int hexIndex, vertexIndex, x, y;
        Hole(int hexIndex, int vertexIndex, int x, int y) {
            this.hexIndex = hexIndex; this.vertexIndex = vertexIndex; this.x = x; this.y = y;
        }
    }

    static class MSEdge implements Comparable<MSEdge> {
        final int u, v;
        final double w;
        MSEdge(int u, int v, double w) { this.u = u; this.v = v; this.w = w; }
        public int compareTo(MSEdge o) { return Double.compare(this.w, o.w); }
    }

    static class Pair implements Comparable<Pair> {
        final int node; final double dist;
        Pair(int node, double dist) { this.node = node; this.dist = dist; }
        public int compareTo(Pair o) { return Double.compare(this.dist, o.dist); }
    }

    static class UnionFind {
        int[] p;
        UnionFind(int n) { p = new int[n]; for (int i = 0; i < n; i++) p[i] = i; }
        int find(int x) { return p[x]==x?x:(p[x]=find(p[x])); }
        void union(int a, int b) { p[find(a)] = find(b); }
    }

    // ---------------- Drawing panel (with mouse click -> bloom) ----------------
    class DrawPanel extends JPanel {
        final int holeRadius = 5;
        final int agentRadius = 10;

        DrawPanel() {
            setBackground(new Color(250, 251, 253));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Point p = e.getPoint();
                    int clickedHole = findHoleAtPoint(p.x, p.y);
                    if (clickedHole == -1) return;
                    // only respond if clicked hole is active (blue or green)
                    if (!(blueAgents.contains(clickedHole) || greenAgents.contains(clickedHole))) return;
                    // compute immediate active neighbors
                    Set<Integer> neighborsToBloom = new HashSet<>();
                    neighborsToBloom.add(clickedHole); // also highlight clicked node briefly
                    List<Integer> neighbors = holeAdj.getOrDefault(clickedHole, Collections.emptyList());
                    for (int nb : neighbors) {
                        if (blueAgents.contains(nb) || greenAgents.contains(nb)) neighborsToBloom.add(nb);
                    }
                    // apply bloom
                    bloomedNodes.clear();
                    bloomedNodes.addAll(neighborsToBloom);
                    repaint();
                    // schedule clearance after BLOOM_DURATION_MS
                    javax.swing.Timer t = new javax.swing.Timer(BLOOM_DURATION_MS, ev -> {
                        bloomedNodes.clear();
                        repaint();
                        ((javax.swing.Timer) ev.getSource()).stop();
                    });
                    t.setRepeats(false);
                    t.start();
                }
            });
        }

        int findHoleAtPoint(int x, int y) {
            int thresh = 14; // click tolerance
            for (int i = 0; i < holes.size(); i++) {
                Hole h = holes.get(i);
                double d = distance(x, y, h.x, h.y);
                if (d <= thresh) return i;
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // draw perimeter edges lightly
            g.setColor(new Color(200, 210, 220, 80));
            g.setStroke(new BasicStroke(1f));
            for (Hex h : hexes) {
                int[] v = h.vertexHoleIndices;
                for (int i = 0; i < 6; i++) {
                    Hole a = holes.get(v[i]);
                    Hole b = holes.get(v[(i+1)%6]);
                    g.drawLine(a.x, a.y, b.x, b.y);
                }
            }

            // draw realized edges (thick green)
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(46, 125, 50, 220));
            for (int[] e : realizedEdges) {
                Hole a = holes.get(e[0]), b = holes.get(e[1]);
                g.drawLine(a.x, a.y, b.x, b.y);
            }

            // draw green agents (Steiner nodes)
            for (int gi : greenAgents) {
                Hole h = holes.get(gi);
                if (bloomedNodes.contains(gi)) {
                    // bloom color (bright)
                    g.setColor(new Color(102, 255, 178, 220));
                    g.fillOval(h.x - agentRadius, h.y - agentRadius, agentRadius*2+2, agentRadius*2+2);
                    g.setColor(new Color(0, 153, 77));
                    g.drawOval(h.x - agentRadius, h.y - agentRadius, agentRadius*2+2, agentRadius*2+2);
                } else {
                    g.setColor(new Color(56, 142, 60));
                    g.fillOval(h.x - agentRadius, h.y - agentRadius, agentRadius*2, agentRadius*2);
                    g.setColor(new Color(27, 94, 32));
                    g.drawOval(h.x - agentRadius, h.y - agentRadius, agentRadius*2, agentRadius*2);
                }
            }

            // draw blue agents (terminals)
            for (int bi : blueAgents) {
                Hole h = holes.get(bi);
                if (bloomedNodes.contains(bi)) {
                    // bloomed blue color
                    g.setColor(new Color(135, 206, 250, 220));
                    g.fillOval(h.x - agentRadius - 4, h.y - agentRadius - 4, (agentRadius+4)*2+2, (agentRadius+4)*2+2);
                    g.setColor(new Color(10, 90, 150));
                    g.drawOval(h.x - agentRadius - 4, h.y - agentRadius - 4, (agentRadius+4)*2+2, (agentRadius+4)*2+2);
                    g.setColor(Color.WHITE);
                    g.fillOval(h.x - 5, h.y - 5, 10, 10);
                } else {
                    g.setColor(new Color(30, 136, 229, 160));
                    g.fillOval(h.x - agentRadius - 4, h.y - agentRadius - 4, (agentRadius+4)*2, (agentRadius+4)*2);
                    g.setColor(new Color(21, 101, 192));
                    g.fillOval(h.x - agentRadius, h.y - agentRadius, agentRadius*2, agentRadius*2);
                    g.setColor(Color.WHITE);
                    g.fillOval(h.x - 4, h.y - 4, 8, 8);
                }
            }

            // optionally draw faint hole circles
            g.setColor(new Color(150, 160, 170, 80));
            for (Hole h : holes) g.drawOval(h.x - holeRadius, h.y - holeRadius, holeRadius*2, holeRadius*2);
        }
    }
}
