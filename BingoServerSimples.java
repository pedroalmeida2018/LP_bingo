import java.io.*;
import java.net.*;
import java.util.*;

public class BingoServerSimples {
    private static final int PORT = 12345;
    private static final int MAX_NUM = 75;
    private static final int NUM_JOGADORES = 2;
    private static final Map<String, BingoCard> clientCards = new HashMap<>();
    private static final Set<Integer> drawnNumbers = new HashSet<>(); 
    private static final Set<String> prontos = new HashSet<>();
    private static final List<PrintWriter> clients = new ArrayList<>();
    private static final String SERVER_NAME = "Servidor";
    private static final Map<String, String> cardIdToName = new HashMap<>();
    private static final BingoCard serverCard = new BingoCard();
    private static volatile boolean jogoIniciado = false;
    private static volatile boolean linhaFeita = false;
    private static volatile boolean bingoFeito = false;
    private static Thread sorteioThread = null;

    public static void main(String[] args) {
        System.out.println("Servidor Bingo iniciado na porta " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.out.println("Erro no servidor: " + e.getMessage());
        }
    }

    private static synchronized void broadcast(String mensagem) {
        System.out.println(">>> Broadcast: " + mensagem);
        for (PrintWriter client : clients) {
            client.println(mensagem);
            client.flush();
        }
    }

    private static void iniciarSorteio() {
        if (sorteioThread != null && sorteioThread.isAlive()) {
            return;
        }

        sorteioThread = new Thread(() -> {
            System.out.println(">>> Iniciando sorteio de números...");
            Random rand = new Random();
            
            try {
                Thread.sleep(2000);
                
                while (!bingoFeito && drawnNumbers.size() < MAX_NUM) {
                    int numeroSorteado;
                    synchronized (drawnNumbers) {
                        do {
                            numeroSorteado = rand.nextInt(MAX_NUM) + 1;
                        } while (!drawnNumbers.add(numeroSorteado));
                    }
                    
                    broadcast("NUMERO;" + numeroSorteado);
                    System.out.println(">>> Número sorteado: " + numeroSorteado + " (Total: " + drawnNumbers.size() + ")");
                    
                    for (BingoCard card : clientCards.values()) {
                        card.markNumber(numeroSorteado);
                    }
                    
                    serverCard.markNumber(numeroSorteado);
                    if (!linhaFeita && serverCard.hasLine()) {
                        linhaFeita = true;
                        broadcast("LINHA_VALIDA;" + SERVER_NAME);
                    }
                    if (serverCard.hasBingo()) {
                        bingoFeito = true;
                        broadcast("BINGO_VALIDA;" + SERVER_NAME);
                        break;
                    }
                    
                    Thread.sleep(3000);
                }
            } catch (InterruptedException e) {
                System.out.println(">>> Sorteio interrompido: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        });
        sorteioThread.setDaemon(true);
        sorteioThread.start();
    }
    
    private static void handleClient(Socket client) {
        String nome = null;
        String cardId = null;
        
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(client.getOutputStream(), true)
        ) {
            synchronized (clients) {
                clients.add(out);
            }
            String linha;
            
            System.out.println(">>> Novo cliente conectado! Total de clientes: " + clients.size());
            
            while ((linha = in.readLine()) != null) {
                String[] partes = linha.split(";");
                String comando = partes[0];
                  if (comando.equals("CONECTAR")) {
                    nome = partes[1];
                    cardId = partes[2];
                    BingoCard card = new BingoCard();
                    clientCards.put(cardId, card);
                    cardIdToName.put(cardId, nome);
                    out.println("CARD;" + card.toString());
                    System.out.println(">>> Cliente " + nome + " conectado com cardId " + cardId);
                    out.println("OK;Bem-vindo(a) " + nome + "!");
                    broadcast("INFO;" + nome + " entrou no jogo!");
                    continue;
                }
                if (linha.startsWith("LOGIN;")) {
                    String[] parts = linha.split(";");
                    nome = parts[1];
                    cardId = parts[2];
                    BingoCard card = new BingoCard();
                    System.out.println(">>> Gerando novo cartão para " + nome);
                    StringBuilder cardStr = new StringBuilder();
                    for (int i = 0; i < 5; i++) {
                        for (int j = 0; j < 5; j++) {
                            cardStr.append(card.getNumber(i, j));
                            if (j < 4) cardStr.append(",");
                        }
                        if (i < 4) cardStr.append(";");
                    }
                    clientCards.put(cardId, card);
                    cardIdToName.put(cardId, nome);
                    String cartaoString = cardStr.toString();
                    System.out.println(">>> Cartão gerado: " + cartaoString);
                    out.println("CARTAO;" + cardId + ";" + cartaoString);
                    out.flush();
                    System.out.println(">>> Cartão enviado para " + nome + " (ID: " + cardId + ")");
                } else if (linha.startsWith("PRONTO;")) {
                    String[] parts = linha.split(";");
                    String readyCardId = parts[1];
                    System.out.println(">>> Recebido PRONTO do jogador " + cardIdToName.get(readyCardId));
                    synchronized (prontos) {
                        prontos.add(readyCardId);
                    }
                    System.out.println(">>> Jogadores prontos: " + prontos.size() + "/" + NUM_JOGADORES);
                    System.out.println(">>> Lista de prontos: " + String.join(", ", prontos));
                    System.out.println(">>> Jogo já iniciado? " + jogoIniciado);
                    broadcast("AGUARDE;Aguardando outros jogadores ficarem prontos... (" + prontos.size() + "/" + NUM_JOGADORES + ")");
                    
                    if (prontos.size() >= NUM_JOGADORES && !jogoIniciado) {
                        System.out.println(">>> INICIANDO JOGO!");
                        jogoIniciado = true;
                        broadcast("INICIAR;O jogo vai começar!");
                        System.out.println(">>> Mensagem INICIAR enviada para todos os clientes");
                        iniciarSorteio();
                    }
                } else if (linha.startsWith("LINHA;")) {
                    String[] parts = linha.split(";");
                    String lineCardId = parts[1];
                    BingoCard card = clientCards.get(lineCardId);
                    if (card != null && card.hasLine()) {
                        broadcast("LINHA_VALIDA;" + cardIdToName.get(lineCardId));
                    }
                } else if (linha.startsWith("BINGO;")) {
                    String[] parts = linha.split(";");
                    String bingoCardId = parts[1];
                    BingoCard bingoCard = clientCards.get(bingoCardId);
                    String bingoPlayer = cardIdToName.get(bingoCardId);
                    if (bingoCard != null && bingoPlayer != null) {
                        synchronized (drawnNumbers) {
                            for (int num : drawnNumbers) {
                                bingoCard.markNumber(num);
                            }
                        }
                        if (bingoCard.hasBingo()) {
                            bingoFeito = true;
                            System.out.println(">>> BINGO VÁLIDO do jogador " + bingoPlayer);
                            broadcast("BINGO_VALIDA;" + bingoPlayer);
                        } else {
                            System.out.println(">>> BINGO INVÁLIDO do jogador " + bingoPlayer);
                            out.println("BINGO_INVALIDO;Você precisa marcar todos os números sorteados antes de fazer Bingo!");
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao processar cliente: " + e.getMessage());
        } finally {
            try {
                synchronized (clients) {
                    clients.removeIf(writer -> writer.checkError());
                }
                client.close();
            } catch (IOException e) {
                System.out.println("Erro ao fechar conexão com cliente: " + e.getMessage());
            }
        }
    }
}
