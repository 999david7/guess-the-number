import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Random;

public class Main {

    // ── Palette ─────────────────────────────────────────────────────────────
    static final Color C_BG      = new Color(0x08080F);
    static final Color C_CARD    = new Color(0x10101C);
    static final Color C_BORDER  = new Color(0x1E1E36);
    static final Color C_ACCENT  = new Color(0x6EE7F7);
    static final Color C_ACCENT2 = new Color(0xB06EF7);
    static final Color C_SUCCESS = new Color(0x39D98A);
    static final Color C_DANGER  = new Color(0xFF5572);
    static final Color C_WARN    = new Color(0xFFB836);
    static final Color C_TEXT    = new Color(0xECECFF);
    static final Color C_MUTED   = new Color(0x52527A);
    static final Color C_DIM     = new Color(0x1A1A2E);

    // ── State ────────────────────────────────────────────────────────────────
    private int targetNumber, attempts, maxAttempts = 5;
    private long startTime;
    private int bestScore = Integer.MAX_VALUE;
    private boolean gameOver = false;

    // ── UI ───────────────────────────────────────────────────────────────────
    private JFrame frame;
    private JPanel contentPane;
    private NumberInput numberInput;
    private FeedbackDisplay feedbackDisplay;
    private LivesBar livesBar;
    private HistoryList historyList;
    private JLabel timerLbl, bestLbl;
    private Timer uiTimer;
    private int bgOffset = 0;
    private float glowPhase = 0f;

    public Main() {
        applyUIDefaults();
        chooseDifficulty();
        chooseGameMode();
        buildUI();
        startTimers();
    }

    private void applyUIDefaults() {
        UIManager.put("OptionPane.background",        C_CARD);
        UIManager.put("Panel.background",             C_CARD);
        UIManager.put("OptionPane.messageForeground", C_TEXT);
        UIManager.put("Button.background",            C_DIM);
        UIManager.put("Button.foreground",            C_TEXT);
        UIManager.put("RadioButton.background",       C_CARD);
        UIManager.put("RadioButton.foreground",       C_TEXT);
        UIManager.put("Spinner.background",           C_DIM);
        UIManager.put("TextField.background",         C_DIM);
        UIManager.put("TextField.foreground",         C_TEXT);
        UIManager.put("TextField.caretForeground",    C_ACCENT);
        UIManager.put("Label.foreground",             C_TEXT);
    }

    // ════════════════════════════════════════════════════════════════════════
    private void chooseDifficulty() {
        JPanel p = dialogPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        addDialogTitle(p, "SELECT DIFFICULTY");

        JRadioButton[] opts = {
                radioBtn("EASY     — 10 attempts", false),
                radioBtn("NORMAL   —  5 attempts", true),
                radioBtn("HARD     —  3 attempts", false),
                radioBtn("CUSTOM   —  set your own", false)
        };
        ButtonGroup bg = new ButtonGroup();
        for (JRadioButton o : opts) { bg.add(o); p.add(o); p.add(vgap(4)); }

        p.add(vgap(8));
        JLabel customLbl = new JLabel("Custom attempt count (1 – 99):");
        customLbl.setFont(mono(12)); customLbl.setForeground(C_MUTED);
        customLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        SpinnerNumberModel spinModel = new SpinnerNumberModel(7, 1, 99, 1);
        JSpinner spinner = new JSpinner(spinModel);
        styleSpinner(spinner);
        spinner.setEnabled(false);
        spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        spinner.setAlignmentX(Component.LEFT_ALIGNMENT);

        opts[3].addActionListener(e -> spinner.setEnabled(true));
        for (int i = 0; i < 3; i++) opts[i].addActionListener(e -> spinner.setEnabled(false));
        p.add(customLbl); p.add(vgap(4)); p.add(spinner);

        int r = JOptionPane.showConfirmDialog(null, p, "Number Guesser",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) System.exit(0);

        if      (opts[0].isSelected()) maxAttempts = 10;
        else if (opts[2].isSelected()) maxAttempts = 3;
        else if (opts[3].isSelected()) maxAttempts = (int) spinner.getValue();
        else                           maxAttempts = 5;
    }

