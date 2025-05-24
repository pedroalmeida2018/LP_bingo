# Projeto Bingo Java

Este projeto implementa um jogo de Bingo com interface gráfica (Swing) e comunicação cliente-servidor em Java.

## Ficheiros principais
- **BingoClient.java**: Cliente com interface gráfica, conecta ao servidor, permite marcar números, pedir linha/bingo e recebe notificações.
- **BingoCard.java**: Lógica do cartão de bingo (números, marcações, validação de linha/bingo).
- **BingoServerSimples.java**: Servidor que sorteia números, joga contra o(s) cliente(s) e valida pedidos de linha/bingo.

## Como compilar e executar

Abra o terminal na pasta do projeto e execute:

```
javac BingoCard.java BingoClient.java BingoServerSimples.java
```

### Para rodar o servidor:
```
java BingoServerSimples
```

### Para rodar o cliente:
```
java BingoClient
```

Abra vários clientes para simular vários jogadores.

## Observações
- O servidor sorteia números automaticamente a cada 10 segundos.
- O servidor também joga e pode ganhar linha/bingo.
- O cliente só pode marcar números que já foram sorteados.
- O pedido de linha/bingo é validado pelo servidor e todos os clientes são notificados.

---

Se precisar de mais instruções ou ajustes, peça ao Copilot!
