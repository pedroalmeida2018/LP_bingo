import java.util.*;

public class BingoCard {
    private int[][] numbers = new int[5][5];
    private boolean[][] marked = new boolean[5][5];

    public BingoCard() {
        Set<Integer> nums = new LinkedHashSet<>();
        Random rand = new Random();
        while (nums.size() < 25) nums.add(rand.nextInt(75) + 1);
        Iterator<Integer> it = nums.iterator();
        for (int i = 0; i < 5; i++)
            for (int j = 0; j < 5; j++)
                numbers[i][j] = it.next();
    }

    public BingoCard(int[][] nums) {
        for (int i = 0; i < 5; i++)
            for (int j = 0; j < 5; j++)
                numbers[i][j] = nums[i][j];
    }

    public int getNumber(int row, int col) {
        return numbers[row][col];
    }

    public void markNumber(int n) {
        for (int i = 0; i < 5; i++)
            for (int j = 0; j < 5; j++)
                if (numbers[i][j] == n) marked[i][j] = true;
    }

    public void unmarkNumber(int n) {
        for (int i = 0; i < 5; i++)
            for (int j = 0; j < 5; j++)
                if (numbers[i][j] == n) marked[i][j] = false;
    }

    public boolean isMarked(int row, int col) {
        return marked[row][col];
    }

    public boolean hasLine() {
        for (int i = 0; i < 5; i++) {
            boolean all = true;
            for (int j = 0; j < 5; j++) if (!marked[i][j]) all = false;
            if (all) return true;
        }
        return false;
    }

    public boolean hasBingo() {
        // Bingo só é válido se todas as linhas E todas as colunas estiverem completas
        boolean todasLinhas = true;
        boolean todasColunas = true;
        // Verifica todas as linhas
        for (int i = 0; i < 5; i++) {
            boolean linhaCompleta = true;
            for (int j = 0; j < 5; j++) {
                if (!marked[i][j]) {
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
                if (!marked[i][j]) {
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                sb.append(numbers[i][j]);
                if (j < 4) sb.append(",");
            }
            if (i < 4) sb.append(";");
        }
        return sb.toString();
    }

    public static BingoCard fromString(String str) {
        String[] rows = str.split(";");
        int[][] nums = new int[5][5];
        for (int i = 0; i < 5; i++) {
            String[] cols = rows[i].split(",");
            for (int j = 0; j < 5; j++) {
                nums[i][j] = Integer.parseInt(cols[j]);
            }
        }
        return new BingoCard(nums);
    }
}