    private void chooseGameMode() {
        JPanel p = dialogPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        addDialogTitle(p, "GAME MODE");

        JRadioButton rand   = radioBtn("RANDOM   — computer picks (1–100)", true);
        JRadioButton friend = radioBtn("VERSUS   — a friend sets the number", false);
        ButtonGroup bg = new ButtonGroup(); bg.add(rand); bg.add(friend);
        p.add(rand); p.add(vgap(4)); p.add(friend);

        int r = JOptionPane.showConfirmDialog(null, p, "Number Guesser",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) System.exit(0);

        if (friend.isSelected()) {
            while (true) {
                try {
                    String s = JOptionPane.showInputDialog(null,
                            "Friend, enter a secret number (1–100):", "Number Guesser", JOptionPane.PLAIN_MESSAGE);
                    if (s == null) System.exit(0);
                    targetNumber = Integer.parseInt(s.trim());
                    if (targetNumber >= 1 && targetNumber <= 100) break;
                } catch (Exception ignored) {}
            }
        } else {
            targetNumber = new Random().nextInt(100) + 1;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    private void buildUI() {
        frame = new JFrame();
        frame.setUndecorated(true);
        frame.setSize(460, 680);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        contentPane = new JPanel(null) {
            @Override protected void paintComponent(Graphics g2) {
                Graphics2D g = (Graphics2D) g2;
                g.setColor(C_BG);
                g.fillRect(0, 0, getWidth(), getHeight());
                float phase = (bgOffset % 360) / 360f;
                float cx = (float)(getWidth() * (0.3 + 0.4 * Math.sin(phase * Math.PI * 2)));
                RadialGradientPaint rg = new RadialGradientPaint(cx, 120, 260,
                        new float[]{0f, 1f},
                        new Color[]{new Color(110,231,247,22), new Color(0,0,0,0)});
                g.setPaint(rg); g.fillRect(0, 0, getWidth(), getHeight());
                RadialGradientPaint rg2 = new RadialGradientPaint(
                        getWidth()*0.75f, getHeight()*0.85f, 200,
                        new float[]{0f, 1f},
                        new Color[]{new Color(176,110,247,18), new Color(0,0,0,0)});
                g.setPaint(rg2); g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(C_BORDER);
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 20, 20);
            }
        };

        // ── Title bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.setBounds(0, 0, 460, 48);

        JLabel titleLbl2 = new JLabel("  NUMBER GUESSER");
        titleLbl2.setFont(mono(13, true));
        titleLbl2.setForeground(C_MUTED);

        JButton closeBtn = iconButton("✕", C_DANGER);
        closeBtn.addActionListener(e -> System.exit(0));
        JButton minBtn = iconButton("−", C_MUTED);
        minBtn.addActionListener(e -> frame.setExtendedState(JFrame.ICONIFIED));

        JPanel winBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 10));
        winBtns.setOpaque(false);
        winBtns.add(minBtn); winBtns.add(closeBtn);

        titleBar.add(titleLbl2, BorderLayout.WEST);
        titleBar.add(winBtns,   BorderLayout.EAST);

        MouseAdapter drag = new MouseAdapter() {
            Point start;
            public void mousePressed(MouseEvent e)  { start = e.getPoint(); }
            public void mouseDragged(MouseEvent e)  {
                Point loc = frame.getLocation();
                frame.setLocation(loc.x + e.getX() - start.x, loc.y + e.getY() - start.y);
            }
        };
        titleBar.addMouseListener(drag);
        titleBar.addMouseMotionListener(drag);
        contentPane.add(titleBar);

        // ── Stats row
        timerLbl = statLabel("0s");
        timerLbl.setBounds(20, 52, 120, 22);
        bestLbl = statLabel("");
        bestLbl.setHorizontalAlignment(SwingConstants.RIGHT);
        bestLbl.setBounds(320, 52, 120, 22);
        contentPane.add(timerLbl);
        contentPane.add(bestLbl);

        // ── Feedback
        feedbackDisplay = new FeedbackDisplay();
        feedbackDisplay.setBounds(20, 82, 420, 140);
        contentPane.add(feedbackDisplay);

        // ── Lives bar
        livesBar = new LivesBar(maxAttempts);
        livesBar.setBounds(20, 228, 420, 30);
        contentPane.add(livesBar);

        // ── Input
        numberInput = new NumberInput();
        numberInput.setBounds(20, 274, 420, 72);
        numberInput.onSubmit = this::checkGuess;
        contentPane.add(numberInput);

        // ── Guess button
        NeonButton guessBtn = new NeonButton("GUESS");
        guessBtn.setBounds(20, 358, 420, 52);
        guessBtn.addActionListener(e -> checkGuess());
        contentPane.add(guessBtn);

