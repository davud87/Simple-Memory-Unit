package memoryapp;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class MemoryUnitGUI extends JFrame {

    private MemoryLogic memoryLogic;

    // UI controls
    private JTextField addressBitsField;
    private JTextField dataBitsField;
    private JCheckBox writeEnableCheck;
    private JCheckBox memoryEnableCheck;
    private JLabel dataOutLabel;
    private JButton writeButton, readButton, clockButton, resetButton;

    // Status display
    private JLabel statusLabel;
    private JLabel clockCountLabel;

    // History monitor
    private JTextArea historyArea;
    private JButton clearHistoryButton;
    private List<String> historyList = new ArrayList<>();

    // Memory state save/load
    private JButton saveStateButton, loadStateButton;

    // Panels
    private JPanel registerPanel;
    private CircuitPanel circuitPanel;

    // Tooltip manager to show longer descriptions
    private ToolTipManager tooltipManager;

    public MemoryUnitGUI() {
        super("Memory Circuit Simulator (8 Registers, 3-bit Address)");
        setSize(1300, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        memoryLogic = new MemoryLogic();

        // Configure tooltips to show longer and stay visible longer
        tooltipManager = ToolTipManager.sharedInstance();
        tooltipManager.setInitialDelay(300);
        tooltipManager.setDismissDelay(15000);  // 15 seconds

        setupTopPanel();
        setupCenterPanel();
        setupBottomPanel();
        
        // Start with a help dialog
        showHelp();
        
        updateLogicFromUI();
    }

    private void setupTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("Controls"));

        // Main controls panel
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        
        controlsPanel.add(new JLabel("Address (3 bits):"));
        addressBitsField = new JTextField("000", 4);
        addressBitsField.setToolTipText("Enter a 3-bit binary address (000-111) to select one of 8 registers");
        controlsPanel.add(addressBitsField);

        controlsPanel.add(new JLabel("Data (4 bits):"));
        dataBitsField = new JTextField("0000", 5);
        dataBitsField.setToolTipText("Enter a 4-bit binary value (0000-1111) to write to the selected register");
        controlsPanel.add(dataBitsField);

        memoryEnableCheck = new JCheckBox("Memory Enable");
        memoryEnableCheck.setSelected(true);
        memoryEnableCheck.setToolTipText("Enable/disable the entire memory unit. When disabled, no operations will work.");
        controlsPanel.add(memoryEnableCheck);

        writeEnableCheck = new JCheckBox("Write Enable");
        writeEnableCheck.setToolTipText("Enable write operations. Must be checked to write data to memory.");
        controlsPanel.add(writeEnableCheck);

        writeButton = new JButton("Write");
        writeButton.setEnabled(false);
        writeButton.setToolTipText("Queue a write operation (will execute on next clock pulse if Write Enable is on)");
        controlsPanel.add(writeButton);
        
        readButton = new JButton("Read");
        readButton.setToolTipText("Read data from the currently selected register");
        controlsPanel.add(readButton);
        
        clockButton = new JButton("Pulse Clock");
        clockButton.setToolTipText("Trigger a clock pulse to execute queued write operations");
        controlsPanel.add(clockButton);
        
        resetButton = new JButton("Reset");
        resetButton.setToolTipText("Clear all registers to 0000");
        controlsPanel.add(resetButton);

        controlsPanel.add(new JLabel("DataOut:"));
        dataOutLabel = new JLabel("----");
        dataOutLabel.setPreferredSize(new Dimension(60, 30));
        dataOutLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        dataOutLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dataOutLabel.setToolTipText("Shows the data read from memory");
        controlsPanel.add(dataOutLabel);

        topPanel.add(controlsPanel, BorderLayout.CENTER);
        
        // Status panel at the bottom of the top panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        statusPanel.setBorder(BorderFactory.createTitledBorder("Status"));
        
        statusLabel = new JLabel("Ready");
        statusLabel.setPreferredSize(new Dimension(600, 20));
        statusPanel.add(statusLabel);
        
        clockCountLabel = new JLabel("Clock Pulses: 0");
        statusPanel.add(clockCountLabel);
        
        saveStateButton = new JButton("Save State");
        saveStateButton.setToolTipText("Save the current memory state to a file");
        saveStateButton.addActionListener(e -> saveMemoryState());
        statusPanel.add(saveStateButton);
        
        loadStateButton = new JButton("Load State");
        loadStateButton.setToolTipText("Load a previously saved memory state");
        loadStateButton.addActionListener(e -> loadMemoryState());
        statusPanel.add(loadStateButton);
        
        JButton helpButton = new JButton("Help");
        helpButton.setToolTipText("Show help information");
        helpButton.addActionListener(e -> showHelp());
        statusPanel.add(helpButton);
        
        topPanel.add(statusPanel, BorderLayout.SOUTH);
        
        add(topPanel, BorderLayout.NORTH);

        // Listeners to update logic on changes
        KeyAdapter updateListener = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                updateLogicFromUI();
            }
        };
        addressBitsField.addKeyListener(updateListener);
        dataBitsField.addKeyListener(updateListener);
        
        writeEnableCheck.addItemListener(e -> {
            boolean checked = (e.getStateChange() == ItemEvent.SELECTED);
            writeButton.setEnabled(checked && memoryEnableCheck.isSelected());
            updateLogicFromUI();
        });
        
        memoryEnableCheck.addItemListener(e -> {
            boolean enabled = (e.getStateChange() == ItemEvent.SELECTED);
            writeButton.setEnabled(enabled && writeEnableCheck.isSelected());
            updateLogicFromUI();
            
            if (enabled) {
                statusLabel.setText("Memory enabled");
            } else {
                statusLabel.setText("Memory disabled - all operations blocked");
            }
        });

        // Button actions
        writeButton.addActionListener(e -> {
            boolean success = memoryLogic.requestWrite();
            if (success) {
                JOptionPane.showMessageDialog(this,
                        "Write request queued.\nIt will execute on Pulse Clock if Write Enable is active.",
                        "Write Request", JOptionPane.INFORMATION_MESSAGE);
                historyList.add("Write requested: Address=" + addressBitsField.getText() + 
                                ", Data=" + dataBitsField.getText());
            } else {
                JOptionPane.showMessageDialog(this,
                        "Write request failed: " + memoryLogic.getLastOperation(),
                        "Write Request Failed", JOptionPane.ERROR_MESSAGE);
                historyList.add("Failed write request: " + memoryLogic.getLastOperation());
            }
            updateHistoryText();
            updateStatusDisplay();
            circuitPanel.repaint();
        });
        
        readButton.addActionListener(e -> {
            boolean success = memoryLogic.readFromMemory();
            String outVal = memoryLogic.getDataOutBits();
            dataOutLabel.setText(outVal);
            
            if (success) {
                historyList.add("Read " + outVal + " from address " + addressBitsField.getText());
            } else {
                historyList.add("Failed read: " + memoryLogic.getLastOperation());
            }
            
            updateHistoryText();
            updateStatusDisplay();
            circuitPanel.repaint();
        });
        
        clockButton.addActionListener(e -> {
            boolean writeExecuted = memoryLogic.onClockPulse();
            updateRegisterPanel();
            updateStatusDisplay();
            flashCircuitPanel();
            
            if (writeExecuted) {
                JOptionPane.showMessageDialog(this,
                        "Clock pulse triggered! Write executed successfully.",
                        "Clock Pulse", JOptionPane.INFORMATION_MESSAGE);
                historyList.add("Clock pulse: Write executed to address " + 
                                addressBitsField.getText() + " with data " + dataBitsField.getText());
            } else {
                JOptionPane.showMessageDialog(this,
                        "Clock pulse triggered! " + 
                        (memoryLogic.hasPendingWrite() ? "No write executed (Write Enable off or Memory disabled)" : 
                                                         "No pending write operations."),
                        "Clock Pulse", JOptionPane.INFORMATION_MESSAGE);
                historyList.add("Clock pulse: " + memoryLogic.getLastOperation());
            }
            
            updateHistoryText();
            circuitPanel.repaint();
        });
        
        resetButton.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, 
                    "Are you sure you want to reset all memory registers to 0?",
                    "Confirm Reset", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                memoryLogic.resetMemory();
                updateRegisterPanel();
                updateStatusDisplay();
                historyList.add("Memory reset: All registers cleared");
                updateHistoryText();
                circuitPanel.repaint();
            }
        });
    }

    private void setupCenterPanel() {
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerSplit.setResizeWeight(0.8);
        add(centerSplit, BorderLayout.CENTER);

        circuitPanel = new CircuitPanel();
        circuitPanel.setBorder(BorderFactory.createTitledBorder("Circuit Diagram"));
        centerSplit.setLeftComponent(circuitPanel);

        JPanel historyPanel = new JPanel(new BorderLayout(5,5));
        historyPanel.setBorder(BorderFactory.createTitledBorder("History Monitor"));
        historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(historyArea);
        historyPanel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel historyButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        clearHistoryButton = new JButton("Clear History");
        clearHistoryButton.addActionListener(e -> {
            historyList.clear();
            updateHistoryText();
        });
        historyButtonPanel.add(clearHistoryButton);
        
        JButton saveHistoryButton = new JButton("Save History");
        saveHistoryButton.addActionListener(e -> saveHistoryLog());
        historyButtonPanel.add(saveHistoryButton);
        
        historyPanel.add(historyButtonPanel, BorderLayout.SOUTH);
        centerSplit.setRightComponent(historyPanel);
    }

    private void setupBottomPanel() {
        registerPanel = new JPanel(new GridLayout(2, 4, 10, 10));
        TitledBorder border = BorderFactory.createTitledBorder("Register States (4-bit)");
        border.setTitleFont(new Font("SansSerif", Font.BOLD, 14));
        registerPanel.setBorder(border);
        
        for (int i = 0; i < 8; i++) {
            JLabel regLabel = new JLabel("Reg" + i + ": 0000", SwingConstants.CENTER);
            regLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
            regLabel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            regLabel.setToolTipText("Register " + i + " (Address: " + 
                                   String.format("%3s", Integer.toBinaryString(i)).replace(' ', '0') + ")");
            registerPanel.add(regLabel);
        }
        
        add(registerPanel, BorderLayout.SOUTH);
    }
    
    private void showHelp() {
        String helpContent = 
            "<html><body style='width: 400px; padding: 10px;'>" +
            "<h2>Memory Circuit Simulator Help</h2>" +
            "<p>This application simulates a simple RAM memory unit with 8 registers (R0-R7), " +
            "each storing 4 bits of data. The memory is addressed using 3 bits (000-111).</p>" +
            
            "<h3>Basic Operations:</h3>" +
            "<ul>" +
            "<li><b>Address Field</b>: Enter a 3-bit binary value (000-111) to select a register</li>" +
            "<li><b>Data Field</b>: Enter a 4-bit binary value (0000-1111) to be written</li>" +
            "<li><b>Memory Enable</b>: Master enable/disable for the entire memory unit</li>" +
            "<li><b>Write Enable</b>: Must be checked to allow write operations</li>" +
            "<li><b>Write Button</b>: Queues a write operation (executes on clock pulse)</li>" +
            "<li><b>Read Button</b>: Reads data from the selected register</li>" +
            "<li><b>Pulse Clock</b>: Triggers a clock cycle, executing any queued writes</li>" +
            "<li><b>Reset</b>: Clears all registers to 0000</li>" +
            "</ul>" +
            
            "<h3>Memory Operation Flow:</h3>" +
            "<ol>" +
            "<li>Set the address and data values</li>" +
            "<li>Enable Memory and Write Enable if writing</li>" +
            "<li>Click Write to queue a write operation</li>" +
            "<li>Click Pulse Clock to execute the write</li>" +
            "<li>Use Read to see the contents of any register</li>" +
            "</ol>" +
            
            "<h3>Additional Features:</h3>" +
            "<ul>" +
            "<li>Save/Load Memory State to preserve your work</li>" +
            "<li>Save History Log to export the operation history</li>" +
            "<li>Status display shows operation results and error messages</li>" +
            "</ul>" +
            "</body></html>";
        
        JOptionPane.showMessageDialog(this, 
                new JLabel(helpContent), 
                "Memory Circuit Simulator Help", 
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void saveMemoryState() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Memory State");
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(".mem")) {
                file = new File(file.getAbsolutePath() + ".mem");
            }
            
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                int[] registers = memoryLogic.getRegisters();
                oos.writeObject(registers);
                
                statusLabel.setText("Memory state saved to " + file.getName());
                historyList.add("Memory state saved to file: " + file.getName());
                updateHistoryText();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, 
                        "Error saving memory state: " + e.getMessage(),
                        "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void loadMemoryState() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Load Memory State");
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                int[] registers = (int[]) ois.readObject();
                
                for (int i = 0; i < registers.length && i < memoryLogic.getRegisters().length; i++) {
                    memoryLogic.getRegisters()[i] = registers[i];
                }
                
                updateRegisterPanel();
                updateStatusDisplay();
                circuitPanel.repaint();
                
                statusLabel.setText("Memory state loaded from " + file.getName());
                historyList.add("Memory state loaded from file: " + file.getName());
                updateHistoryText();
            } catch (IOException | ClassNotFoundException e) {
                JOptionPane.showMessageDialog(this, 
                        "Error loading memory state: " + e.getMessage(),
                        "Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void saveHistoryLog() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save History Log");
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(".txt")) {
                file = new File(file.getAbsolutePath() + ".txt");
            }
            
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("Memory Circuit Simulator - Operation History");
                writer.println("----------------------------------------");
                for (String line : historyList) {
                    writer.println(line);
                }
                
                statusLabel.setText("History log saved to " + file.getName());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, 
                        "Error saving history log: " + e.getMessage(),
                        "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void updateLogicFromUI() {
        memoryLogic.setAddressBits(addressBitsField.getText().trim());
        memoryLogic.setDataBits(dataBitsField.getText().trim());
        memoryLogic.setWriteEnable(writeEnableCheck.isSelected());
        memoryLogic.setMemoryEnabled(memoryEnableCheck.isSelected());
        memoryLogic.resetDataOut();
        updateRegisterPanel();
        updateStatusDisplay();
        circuitPanel.repaint();
    }

    private void updateRegisterPanel() {
        Component[] comps = registerPanel.getComponents();
        for (int i = 0; i < comps.length; i++) {
            if (comps[i] instanceof JLabel) {
                JLabel label = (JLabel) comps[i];
                String value = memoryLogic.getRegisterBits(i);
                label.setText("Reg" + i + ": " + value);
                
                if (!value.equals("0000")) {
                    label.setForeground(Color.BLUE);
                } else {
                    label.setForeground(Color.BLACK);
                }
                
                if (i == memoryLogic.getCurrentAddress()) {
                    label.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                } else {
                    label.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
                }
            }
        }
    }

    private void updateHistoryText() {
        StringBuilder sb = new StringBuilder();
        for (String line : historyList) {
            sb.append(line).append("\n");
        }
        historyArea.setText(sb.toString());
        historyArea.setCaretPosition(historyArea.getDocument().getLength());
    }
    
    private void updateStatusDisplay() {
        statusLabel.setText(memoryLogic.getLastOperation());
        if (memoryLogic.isLastOperationSuccess()) {
            statusLabel.setForeground(Color.BLACK);
        } else {
            statusLabel.setForeground(Color.RED);
        }
        
        clockCountLabel.setText("Clock Pulses: " + memoryLogic.getClockPulseCount());
        dataOutLabel.setText(memoryLogic.getDataOutBits());
    }

    private void flashCircuitPanel() {
        Color original = circuitPanel.getBackground();
        circuitPanel.setBackground(Color.CYAN);
        Timer timer = new Timer(200, e -> {
            circuitPanel.setBackground(original);
            circuitPanel.repaint();
        });
        timer.setRepeats(false);
        timer.start();
    }

    private class CircuitPanel extends JPanel {
        public CircuitPanel() {
            setPreferredSize(new Dimension(1100, 700));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int offsetX = 150;
            int offsetY = 80;

            int addr = memoryLogic.getCurrentAddress();
            boolean we = memoryLogic.isWriteEnable();
            boolean me = memoryLogic.isMemoryEnabled();
            boolean pendingWrite = memoryLogic.hasPendingWrite();

            // --- Draw Address Box ---
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString("Address (3 bits)", offsetX, offsetY - 10);
            int boxW = 80, boxH = 40;
            g2.drawRect(offsetX, offsetY, boxW, boxH);
            g2.drawString(addressBitsField.getText(), offsetX + 20, offsetY + 25);

            // --- Draw DataIn Box ---
            g2.drawString("DataIn (4 bits)", offsetX, offsetY + 70);
            int dataBoxY = offsetY + 80;
            g2.drawRect(offsetX, dataBoxY, boxW, boxH);
            g2.drawString(dataBitsField.getText(), offsetX + 20, dataBoxY + 25);

            // --- Draw Clock Signal ---
            int clockX = offsetX - 100;
            int clockY = offsetY + 150;
            g2.drawString("Clock", clockX, clockY - 10);
            
            int waveWidth = 80;
            int waveHeight = 20;
            g2.drawLine(clockX, clockY, clockX + waveWidth/4, clockY);
            g2.drawLine(clockX + waveWidth/4, clockY, clockX + waveWidth/4, clockY - waveHeight);
            g2.drawLine(clockX + waveWidth/4, clockY - waveHeight, clockX + waveWidth/2, clockY - waveHeight);
            g2.drawLine(clockX + waveWidth/2, clockY - waveHeight, clockX + waveWidth/2, clockY);
            g2.drawLine(clockX + waveWidth/2, clockY, clockX + 3*waveWidth/4, clockY);
            g2.drawLine(clockX + 3*waveWidth/4, clockY, clockX + 3*waveWidth/4, clockY - waveHeight);
            g2.drawLine(clockX + 3*waveWidth/4, clockY - waveHeight, clockX + waveWidth, clockY - waveHeight);
            g2.drawLine(clockX + waveWidth, clockY - waveHeight, clockX + waveWidth, clockY);
            
            g2.drawLine(clockX + waveWidth, clockY, clockX + waveWidth + 30, clockY);
            g2.drawString("pulse →", clockX + waveWidth + 5, clockY - 5);

            // --- Draw Decoder (3->8) ---
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString("Decoder (3->8)", offsetX + 150, offsetY - 10);
            int decX = offsetX + 140;
            int decY = offsetY;
            int decW = 70, decH = 300;
            g2.drawRect(decX, decY, decW, decH);
            
            g2.drawLine(offsetX + boxW, offsetY + boxH/2, decX, decY + 20);

            if (pendingWrite) {
                g2.setColor(Color.ORANGE);
                g2.drawString("WRITE PENDING", decX - 30, decY - 60);
                g2.drawLine(decX + decW/2, decY - 50, decX + decW/2, decY);
                g2.setColor(Color.BLACK);
            }

            // --- Draw AND Gates and Registers ---
            int gateStartX = decX + decW + 50;
            int gateStartY = decY + 30;
            int gateSize = 25;
            int gateGap = 50;
            for (int i = 0; i < 8; i++) {
                if (i == addr) {
                    g2.setColor(we && me ? Color.RED : Color.GREEN);
                } else {
                    g2.setColor(Color.BLACK);
                }
                int decOutX = decX + decW;
                int decOutY = decY + 20 + i * (decH / 8);
                int gateX = gateStartX;
                int gateY = gateStartY + i * gateGap;
                g2.drawLine(decOutX, decOutY, gateX, gateY + gateSize/2);

                g2.drawArc(gateX, gateY, gateSize, gateSize, 270, 180);
                g2.drawLine(gateX, gateY, gateX, gateY + gateSize);

                // --- Draw Registers ---
                int regStartX = gateStartX + gateSize;
                int regW = 80, regH = 30;
                
                int gateOutX = gateStartX + gateSize;
                int gateOutY = gateStartY + i * gateGap + gateSize/2;
                int regX = regStartX;
                int regY = gateStartY + i * gateGap;
                g2.drawLine(gateOutX, gateOutY, regX, regY + regH/2);
                
                if (i == addr) {
                    g2.setColor(we && me ? new Color(255, 200, 200) : new Color(200, 255, 200));
                } else {
                    g2.setColor(Color.LIGHT_GRAY);
                }
                g2.fillRect(regX, regY, regW, regH);
                g2.setColor(Color.BLACK);
                g2.drawRect(regX, regY, regW, regH);
                
                String regValue = memoryLogic.getRegisterBits(i);
                g2.drawString("R" + i + ": " + regValue, regX + 10, regY + 20);
            }

            // --- Draw MUX (8->1) ---
            int muxX = gateStartX + gateSize + 60 + 30;
            int muxY = decY + 30;
            int muxW = 60, muxH = 320;
            g2.setColor(Color.BLACK);
            g2.drawString("MUX (8->1)", muxX, muxY - 10);
            g2.drawRect(muxX, muxY, muxW, muxH);
            
            g2.drawLine(offsetX + boxW/2, offsetY + boxH, offsetX + boxW/2, muxY - 30);
            g2.drawLine(offsetX + boxW/2, muxY - 30, muxX + muxW/2, muxY - 30);
            g2.drawLine(muxX + muxW/2, muxY - 30, muxX + muxW/2, muxY);
            g2.drawString("Select", muxX + muxW/2 - 20, muxY - 35);

            for (int i = 0; i < 8; i++) {
                int regX = gateStartX + gateSize + 60;
                int regY = gateStartY + i * gateGap + 25/2;
                int muxInX = muxX;
                int muxInY = muxY + 15 + i * (muxH / 8);
                if (i == addr && me && !memoryLogic.getDataOutBits().equals("----")) {
                    g2.setColor(Color.BLUE);
                } else {
                    g2.setColor(Color.BLACK);
                }
                g2.drawLine(regX, regY, muxInX, muxInY);
            }

            // --- Draw DataOut Monitor ---
            int monX = muxX + muxW + 40;
            int monY = muxY + muxH/2 - 20;
            int monW = 80, monH = 40;
            g2.setColor(Color.MAGENTA);
            g2.drawString("DataOut Monitor", monX, monY - 5);
            g2.drawLine(muxX + muxW, muxY + muxH/2, monX, monY + monH/2);
            g2.drawRect(monX, monY, monW, monH);
            g2.setColor(Color.GRAY);
            g2.fillRect(monX + 1, monY + 1, monW - 2, monH - 2);
            g2.setColor(Color.BLACK);
            g2.drawString(memoryLogic.getDataOutBits(), monX + 20, monY + 25);
            
            // --- Draw Memory Status Indicator ---
            int statusX = gateStartX + gateSize + 60;
            int statusY = gateStartY + 8 * gateGap + 10;
            g2.setColor(Color.BLACK);
            g2.drawString("Memory Status:", statusX, statusY);
            
            if (pendingWrite && we && me) {
                g2.setColor(Color.RED);
                g2.drawString("WRITE PENDING → Address: " + addressBitsField.getText() + 
                             ", Data: " + dataBitsField.getText(), statusX + 120, statusY);
            } else if (!memoryLogic.getDataOutBits().equals("----") && me) {
                g2.setColor(Color.BLUE);
                g2.drawString("READ ACTIVE → Address: " + addressBitsField.getText() + 
                             ", Data: " + memoryLogic.getDataOutBits(), statusX + 120, statusY);
            } else {
                g2.setColor(me ? Color.GREEN : Color.RED);
                g2.drawString(me ? "READY" : "DISABLED", statusX + 120, statusY);
            }

            // --- Draw Memory Enable and Write Enable Controls ---
            int controlX = 20;
            int controlY = getHeight() - 100;

            // Memory Enable control
            g2.setColor(Color.BLACK);
            g2.drawString("Memory Enable", controlX, controlY);
            g2.drawRect(controlX, controlY + 10, 100, 30);
            g2.setColor(me ? Color.GREEN : Color.RED);
            g2.fillRect(controlX + 1, controlY + 11, 99, 29);
            g2.setColor(Color.BLACK);
            g2.drawString(me ? "ENABLED" : "DISABLED", controlX + 25, controlY + 30);

            // Write Enable control
            controlY += 50;
            g2.drawString("Write Enable", controlX, controlY);
            g2.drawRect(controlX, controlY + 10, 100, 30);
            g2.setColor(we ? Color.GREEN : Color.RED);
            g2.fillRect(controlX + 1, controlY + 11, 99, 29);
            g2.setColor(Color.BLACK);
            g2.drawString(we ? "ON" : "OFF", controlX + 35, controlY + 30);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> {
            MemoryUnitGUI gui = new MemoryUnitGUI();
            gui.setVisible(true);
        });
    }
}