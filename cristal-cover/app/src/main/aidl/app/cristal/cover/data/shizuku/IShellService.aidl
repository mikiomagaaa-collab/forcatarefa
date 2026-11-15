// Interface do serviço de usuário do Shizuku: roda comandos com a identidade "shell" (ADB).
package app.cristal.cover.data.shizuku;

interface IShellService {
    // Código de transação reservado pelo Shizuku para encerrar o serviço.
    void destroy() = 16777114;

    // Executa um comando e devolve "<código de saída>\n<saída>".
    String exec(String command) = 1;
}