        // ── History label
        JLabel hLbl = new JLabel("HISTORY");
        hLbl.setFont(mono(10, true));
        hLbl.setForeground(C_MUTED);
        hLbl.setBounds(20, 426, 100, 16);
        contentPane.add(hLbl);

        historyList = new HistoryList();
        historyList.setBounds(20, 448, 420, 210);
        contentPane.add(historyList);

        frame.setContentPane(contentPane);
        frame.setVisible(true);
        SwingUtilities.invokeLater(numberInput::focus);
    }

    private void startTimers() {
        startTime = System.currentTimeMillis();
        uiTimer = new Timer(33, e -> {
            long s = (System.currentTimeMillis() - startTime) / 1000;
            timerLbl.setText(s + "s");
            bgOffset++;
            glowPhase += 0.02f;
            feedbackDisplay.tick(glowPhase);
            contentPane.repaint();
        });
        uiTimer.start();
    }

    // ════════════════════════════════════════════════════════════════════════
    private void checkGuess() {
        if (gameOver) return;
        String raw = numberInput.getText().trim();
        try {
            int guess = Integer.parseInt(raw);
            if (guess < 1 || guess > 100) {
                feedbackDisplay.set("1 to 100 only", C_WARN, "");
                return;
            }
            attempts++;
            int cmp = Integer.compare(guess, targetNumber);
            historyList.addEntry(attempts, guess, cmp);
            livesBar.setUsed(attempts);

            if (cmp == 0) {
                feedbackDisplay.set("CORRECT!", C_SUCCESS, "You nailed it");
                updateBest();
                gameOver = true; uiTimer.stop();
                delay(900, () -> endGame(true));
            } else if (attempts >= maxAttempts) {
                feedbackDisplay.set("" + targetNumber, C_DANGER, "was the number");
                gameOver = true; uiTimer.stop();
                delay(1100, () -> endGame(false));
            } else {
                feedbackDisplay.set(cmp < 0 ? "TOO LOW  ↑" : "TOO HIGH  ↓",
                        cmp < 0 ? C_ACCENT : C_ACCENT2,
                        (maxAttempts - attempts) + " attempts left");
            }
            numberInput.clear();
        } catch (NumberFormatException ex) {
            feedbackDisplay.set("NUMBERS ONLY", C_WARN, "enter a digit 1–100");
        }
    }

    private void updateBest() {
        if (attempts < bestScore) { bestScore = attempts; bestLbl.setText("BEST  " + bestScore); }
    }

    private void endGame(boolean won) {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        JPanel p = dialogPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        addDialogTitle(p, won ? "✦  VICTORY" : "✗  GAME OVER");
        String msg = won
                ? "Guessed in " + attempts + " attempt" + (attempts==1?"":"s") + "  ·  " + elapsed + "s"
                : "The number was " + targetNumber;
        JLabel sub = new JLabel(msg);
        sub.setFont(mono(13)); sub.setForeground(C_MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(sub); p.add(vgap(12));
        JLabel q = new JLabel("Play again?");
        q.setFont(mono(13)); q.setForeground(C_TEXT);
        q.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(q);

        int r = JOptionPane.showConfirmDialog(frame, p, "Number Guesser",
                JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r == JOptionPane.YES_OPTION) { frame.dispose(); new Main(); }
        else System.exit(0);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Custom Swing components
    // ════════════════════════════════════════════════════════════════════════

    static class FeedbackDisplay extends JComponent {
        String big = "Type a number", small = "1 – 100";
        Color bigColor = new Color(0x52527A);
        float phase = 0;
        void set(String b, Color c, String s) { big = b; bigColor = c; small = s; repaint(); }
        void tick(float p) { phase = p; }
        @Override protected void paintComponent(Graphics g2) {
            Graphics2D g = (Graphics2D) g2;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(C_CARD);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 18, 18));
            float alpha = (float)(0.35 + 0.25 * Math.sin(phase * Math.PI * 2));
            g.setColor(new Color(bigColor.getRed(), bigColor.getGreen(), bigColor.getBlue(),
                    Math.min(255, (int)(alpha*255))));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new RoundRectangle2D.Float(0.75f, 0.75f, getWidth()-1.5f, getHeight()-1.5f, 18, 18));
            g.setFont(mono(11));
            g.setColor(C_MUTED);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(small, (getWidth()-fm.stringWidth(small))/2, getHeight()-18);
            g.setFont(mono(30, true));
            fm = g.getFontMetrics();
            // glow
            g.setColor(new Color(bigColor.getRed(), bigColor.getGreen(), bigColor.getBlue(), 55));
            for (int dx=-3; dx<=3; dx++) for (int dy=-3; dy<=3; dy++)
                g.drawString(big, (getWidth()-fm.stringWidth(big))/2+dx, getHeight()/2+12+dy);
            g.setColor(bigColor);
            g.drawString(big, (getWidth()-fm.stringWidth(big))/2, getHeight()/2+12);
        }
    }

    static class LivesBar extends JComponent {
        int total, used = 0;
        LivesBar(int t) { total = t; }
        void setUsed(int u) { used = u; repaint(); }
        @Override protected void paintComponent(Graphics g2) {
            Graphics2D g = (Graphics2D) g2;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int n = Math.min(total, 20);
            float segW = (getWidth() - (n-1)*4f) / n;
            for (int i = 0; i < n; i++) {
                float x = i*(segW+4);
                boolean spent = i < used;
                Color fill = !spent ? C_DIM
                        : (double)used/total < 0.5 ? C_ACCENT
                          : (double)used/total < 0.8 ? C_WARN : C_DANGER;
                g.setColor(fill);
                g.fill(new RoundRectangle2D.Float(x, 10, segW, 10, 5, 5));
                if (!spent) {
                    g.setColor(C_BORDER); g.setStroke(new BasicStroke(1f));
                    g.draw(new RoundRectangle2D.Float(x, 10, segW, 10, 5, 5));
                }
            }
            g.setFont(mono(10)); g.setColor(C_MUTED);
            String lbl = used + " / " + total;
            g.drawString(lbl, getWidth()-g.getFontMetrics().stringWidth(lbl), 9);
        }
    }

    static class NumberInput extends JComponent {
        Runnable onSubmit;
        private final JTextField field;
        NumberInput() {
            setLayout(null);
            field = new JTextField();
            field.setFont(mono(32, true));
            field.setHorizontalAlignment(JTextField.CENTER);
            field.setBackground(new Color(0,0,0,0));
            field.setForeground(C_TEXT);
            field.setCaretColor(C_ACCENT);
            field.setBorder(BorderFactory.createEmptyBorder());
            field.setOpaque(false);
            field.addActionListener(e -> { if (onSubmit != null) onSubmit.run(); });
            add(field);
        }
        @Override public void setBounds(int x, int y, int w, int h) {
            super.setBounds(x, y, w, h);
            field.setBounds(12, 8, w-24, h-16);
        }
        String getText()  { return field.getText(); }
        void clear()      { field.setText(""); field.requestFocus(); }
        void focus()      { field.requestFocus(); }
        @Override protected void paintComponent(Graphics g2) {
            Graphics2D g = (Graphics2D) g2;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(C_CARD);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
            g.setColor(C_BORDER); g.setStroke(new BasicStroke(1.5f));
            g.draw(new RoundRectangle2D.Float(0.75f, 0.75f, getWidth()-1.5f, getHeight()-1.5f, 16, 16));
        }
    }

    static class NeonButton extends JButton {
        boolean hover = false;
        NeonButton(String t) {
            super(t);
            setFont(mono(14, true));
            setForeground(Color.WHITE);
            setFocusPainted(false); setBorderPainted(false); setContentAreaFilled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover=true;  repaint(); }
                public void mouseExited (MouseEvent e) { hover=false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g2) {
            Graphics2D g = (Graphics2D) g2;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color a = hover ? new Color(0x8CF0FF) : C_ACCENT;
            Color b = hover ? new Color(0xC48AFF) : C_ACCENT2;
            g.setPaint(new GradientPaint(0, 0, a, getWidth(), 0, b));
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
            if (hover) {
                for (int i=6; i>=1; i--) {
                    g.setColor(new Color(110,231,247,10));
                    g.setStroke(new BasicStroke(i*2f));
                    g.draw(new RoundRectangle2D.Float(0,0,getWidth(),getHeight(),14,14));
                }
            }
            g.dispose();
            super.paintComponent(g2);
        }
    }

    static class HistoryList extends JComponent {
        JScrollPane scroll;
        JPanel inner;
        HistoryList() {
            setLayout(new BorderLayout());
            inner = new JPanel();
            inner.setBackground(C_CARD);
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
            inner.setBorder(BorderFactory.createEmptyBorder(6,10,6,10));
            scroll = new JScrollPane(inner,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBackground(C_CARD);
            scroll.getViewport().setBackground(C_CARD);
            scroll.setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
            add(scroll);
        }
        @Override public void setBounds(int x,int y,int w,int h) {
            super.setBounds(x,y,w,h);
            if (scroll != null) scroll.setBounds(0,0,w,h);
        }
        void addEntry(int attempt, int guess, int cmp) {
            JPanel row = new JPanel(new BorderLayout(8,0)) {
                @Override protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.setColor(C_BORDER);
                    g.drawLine(0,getHeight()-1,getWidth(),getHeight()-1);
                }
            };
            row.setBackground(C_CARD);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            row.setBorder(BorderFactory.createEmptyBorder(6,0,6,0));

            JLabel numLbl = new JLabel("#"+attempt+"   "+String.format("%3d",guess));
            numLbl.setFont(mono(13,true));
            numLbl.setForeground(cmp==0 ? C_SUCCESS : C_TEXT);

            String hint = cmp==0 ? "✓  correct" : cmp<0 ? "↑  too low" : "↓  too high";
            Color hcol  = cmp==0 ? C_SUCCESS    : cmp<0 ? C_ACCENT     : C_ACCENT2;
            JLabel h2 = new JLabel(hint, SwingConstants.RIGHT);
            h2.setFont(mono(12)); h2.setForeground(hcol);

            row.add(numLbl, BorderLayout.WEST);
            row.add(h2,     BorderLayout.EAST);
            inner.add(row);
            inner.revalidate();
            SwingUtilities.invokeLater(() -> {
                JScrollBar sb = scroll.getVerticalScrollBar();
                sb.setValue(sb.getMaximum());
            });
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    static Font mono(int s)          { return new Font("Monospaced", Font.PLAIN, s); }
    static Font mono(int s, boolean b){ return new Font("Monospaced", b?Font.BOLD:Font.PLAIN, s); }

    static JPanel dialogPanel() {
        JPanel p = new JPanel();
        p.setBackground(C_CARD);
        p.setBorder(BorderFactory.createEmptyBorder(14,18,14,18));
        return p;
    }

    static void addDialogTitle(JPanel p, String text) {
        JLabel l = new JLabel(text);
        l.setFont(mono(15,true)); l.setForeground(C_ACCENT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0,0,12,0));
        p.add(l);
    }

    static JRadioButton radioBtn(String text, boolean sel) {
        JRadioButton r = new JRadioButton(text, sel);
        r.setFont(mono(13)); r.setForeground(C_TEXT); r.setBackground(C_CARD);
        r.setFocusPainted(false); r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }

    static JLabel statLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(mono(12,true)); l.setForeground(C_MUTED);
        return l;
    }

    static JButton iconButton(String sym, Color color) {
        JButton b = new JButton(sym) {
            boolean h = false;
            { addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e){h=true; repaint();}
                public void mouseExited (MouseEvent e){h=false;repaint();}
            });}
            @Override protected void paintComponent(Graphics g2) {
                Graphics2D g=(Graphics2D)g2;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                if(h){ g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),40));
                    g.fillOval(0,0,getWidth(),getHeight()); }
                g.setColor(h?color:C_MUTED); g.setFont(getFont());
                FontMetrics fm=g.getFontMetrics();
                g.drawString(getText(),(getWidth()-fm.stringWidth(getText()))/2,
                        (getHeight()+fm.getAscent()-fm.getDescent())/2);
            }
        };
        b.setFont(mono(14,true));
        b.setPreferredSize(new Dimension(28,28));
        b.setFocusPainted(false); b.setBorderPainted(false); b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    static void styleSpinner(JSpinner s) {
        s.setFont(mono(13));
        JTextField tf = ((JSpinner.DefaultEditor)s.getEditor()).getTextField();
        tf.setBackground(C_DIM); tf.setForeground(C_TEXT); tf.setCaretColor(C_ACCENT);
        tf.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
    }

    static Component vgap(int h) { return Box.createRigidArea(new Dimension(0,h)); }

    static void delay(int ms, Runnable r) {
        Timer t = new Timer(ms, e -> r.run()); t.setRepeats(false); t.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::new);
    }
}