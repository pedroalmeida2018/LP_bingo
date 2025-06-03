import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

public class BingoClient extends JFrame {
    private final JTextField nameField;
    private final JButton connectButton, readyButton, lineButton, bingoButton;
    private final JLabel statusLabel, cardIdLabel;
    private final JPanel cardPanel, drawnNumbersPanel;
    private final JButton[] cardButtons = new JButton[25];
    private final String cardId;
    private final List<Integer> drawnNumbersList = new ArrayList<>();
    private final Set<Integer> drawnNumbers = new HashSet<>();
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;
    private final Map<Integer, Integer> numberToButtonIndex = new HashMap<>(); // Mapeia número -> posição no cartão
    private final Map<Integer, JButton> buttonMap = new HashMap<>(); // Mapeia número -> botão

    public BingoClient() {
        setTitle("Cliente Bingo ESTGA");
        setSize(900, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Top panel with name and connect button
        JPanel topPanel = new JPanel(new BorderLayout());
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        nameField = new JTextField(15);
        connectButton = new JButton("Conectar");
        namePanel.add(new JLabel("Nome: "));
        namePanel.add(nameField);
        namePanel.add(connectButton);
        topPanel.add(namePanel, BorderLayout.WEST);
        cardId = UUID.randomUUID().toString().substring(0, 8);
        cardIdLabel = new JLabel("Card ID: " + cardId);
        JPanel idPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        idPanel.add(cardIdLabel);
        topPanel.add(idPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Card panel
        cardPanel = new JPanel(new GridLayout(5, 5, 5, 5));
        cardPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        for (int i = 0; i < 25; i++) {
            JButton cell = new JButton("--");
            cell.setFont(new Font("Arial", Font.PLAIN, 20));
            cell.setPreferredSize(new Dimension(60, 60));
            cell.setEnabled(false);
            int finalI = i; // Para usar no ActionListener
            cell.addActionListener(e -> {
                if (drawnNumbers.contains(Integer.parseInt(cell.getText()))) {
                    cell.setBackground(Color.GREEN);
                    cell.setEnabled(false);
                }
            });
            cardButtons[i] = cell;
            cardPanel.add(cell);
        }
        add(cardPanel, BorderLayout.CENTER);

        // Bottom panel with game buttons and status
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonsPanel = new JPanel();
        readyButton = new JButton("Pronto para iniciar");
        lineButton = new JButton("Linha");
        bingoButton = new JButton("Bingo");
        readyButton.setEnabled(false);
        lineButton.setEnabled(false);
        bingoButton.setEnabled(false);
        buttonsPanel.add(readyButton);
        buttonsPanel.add(lineButton);
        buttonsPanel.add(bingoButton);
        statusLabel = new JLabel("Digite seu nome e conecte-se ao servidor");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        bottomPanel.add(buttonsPanel, BorderLayout.NORTH);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        // Panel for drawn numbers
        drawnNumbersPanel = new JPanel();
        drawnNumbersPanel.setLayout(new BoxLayout(drawnNumbersPanel, BoxLayout.Y_AXIS));
        drawnNumbersPanel.setBorder(BorderFactory.createTitledBorder("Números Sorteados"));
        JScrollPane scrollPane = new JScrollPane(drawnNumbersPanel);
        scrollPane.setPreferredSize(new Dimension(150, 0));
        add(scrollPane, BorderLayout.EAST);

        setupListeners();
        setVisible(true);
    }

    private void setupListeners() {
        nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateName(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateName(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { validateName(); }
            private void validateName() {
                connectButton.setEnabled(!nameField.getText().trim().isEmpty());
            }
        });

        connectButton.addActionListener(_ignore -> {
            if (!connected) {
                try {
                    socket = new Socket("localhost", 12345);
                    out = new PrintWriter(socket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    connected = true;
                    System.out.println("Conectado ao servidor com sucesso!");
                    
                    out.println("LOGIN;" + nameField.getText() + ";" + cardId);
                    statusLabel.setText("Conectado! Aguardando cartão...");
                    connectButton.setEnabled(false);
                    nameField.setEnabled(false);
                    
                    new Thread(this::receiveMessages).start();
                } catch (IOException ex) {
                    System.out.println("Erro ao conectar ao servidor: " + ex.getMessage());
                    statusLabel.setText("Erro ao conectar ao servidor!");
                }
            }
        });

        readyButton.addActionListener(_ignore -> {
            readyButton.setEnabled(false);
            if (out != null) {
                out.println("PRONTO;" + cardId);
                statusLabel.setText("Aguardando outros jogadores...");
            }
        });

        lineButton.addActionListener(_ignore -> {
            if (out != null) {
                out.println("LINHA;" + cardId);
                System.out.println("Enviado LINHA para o servidor");
            }
        });

        bingoButton.addActionListener(_ignore -> {
            if (out != null && hasCompletedCard()) {
                out.println("BINGO;" + cardId);
                System.out.println("Enviado BINGO para o servidor");
            } else {
                statusLabel.setText("Você precisa marcar todos os números sorteados antes de fazer Bingo!");
            }
        });
    }

    private void receiveMessages() {
        try {
            String resposta;
            while ((resposta = in.readLine()) != null) {
                System.out.println("Resposta recebida do servidor: " + resposta);
                
                if (resposta.startsWith("CARTAO;")) {
                    String[] parts = resposta.split(";", 3);
                    String[] rows = parts[2].split(";");
                    System.out.println("Cartão recebido: " + parts[2]);
                    int[][] cardNums = new int[5][5];
                    for (int i = 0; i < 5; i++) {
                        String[] nums = rows[i].split(",");
                        for (int j = 0; j < 5; j++) {
                            try {
                                cardNums[i][j] = Integer.parseInt(nums[j].trim());
                            } catch (NumberFormatException e) {
                                System.out.println("Erro ao converter número na posição [" + i + "][" + j + "]: " + nums[j]);
                            }
                        }
                    }
                    SwingUtilities.invokeLater(() -> {
                        gerarCartaoComNumeros(cardNums);
                        readyButton.setEnabled(true);
                        statusLabel.setText("Cartão recebido! Clique em 'Pronto para iniciar' quando estiver pronto.");
                    });
                } else if (resposta.startsWith("AGUARDE;")) {
                    String msg = resposta.substring(8);
                    SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
                } else if (resposta.startsWith("INICIAR;")) {
                    String msg = resposta.substring(8);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText(msg);
                        System.out.println("Jogo iniciado!");
                        readyButton.setVisible(false); // Esconde o botão pronto
                        lineButton.setEnabled(true);
                        bingoButton.setEnabled(true);
                    });
                } else if (resposta.startsWith("NUMERO;")) {
                    String numStr = resposta.substring(7);
                    try {
                        int numero = Integer.parseInt(numStr.trim());
                        System.out.println("Número sorteado recebido: " + numero);                        SwingUtilities.invokeLater(() -> {
                            addDrawnNumber(numero);
                        });
                    } catch (NumberFormatException ex) {
                        System.out.println("Erro ao converter número sorteado: " + numStr);
                    }                } else if (resposta.startsWith("LINHA_VALIDA;")) {
                    String jogadorLinha = resposta.substring(12);
                    SwingUtilities.invokeLater(() -> 
                        statusLabel.setText("Linha feita pelo utilizador " + jogadorLinha)
                    );                } else if (resposta.startsWith("BINGO_VALIDA;")) {
                    String jogadorBingo = resposta.substring(12);
                    String meuNome = nameField.getText().trim();
                    System.out.println("Jogador que fez bingo: " + jogadorBingo);
                    System.out.println("Meu nome: " + meuNome);
                    SwingUtilities.invokeLater(() -> {
                        if (jogadorBingo.trim().equals(meuNome)) {
                            statusLabel.setText("Parabéns!");
                        } else {
                            statusLabel.setText("Ainda não foi desta. Tente novamente.");
                        }
                        // Desabilita os botões após o fim do jogo
                        lineButton.setEnabled(false);
                        bingoButton.setEnabled(false);
                        
                        // Desabilita todos os números no cartão
                        for (JButton button : cardButtons) {
                            button.setEnabled(false);
                        }
                    });
                } else if (resposta.startsWith("BINGO_INVALIDO;")) {
                    String msg = resposta.substring(14);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText(msg);
                        bingoButton.setEnabled(true); // Reabilita o botão para tentar novamente
                    });
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao receber mensagem do servidor: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Conexão com o servidor perdida!");
                connectButton.setEnabled(true);
                nameField.setEnabled(true);
                readyButton.setEnabled(false);
                lineButton.setEnabled(false);
                bingoButton.setEnabled(false);
            });
        }
    }

    private void gerarCartaoComNumeros(int[][] nums) {
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                int num = nums[i][j];
                int index = i * 5 + j;
                cardButtons[index].setText(String.valueOf(num));
                cardButtons[index].setEnabled(false);
                numberToButtonIndex.put(num, index);
                buttonMap.put(num, cardButtons[index]);
            }
        }
    }

    private void addDrawnNumber(int numero) {
        drawnNumbers.add(numero);
        drawnNumbersList.add(numero);
        JLabel numLabel = new JLabel(String.valueOf(numero));
        numLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        numLabel.setFont(new Font("Arial", Font.BOLD, 16));
        SwingUtilities.invokeLater(() -> {
            drawnNumbersPanel.add(numLabel);
            drawnNumbersPanel.add(Box.createVerticalStrut(5));
            drawnNumbersPanel.revalidate();
            drawnNumbersPanel.repaint();
            
            // Habilita o botão se o número estiver no cartão
            JButton button = buttonMap.get(numero);
            if (button != null) {
                button.setEnabled(true);
                button.setBackground(null);
            }
        });
    }

    private boolean hasCompletedCard() {
        for (JButton button : cardButtons) {
            if (!button.getText().equals("--") && button.getBackground() != Color.GREEN) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(BingoClient::new);
    }
}
