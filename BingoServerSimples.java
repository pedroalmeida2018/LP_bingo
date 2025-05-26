import java.io.*;
import java.net.*;
import java.util.*;

public class BingoServerSimples {    private static final int PORT = 12345;
    private static final int MAX_NUM = 75;
    private static final int NUM_JOGADORES = 2; // Defina o número de jogadores necessários
    private static Set<Integer> drawnNumbers = new HashSet<>();
    private static List<PrintWriter> clients = new ArrayList<>();
    private static Map<String, String> cardIdToName = new HashMap<>();
    private static Map<String, BingoCard> clientCards = new HashMap<>();
    private static BingoCard serverCard = new BingoCard();
    private static String serverName = "Servidor";
    private static boolean linhaFeita = false;
    private static boolean bingoFeito = false;
    private static Set<String> prontos = new HashSet<>();
    private static volatile boolean jogoIniciado = false;
    private static Thread sorteioThread;

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Servidor Bingo iniciado na porta " + PORT);

        // Thread de sorteio só será iniciada após todos os jogadores estarem prontos
        sorteioThread = new Thread(() -> {
            Random rand = new Random();
            while (!bingoFeito && drawnNumbers.size() < MAX_NUM) {
                int n = rand.nextInt(MAX_NUM) + 1;
                if (drawnNumbers.add(n)) {
                    broadcast("NUMERO;" + n);
                    System.out.println("Sorteado: " + n);
                    serverCard.markNumber(n);
                    if (!linhaFeita && serverCard.hasLine()) {
                        linhaFeita = true;
                        broadcast("LINHA_VALIDA;" + serverName);
                    }
                    if (serverCard.hasBingo()) {
                        bingoFeito = true;
                        broadcast("BINGO_VALIDA;" + serverName);
                    }
                    try { Thread.sleep(10000); } catch (InterruptedException e) { }
                }
            }
        });

        // Aceita clientes
        while (true) {
            Socket client = serverSocket.accept();
            new Thread(() -> handleClient(client)).start();
        }
    }

    private static void handleClient(Socket client) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(client.getOutputStream(), true);
            clients.add(out);
            String linha;
            String nome = "";
            String cardId = "";
            boolean cartaoEnviado = false;
            while ((linha = in.readLine()) != null) {
                System.out.println("Recebido do cliente: " + linha);
                if (linha.startsWith("LOGIN;")) {
                    String[] parts = linha.split(";");
                    nome = parts[1];
                    cardId = parts[2];
                    cardIdToName.put(cardId, nome);
                    // Gerar cartão e enviar ao cliente
                    BingoCard card = new BingoCard();
                    clientCards.put(cardId, card);
                    StringBuilder sb = new StringBuilder();
                    sb.append("CARTAO;");
                    for (int i = 0; i < 5; i++) {
                        for (int j = 0; j < 5; j++) {
                            int num = card.getNumber(i, j);
                            System.out.println("Número gerado para posição [" + i + "," + j + "]: " + num);
                            sb.append(num);
                            if (!(i == 4 && j == 4)) sb.append(",");
                        }
                    }
                    String cartaoMsg = sb.toString();
                    System.out.println("Enviando cartão para cliente: " + cartaoMsg);
                    out.println(cartaoMsg);
                    cartaoEnviado = true;
                } else if (linha.startsWith("PRONTO;")) {
                    // Cliente sinalizou que está pronto
                    prontos.add(cardId);
                    broadcast("AGUARDE;Aguardando outros jogadores ficarem prontos... (" + prontos.size() + "/" + NUM_JOGADORES + ")");
                    if (prontos.size() == NUM_JOGADORES && !jogoIniciado) {
                        jogoIniciado = true;
                        broadcast("INICIAR;O jogo vai começar!");
                        sorteioThread.start();
                    }
                } else if (linha.startsWith("LINHA;")) {
                    if (!linhaFeita) {
                        linhaFeita = true;
                        broadcast("LINHA_VALIDA;" + nome);
                    } else {
                        out.println("INVALIDO");
                    }
                } else if (linha.startsWith("BINGO;")) {
                    if (!bingoFeito) {
                        bingoFeito = true;
                        broadcast("BINGO_VALIDA;" + nome);
                    } else {
                        out.println("INVALIDO");
                    }
                }
            }
        } catch (IOException e) {
            // Cliente desconectou
        }
    }

    private static void broadcast(String msg) {
        for (PrintWriter out : clients) {
            out.println(msg);
        }
    }
}
