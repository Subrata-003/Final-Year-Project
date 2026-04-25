//import javax.swing.*;
//import javax.swing.Timer;
//import java.awt.*;
//import java.awt.event.*;
//import java.util.*;
//import java.util.List;
//import java.util.concurrent.*;
//
///**
// * HexClusterWithThread.java
// *
// * A truly distributed, multi-threaded implementation of the Adaptive Stimuli Algorithm.
// * Each active node runs on its own independent thread, communicating via concurrent inboxes.
// */
//public class HexClusterWithThread {
//    public static void main(String[] args) {
//        SwingUtilities.invokeLater(ClusterFrame::new);
//    }
//}
//
///* ===================== SIMULATION STATE ===================== */
//
//class Token {
//    int posIndex; // hole index
//    int hopsLeft;
//    int stimulusId;
//
//    Token(int pos, int hops, int sid) {
//        this.posIndex = pos;
//        this.hopsLeft = hops;
//        this.stimulusId = sid;
//    }
//}
//
///* ===================== THREADED AGENT NODE ===================== */
//
//class AgentNode implements Runnable {
//    final int id;
//    final int delayMs;
//    final double tokenGenProb;
//    final double walkProb;
//    final int tokenMaxHops;
//    final Random rnd = new Random();
//
//    volatile boolean running = true;
//    volatile boolean isStimulus = false;
//    volatile int stimulusId = -1;
//
//    // Thread-safe state tracking
//    final Set<Integer> awareFromStimuli = ConcurrentHashMap.newKeySet();
//    final Set<Integer> clearingStimuli = ConcurrentHashMap.newKeySet();
//
//    // Used strictly to keep the purple "clear" color visible for humans for 300ms
//    final Map<Integer, Long> visualClearing = new ConcurrentHashMap<>();
//
//    // Message passing inbox
//    final Queue<Token> inbox = new ConcurrentLinkedQueue<>();
//
//    // References to neighboring threads
//    final List<AgentNode> neighbors = new ArrayList<>();
//
//    AgentNode(int id, int delayMs, double tokenGenProb, double walkProb, int tokenMaxHops) {
//        this.id = id;
//        this.delayMs = delayMs;
//        this.tokenGenProb = tokenGenProb;
//        this.walkProb = walkProb;
//        this.tokenMaxHops = tokenMaxHops;
//    }
//
//    public boolean isAware() {
//        return !awareFromStimuli.isEmpty();
//    }
//
//    public boolean isVisuallyClearing() {
//        long now = System.currentTimeMillis();
//        visualClearing.entrySet().removeIf(e -> now > e.getValue());
//        return !visualClearing.isEmpty();
//    }
//
//    @Override
//    public void run() {
//        while (running) {
//            try {
//                /* ---- 1. CLEARING PROPAGATION ---- */
//                if (!clearingStimuli.isEmpty()) {
//                    Set<Integer> clearsToBroadcast = new HashSet<>(clearingStimuli);
//
//                    for (int sid : clearsToBroadcast) {
//                        // Push clear command to neighbors
//                        for (AgentNode nb : neighbors) {
//                            if (nb.awareFromStimuli.contains(sid)) {
//                                nb.clearingStimuli.add(sid);
//                                nb.visualClearing.put(sid, System.currentTimeMillis() + 300);
//                            }
//                        }
//
//                        // Erase tokens associated with this ID locally
//                        inbox.removeIf(t -> t.stimulusId == sid);
//
//                        // Remove states locally
//                        awareFromStimuli.remove(sid);
//                        clearingStimuli.remove(sid);
//                    }
//                }
//
//                /* ---- 2. TOKEN GENERATION ---- */
//                if (isStimulus && rnd.nextDouble() < tokenGenProb) {
//                    inbox.add(new Token(this.id, tokenMaxHops, stimulusId));
//                }
//
//                /* ---- 3. TOKEN PROCESSING (WALK/CONSUME) ---- */
//                int currentQueueSize = inbox.size();
//                for (int i = 0; i < currentQueueSize; i++) {
//                    Token t = inbox.poll();
//                    if (t == null) break;
//
//                    if (t.hopsLeft <= 0) continue; // Dead
//
//                    /* Probabilistic continuation of walk */
//                    if (rnd.nextDouble() >= walkProb) continue; // Dead
//
//                    // Absorb if this node is actively clearing this specific stimulus
//                    if (clearingStimuli.contains(t.stimulusId)) continue;
//
//                    // Consume token upon infecting an unaware node
//                    if (!awareFromStimuli.contains(t.stimulusId)) {
//                        awareFromStimuli.add(t.stimulusId);
//                        continue; // Token consumed, does not walk further
//                    }
//
//                    // Walk to random active neighbor
//                    if (!neighbors.isEmpty()) {
//                        t.hopsLeft--;
//                        AgentNode next = neighbors.get(rnd.nextInt(neighbors.size()));
//                        t.posIndex = next.id;
//                        next.inbox.add(t); // Pass to neighbor's thread
//                    }
//                }
//
//                // Simulate the local processing time / tick rate
//                Thread.sleep(delayMs);
//
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                break;
//            }
//        }
//    }
//}
//
///* ===================== MAIN APP FRAME ===================== */
//
//class ClusterFrame extends JFrame {
//    final int hexCount = 30;
//    final int gridCols = 5;
//    final int gridRows = 4;
//    final int hexRadius = 44;
//    final int horizSpacing;
//    final int vertSpacing;
//
//    final List<Hex> hexes = new ArrayList<>();
//    final List<Hole> holes = new ArrayList<>();
//    final Map<String, Integer> holeIndexByXY = new HashMap<>();
//
//    final Set<Integer> blueAgents = new HashSet<>();
//    final List<int[]> realizedEdges = new ArrayList<>();
//    Map<Integer, List<Integer>> holeAdj = new HashMap<>();
//
//    // --- Multi-Threading Variables ---
//    final Map<Integer, AgentNode> activeNodes = new ConcurrentHashMap<>();
//    final List<Thread> agentThreads = new ArrayList<>();
//    Timer renderTimer;
//
//    // --- Simulation Constants ---
//    final double tokenGenProb = 0.15;
//    final double walkProb = 1.00;
//    final int tokenMaxHops = 1200;
//    final int delayMs = 80;
//    int nextStimulusId = 1;
//
//    final Random rnd = new Random();
//    final DrawPanel drawPanel;
//
//    ClusterFrame() {
//        super("Hex Cluster — Multi-Threaded Adaptive Stimuli");
//        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//
//        horizSpacing = (int) Math.round(1.5 * hexRadius);
//        vertSpacing = (int) Math.round(Math.sqrt(3) * hexRadius);
//
//        buildHexGridAndHoles();
//
//        drawPanel = new DrawPanel();
//        int width = gridCols * horizSpacing + hexRadius * 2 + 120;
//        int height = gridRows * vertSpacing + hexRadius * 2 + 120;
//        drawPanel.setPreferredSize(new Dimension(width, height));
//
//        // --- Buttons ---
//        JButton regen = new JButton("Regenerate");
//        regen.setBackground(new Color(30, 144, 255));
//        regen.setForeground(Color.WHITE);
//        regen.setFocusPainted(false);
//        regen.addActionListener(e -> {
//            randomPlaceAgents(20);
//            realizedEdges.clear();
//            stopAllThreads();
//            drawPanel.repaint();
//        });
//
//        JButton connect = new JButton("Connect Blues");
//        connect.setBackground(new Color(46, 204, 113));
//        connect.setForeground(Color.WHITE);
//        connect.setFocusPainted(false);
//        connect.addActionListener(e -> {
//            realizeMSTWithRedundancy();
//            startAgentThreads(); // Spin up the threads!
//        });
//
//        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
//        top.setBackground(new Color(248, 249, 250));
//        top.add(regen);
//        top.add(connect);
//
//        add(top, BorderLayout.NORTH);
//        add(new JScrollPane(drawPanel), BorderLayout.CENTER);
//        add(new LegendPanel(), BorderLayout.SOUTH);
//
//        randomPlaceAgents(40);
//
//        // GUI Rendering Timer - Decoupled from simulation logic!
//        // Simply repaints the screen at ~30 FPS to show whatever the threads are doing.
//        renderTimer = new Timer(33, e -> drawPanel.repaint());
//        renderTimer.start();
//
//        pack();
//        setLocationRelativeTo(null);
//        setVisible(true);
//    }
//
//    /* ===================== THREAD MANAGEMENT ===================== */
//
//    void stopAllThreads() {
//        for (AgentNode node : activeNodes.values()) {
//            node.running = false;
//        }
//        for (Thread t : agentThreads) {
//            t.interrupt();
//        }
//        activeNodes.clear();
//        agentThreads.clear();
//    }
//
//    void startAgentThreads() {
//        stopAllThreads(); // Clean slate
//
//        // 1. Create a node object for every active blue agent
//        for (int idx : blueAgents) {
//            AgentNode node = new AgentNode(idx, delayMs, tokenGenProb, walkProb, tokenMaxHops);
//            activeNodes.put(idx, node);
//        }
//
//        // 2. Wire up their neighbor references physically
//        for (int idx : blueAgents) {
//            AgentNode node = activeNodes.get(idx);
//            for (int nbIdx : holeAdj.get(idx)) {
//                if (blueAgents.contains(nbIdx)) {
//                    node.neighbors.add(activeNodes.get(nbIdx));
//                }
//            }
//        }
//
//        // 3. Start all threads
//        for (AgentNode node : activeNodes.values()) {
//            Thread t = new Thread(node);
//            t.setDaemon(true); // Ensures threads close when app closes
//            agentThreads.add(t);
//            t.start();
//        }
//        drawPanel.repaint();
//    }
//
//    /* ===================== GRAPH BUILD & MST ===================== */
//
//    void buildHexGridAndHoles() {
//        hexes.clear();
//        holes.clear();
//        holeIndexByXY.clear();
//        int idx = 0;
//        for (int r = 0; r < gridRows; r++) {
//            for (int c = 0; c < gridCols; c++) {
//                if (idx >= hexCount) break;
//                int cx = 70 + c * horizSpacing;
//                int cy = 70 + r * vertSpacing + (c % 2 == 1 ? vertSpacing / 2 : 0);
//                Hex h = new Hex(idx, cx, cy, hexRadius);
//                hexes.add(h);
//                for (int v = 0; v < 6; v++) {
//                    Point p = h.vertex(v);
//                    String key = p.x + "," + p.y;
//                    if (!holeIndexByXY.containsKey(key)) {
//                        int holeIndex = holes.size();
//                        holes.add(new Hole(h.index, v, p.x, p.y));
//                        holeIndexByXY.put(key, holeIndex);
//                    }
//                }
//                idx++;
//            }
//        }
//        for (Hex h : hexes) {
//            h.vertexHoleIndices = new int[6];
//            for (int v = 0; v < 6; v++) {
//                Point p = h.vertex(v);
//                h.vertexHoleIndices[v] = holeIndexByXY.get(p.x + "," + p.y);
//            }
//        }
//        buildPerimeterAdj();
//    }
//
//    void buildPerimeterAdj() {
//        holeAdj.clear();
//        for (int i = 0; i < holes.size(); i++) holeAdj.put(i, new ArrayList<>());
//        for (Hex h : hexes) {
//            int[] v = h.vertexHoleIndices;
//            for (int i = 0; i < 6; i++) {
//                int a = v[i], b = v[(i + 1) % 6];
//                if (!holeAdj.get(a).contains(b)) holeAdj.get(a).add(b);
//                if (!holeAdj.get(b).contains(a)) holeAdj.get(b).add(a);
//            }
//        }
//    }
//
//    void randomPlaceAgents(int k) {
//        blueAgents.clear();
//        if (holes.isEmpty()) return;
//        k = Math.min(k, holes.size());
//        while (blueAgents.size() < k) blueAgents.add(rnd.nextInt(holes.size()));
//    }
//
//    void realizeMSTWithRedundancy() {
//        realizedEdges.clear();
//        if (blueAgents.size() <= 1) return;
//
//        List<Integer> bluesList = new ArrayList<>(blueAgents);
//        int m = bluesList.size();
//        List<MSEdge> allBlueEdges = new ArrayList<>();
//        for (int i = 0; i < m; i++) {
//            for (int j = i + 1; j < m; j++) {
//                int u = bluesList.get(i), v = bluesList.get(j);
//                double w = distance(holes.get(u).x, holes.get(u).y, holes.get(v).x, holes.get(v).y);
//                allBlueEdges.add(new MSEdge(u, v, w));
//            }
//        }
//        Collections.sort(allBlueEdges);
//        UnionFind uf = new UnionFind(holes.size());
//        List<MSEdge> mstEdges = new ArrayList<>();
//        for (MSEdge e : allBlueEdges) {
//            if (uf.find(e.u) != uf.find(e.v)) {
//                uf.union(e.u, e.v);
//                mstEdges.add(e);
//                if (mstEdges.size() == m - 1) break;
//            }
//        }
//
//        for (MSEdge e : mstEdges) {
//            List<Integer> path = shortestPathOnHoles(e.u, e.v, holeAdj);
//            if (path != null) addPathAsEdges(path);
//        }
//
//        for (int u : bluesList) {
//            PriorityQueue<MSEdge> pq = new PriorityQueue<>();
//            for (int v : bluesList)
//                if (v != u) {
//                    double w = distance(holes.get(u).x, holes.get(u).y, holes.get(v).x, holes.get(v).y);
//                    pq.add(new MSEdge(u, v, w));
//                }
//            int added = 0;
//            while (!pq.isEmpty() && added < 2) {
//                MSEdge cand = pq.poll();
//                List<Integer> path = shortestPathOnHoles(cand.u, cand.v, holeAdj);
//                if (path != null) {
//                    addPathAsEdges(path);
//                    added++;
//                }
//            }
//        }
//
//        for (int a : holeAdj.keySet()) {
//            for (int b : holeAdj.get(a)) {
//                if (a < b && blueAgents.contains(a) && blueAgents.contains(b)) {
//                    realizedEdges.add(new int[]{a, b});
//                }
//            }
//        }
//
//        Set<Long> seen = new HashSet<>();
//        List<int[]> uniq = new ArrayList<>();
//        for (int[] e : realizedEdges) {
//            int a = Math.min(e[0], e[1]), b = Math.max(e[0], e[1]);
//            long key = (((long) a) << 32) | (b & 0xffffffffL);
//            if (!seen.contains(key)) {
//                seen.add(key);
//                uniq.add(new int[]{a, b});
//            }
//        }
//        realizedEdges.clear();
//        realizedEdges.addAll(uniq);
//    }
//
//    void addPathAsEdges(List<Integer> path) {
//        for (int idx : path) blueAgents.add(idx);
//        for (int i = 0; i + 1 < path.size(); i++) realizedEdges.add(new int[]{path.get(i), path.get(i + 1)});
//    }
//
//    List<Integer> shortestPathOnHoles(int src, int tgt, Map<Integer, List<Integer>> holeAdj) {
//        final double INF = Double.POSITIVE_INFINITY;
//        int n = holes.size();
//        double[] dist = new double[n];
//        int[] parent = new int[n];
//        Arrays.fill(dist, INF);
//        Arrays.fill(parent, -1);
//        PriorityQueue<Pair> pq = new PriorityQueue<>();
//        dist[src] = 0;
//        pq.add(new Pair(src, 0));
//        while (!pq.isEmpty()) {
//            Pair p = pq.poll();
//            int u = p.node;
//            if (p.dist > dist[u]) continue;
//            if (u == tgt) break;
//            for (int v : holeAdj.get(u)) {
//                double w = distance(holes.get(u).x, holes.get(u).y, holes.get(v).x, holes.get(v).y);
//                if (dist[v] > dist[u] + w) {
//                    dist[v] = dist[u] + w;
//                    parent[v] = u;
//                    pq.add(new Pair(v, dist[v]));
//                }
//            }
//        }
//        if (src == tgt) return Collections.singletonList(src);
//        if (parent[tgt] == -1) return null;
//        LinkedList<Integer> path = new LinkedList<>();
//        int cur = tgt;
//        path.addFirst(cur);
//        while (cur != src) {
//            cur = parent[cur];
//            if (cur == -1) return null;
//            path.addFirst(cur);
//        }
//        return path;
//    }
//
//    static double distance(int x1, int y1, int x2, int y2) {
//        return Math.hypot(x1 - x2, y1 - y2);
//    }
//
//    static class Hex {
//        final int index, cx, cy, r;
//        int[] vertexHoleIndices;
//
//        Hex(int index, int cx, int cy, int r) {
//            this.index = index; this.cx = cx; this.cy = cy; this.r = r;
//        }
//
//        Point vertex(int v) {
//            double angle = Math.toRadians(60 * v);
//            return new Point(cx + (int) Math.round(r * Math.cos(angle)), cy + (int) Math.round(r * Math.sin(angle)));
//        }
//    }
//
//    static class Hole {
//        final int hexIndex, vertexIndex, x, y;
//        Hole(int hexIndex, int vertexIndex, int x, int y) {
//            this.hexIndex = hexIndex; this.vertexIndex = vertexIndex; this.x = x; this.y = y;
//        }
//    }
//
//    static class MSEdge implements Comparable<MSEdge> {
//        final int u, v; final double w;
//        MSEdge(int u, int v, double w) { this.u = u; this.v = v; this.w = w; }
//        public int compareTo(MSEdge o) { return Double.compare(this.w, o.w); }
//    }
//
//    static class Pair implements Comparable<Pair> {
//        final int node; final double dist;
//        Pair(int node, double dist) { this.node = node; this.dist = dist; }
//        public int compareTo(Pair o) { return Double.compare(this.dist, o.dist); }
//    }
//
//    static class UnionFind {
//        int[] p;
//        UnionFind(int n) { p = new int[n]; for (int i = 0; i < n; i++) p[i] = i; }
//        int find(int x) { return p[x] == x ? x : (p[x] = find(p[x])); }
//        void union(int a, int b) { p[find(a)] = find(b); }
//    }
//
//    /* ===================== DRAWING ===================== */
//
//    class DrawPanel extends JPanel {
//        final int holeRadius = 5;
//        final int agentRadius = 10;
//
//        DrawPanel() {
//            setBackground(new Color(250, 251, 253));
//            addMouseListener(new MouseAdapter() {
//                @Override
//                public void mouseClicked(MouseEvent e) {
//                    Point p = e.getPoint();
//                    int clickedHole = findHoleAtPoint(p.x, p.y);
//                    if (clickedHole == -1) return;
//
//                    // Only interact if the threads have been spun up
//                    if (!activeNodes.containsKey(clickedHole)) return;
//
//                    AgentNode n = activeNodes.get(clickedHole);
//
//                    if (!n.isStimulus) {
//                        n.isStimulus = true;
//                        n.stimulusId = nextStimulusId++;
//                        n.awareFromStimuli.add(n.stimulusId);
//                    } else {
//                        int sid = n.stimulusId;
//                        n.isStimulus = false;
//                        n.clearingStimuli.add(sid);
//                        n.visualClearing.put(sid, System.currentTimeMillis() + 300);
//                    }
//                    repaint();
//                }
//            });
//        }
//
//        int findHoleAtPoint(int x, int y) {
//            int thresh = 14;
//            for (int i = 0; i < holes.size(); i++) {
//                Hole h = holes.get(i);
//                if (distance(x, y, h.x, h.y) <= thresh) return i;
//            }
//            return -1;
//        }
//
//        @Override
//        protected void paintComponent(Graphics g0) {
//            super.paintComponent(g0);
//            Graphics2D g = (Graphics2D) g0;
//            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//
//            // Perimeter edges
//            g.setColor(new Color(200, 210, 220, 80));
//            g.setStroke(new BasicStroke(1f));
//            for (Hex h : hexes) {
//                int[] v = h.vertexHoleIndices;
//                for (int i = 0; i < 6; i++) {
//                    Hole a = holes.get(v[i]);
//                    Hole b = holes.get(v[(i + 1) % 6]);
//                    g.drawLine(a.x, a.y, b.x, b.y);
//                }
//            }
//
//            // Realized edges
//            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
//            g.setColor(new Color(46, 125, 50, 220));
//            for (int[] e : realizedEdges) {
//                Hole a = holes.get(e[0]), b = holes.get(e[1]);
//                g.drawLine(a.x, a.y, b.x, b.y);
//            }
//
//            // Draw Nodes, Thread States & Tokens
//            for (int i = 0; i < holes.size(); i++) {
//                Hole h = holes.get(i);
//
//                // 1. Draw faint unassigned holes
//                if (!blueAgents.contains(i)) {
//                    g.setColor(new Color(150, 160, 170, 80));
//                    g.drawOval(h.x - holeRadius, h.y - holeRadius, holeRadius * 2, holeRadius * 2);
//                    continue; // Skip the rest, it's an empty hole
//                }
//
//                // 2. Draw Halos (ONLY if the thread is currently active)
//                if (activeNodes.containsKey(i)) {
//                    AgentNode n = activeNodes.get(i);
//                    if (n.isStimulus) {
//                        g.setColor(new Color(60, 170, 60, 160)); // Active Source Green
//                        g.fillOval(h.x - 20, h.y - 20, 40, 40);
//                    } else if (n.isVisuallyClearing()) {
//                        g.setColor(new Color(160, 130, 210, 160)); // Clearing Wave Purple
//                        g.fillOval(h.x - 18, h.y - 18, 36, 36);
//                    } else if (n.isAware()) {
//                        g.setColor(new Color(255, 160, 160, 160)); // Aware Pink
//                        g.fillOval(h.x - 18, h.y - 18, 36, 36);
//                    }
//                }
//
//                // 3. ALWAYS draw Blue agent center (so they are visible before connection)
//                g.setColor(new Color(30, 136, 229, 200));
//                g.fillOval(h.x - agentRadius - 4, h.y - agentRadius - 4, (agentRadius + 4) * 2, (agentRadius + 4) * 2);
//                g.setColor(new Color(21, 101, 192));
//                g.fillOval(h.x - agentRadius, h.y - agentRadius, agentRadius * 2, agentRadius * 2);
//                g.setColor(Color.WHITE);
//                g.fillOval(h.x - 4, h.y - 4, 8, 8);
//
//                // 4. Draw tokens (ONLY if the thread is currently active)
//                if (activeNodes.containsKey(i)) {
//                    AgentNode n = activeNodes.get(i);
//                    g.setColor(new Color(220, 20, 20));
//                    for (Token t : n.inbox) {
//                        int ox = rnd.nextInt(13) - 6; // Jitter to see clustered tokens
//                        int oy = rnd.nextInt(13) - 6;
//                        g.fillOval(h.x + ox - 3, h.y + oy - 3, 6, 6);
//                    }
//                }
//            }
//        }
//    }
//
//    /* color legend */
//    class LegendPanel extends JPanel {
//        LegendPanel() {
//            setLayout(new FlowLayout());
//            addLegend(new Color(150, 160, 170), "Inactive Node");
//            addLegend(new Color(30, 136, 229), "Active Threaded Node");
//            addLegend(new Color(60, 170, 60, 160), "Active Stimulus");
//            addLegend(new Color(255, 160, 160, 160), "Aware");
//            addLegend(new Color(160, 130, 210, 160), "All Clear Wave");
//            addLegend(Color.RED, "Token");
//        }
//
//        void addLegend(Color c, String text) {
//            JPanel box = new JPanel();
//            box.setBackground(c);
//            box.setPreferredSize(new Dimension(15, 15));
//            add(box);
//            add(new JLabel(text));
//        }
//    }
//}