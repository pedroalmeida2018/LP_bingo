import java.awt.*;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class BingoClient extends JFrame {
    private JTextField nameField;
    private JLabel nameLabel;
    private JButton readyButton, lineButton, bingoButton;
    private JLabel statusLabel, cardIdLabel;
    private JPanel cardPanel, drawnNumbersPanel;
    private JButton[] cardButtons = new JButton[25];
    private String cardId;
    private java.util.List<JLabel> drawnNumberLabels = new ArrayList<>();
    private JPanel topPanel;
    private JPanel namePanel;
    private BingoCard bingoCard;
    private Set<Integer> drawnNumbers = new HashSet<>();
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public BingoClient() {
        setTitle("Cliente Bingo ESTGA");
        setSize(900, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Top panel with name and ID
        topPanel = new JPanel(new BorderLayout());
        namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        nameField = new JTextField(15);
        namePanel.add(new JLabel("Name: "));
        namePanel.add(nameField);
        topPanel.add(namePanel, BorderLayout.WEST);
        cardId = UUID.randomUUID().toString().substring(0, 8);
        cardIdLabel = new JLabel("Card ID: " + cardId);
        JPanel idPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        idPanel.add(cardIdLabel);
        topPanel.add(idPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Card panel
        cardPanel = new JPanel(new GridLayout(5, 5, 5, 5));
        for (int i = 0; i < 25; i++) {
            JButton cell = new JButton("--");
            cell.setFont(new Font("Arial", Font.PLAIN, 16));
            cell.setEnabled(false);
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
        lineButton.setEnabled(false);
        bingoButton.setEnabled(false);
        buttonsPanel.add(readyButton);
        buttonsPanel.add(lineButton);
        buttonsPanel.add(bingoButton);
        statusLabel = new JLabel("");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        bottomPanel.add(buttonsPanel, BorderLayout.NORTH);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        // Panel for drawn numbers
        drawnNumbersPanel = new JPanel();
        drawnNumbersPanel.setLayout(new BoxLayout(drawnNumbersPanel, BoxLayout.Y_AXIS));
        drawnNumbersPanel.setBorder(BorderFactory.createTitledBorder("Drawn Numbers"));
        JScrollPane scrollPane = new JScrollPane(drawnNumbersPanel);
        scrollPane.setPreferredSize(new Dimension(150, 0));
        add(scrollPane, BorderLayout.EAST);

        // Enable "Ready" button only if name is filled
        nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateName(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateName(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { validateName(); }
            private void validateName() {
                readyButton.setEnabled(!nameField.getText().trim().isEmpty());
            }
        });
        readyButton.setEnabled(false);

        try {
            socket = new Socket("192.168.1.122", 12345); // Altere para o IP/porta do seu servidor
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            // statusLabel.setText("Erro ao conectar ao servidor!"); // Removido conforme pedido
        }

        readyButton.addActionListener(_ignore -> {
            readyButton.setVisible(false);
            // Não gera mais o cartão localmente, espera receber do servidor
            lineButton.setEnabled(true);
            bingoButton.setEnabled(true);
            replaceNameField();
            if (out != null) out.println("LOGIN;" + nameField.getText() + ";" + cardId);
            aguardaCartaoServidor();
        });
        lineButton.addActionListener(_ignore -> {
            if (out != null) {
                out.println("LINHA;" + cardId);
                aguardaRespostaServidor();
            }
        });
        bingoButton.addActionListener(_ignore -> {
            if (out != null) {
                out.println("BINGO;" + cardId);
                aguardaRespostaServidor();
            }
        });

        setVisible(true);
    }

    private void replaceNameField() {
        String name = nameField.getText();
        namePanel.removeAll();
        nameLabel = new JLabel("Name: " + name);
        namePanel.add(nameLabel);
        namePanel.revalidate();
        namePanel.repaint();
    }

    private void aguardaCartaoServidor() {
        new Thread(() -> {
            try {
                while (true) {
                    String resposta = in.readLine();
                    System.out.println("Resposta recebida do servidor: " + resposta);
                    if (resposta == null) break;
                    if (resposta.startsWith("CARTAO;")) {
                        String[] nums = resposta.substring(7).split(",");
                        System.out.println("Números recebidos: " + Arrays.toString(nums));
                        int[][] cardNums = new int[5][5];
                        for (int i = 0; i < 25; i++) {
                            cardNums[i/5][i%5] = Integer.parseInt(nums[i]);
                        }
                        SwingUtilities.invokeLater(() -> gerarCartaoComNumeros(cardNums));
                        // Após receber o cartão, envia PRONTO para o servidor
                        if (out != null) out.println("PRONTO;" + cardId);
                        SwingUtilities.invokeLater(() -> statusLabel.setText("Aguardando outros jogadores ficarem prontos..."));
                    } else if (resposta.startsWith("AGUARDE;")) {
                        String msg = resposta.substring(8);
                        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
                    } else if (resposta.startsWith("INICIAR;")) {
                        String msg = resposta.substring(8);
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText(msg);
                            // Habilita os botões do cartão para permitir jogar
                            for (JButton button : cardButtons) {
                                button.setEnabled(true);
                            }
                        });
                    } else if (resposta.startsWith("NUMERO;")) {
                        String numStr = resposta.substring(7);
                        try {
                            int numero = Integer.parseInt(numStr);
                            SwingUtilities.invokeLater(() -> addDrawnNumber(numero));
                        } catch (NumberFormatException ex) {
                            // Ignora mensagens mal formatadas
                        }
                    }
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("Erro ao receber dados do servidor!"));
            }
        }).start();
    }

    private void gerarCartaoComNumeros(int[][] cardNums) {
        System.out.println("Gerando cartão com números...");
        bingoCard = new BingoCard(cardNums);
        drawnNumbers.clear();
        for (int i = 0; i < 25; i++) {
            int row = i / 5;
            int col = i % 5;
            int number = bingoCard.getNumber(row, col);
            System.out.println("Posição [" + row + "," + col + "] = " + number);
            JButton button = cardButtons[i];
            button.setText(String.valueOf(number));
            button.setEnabled(true);
            button.setBackground(null);
            for (ActionListener al : button.getActionListeners()) {
                button.removeActionListener(al);
            }
            button.addActionListener(_ignore -> {
                if (!drawnNumbers.contains(number)) {
                    statusLabel.setText("Só pode marcar números já sorteados!");
                    return;
                }
                int r = row, c = col;
                if (!bingoCard.isMarked(r, c)) {
                    button.setBackground(Color.GREEN);
                    bingoCard.markNumber(number);
                } else {
                    button.setBackground(null);
                    bingoCard.unmarkNumber(number);
                }
            });
        }
    }

    public void addDrawnNumber(int number) {
        drawnNumbers.add(number); // Adiciona o número sorteado ao conjunto
        JLabel newLabel = new JLabel(String.valueOf(number));
        newLabel.setFont(new Font("Arial", Font.BOLD, 18));
        for (JLabel label : drawnNumberLabels) {
            label.setFont(new Font("Arial", Font.PLAIN, 16));
        }
        drawnNumberLabels.add(newLabel);
        drawnNumbersPanel.add(newLabel);
        drawnNumbersPanel.revalidate();
        drawnNumbersPanel.repaint();
        // NÃO marca automaticamente no cartão!
    }

    private boolean hasLine() {
        return bingoCard != null && bingoCard.hasLine();
    }

    private boolean hasBingo() {
        // Bingo só é válido se todas as linhas E todas as colunas estiverem completas
        if (bingoCard == null) return false;
        boolean todasLinhas = true;
        boolean todasColunas = true;
        // Verifica todas as linhas
        for (int i = 0; i < 5; i++) {
            boolean linhaCompleta = true;
            for (int j = 0; j < 5; j++) {
                if (!bingoCard.isMarked(i, j)) {
                    linhaCompleta = false;
                    break;
                }
            }
            if (!linhaCompleta) {
                todasLinhas = false;
                break;
            }
        }
        // Verifica todas as colunas
        for (int j = 0; j < 5; j++) {
            boolean colunaCompleta = true;
            for (int i = 0; i < 5; i++) {
                if (!bingoCard.isMarked(i, j)) {
                    colunaCompleta = false;
                    break;
                }
            }
            if (!colunaCompleta) {
                todasColunas = false;
                break;
            }
        }
        return todasLinhas && todasColunas;
    }

    private void aguardaRespostaServidor() {
        new Thread(() -> {
            try {
                String resposta = in.readLine();
                SwingUtilities.invokeLater(() -> processaRespostaServidor(resposta));
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("Erro na comunicação com o servidor!"));
            }
        }).start();
    }

    private void processaRespostaServidor(String resposta) {
        // Exemplo de resposta: "LINHA_VALIDA;nome" ou "BINGO_VALIDA;nome" ou "INVALIDO"
        if (resposta == null) return;
        Font bigFont = new Font("Arial", Font.BOLD, 28);
        if (resposta.startsWith("LINHA_VALIDA")) {
            String nome = resposta.split(";")[1];
            if (nome.equals(nameField.getText())) {
                statusLabel.setText("Linha feita por si!");
            } else if (nome.equalsIgnoreCase("Servidor")) {
                statusLabel.setText("Linha feita pelo computador!");
            } else {
                statusLabel.setText("Linha feita pelo utilizador " + nome);
            }
            statusLabel.setFont(bigFont);
            lineButton.setEnabled(false);
            bingoButton.setEnabled(false);
        } else if (resposta.startsWith("BINGO_VALIDA")) {
            String nome = resposta.split(";")[1];
            String mensagem;
            if (nome.equals(nameField.getText())) {
                mensagem = "Parabéns! Fez BINGO (5 linhas e 5 colunas completas)";
            } else if (nome.equalsIgnoreCase("Servidor")) {
                mensagem = "O computador fez BINGO!";
            } else {
                mensagem = "O utilizador " + nome + " fez BINGO!";
            }
            // Exibe mensagem em uma janela menor e centralizada
            JDialog dialog = new JDialog(this, "BINGO!", true);
            JLabel label = new JLabel(mensagem, SwingConstants.CENTER);
            label.setFont(new Font("Arial", Font.BOLD, 22));
            dialog.getContentPane().add(label);
            dialog.setSize(400, 120);
            dialog.setLocationRelativeTo(this);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setVisible(true);
            statusLabel.setText(mensagem);
            statusLabel.setFont(bigFont);
            lineButton.setEnabled(false);
            bingoButton.setEnabled(false);
        } else {
            statusLabel.setText("Pedido inválido ou já feito.");
            statusLabel.setFont(new Font("Arial", Font.BOLD, 18));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(BingoClient::new);
    }
}
