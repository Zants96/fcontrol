# Tasks: Otimização do Tamanho do Instalador e Testes de Saúde

- [x] **Task 1: Otimizar o Comando `jlink` nos Scripts de Build** <!-- id: 1 -->
  - Atualizar `build-installer.sh` e `build-installer-windows.bat` substituindo `java.se` pela lista de módulos estritamente necessários.
  - Adicionar as flags de otimização `--strip-debug`, `--no-header-files`, `--no-man-pages` e `--compress=2`.

- [x] **Task 2: Configurar Perfis de Binários Nativos JavaFX no `pom.xml`** <!-- id: 2 -->
  - Criar perfis Maven `platform-linux` e `platform-win` para que apenas os binários nativos da plataforma corrente sejam empacotados.

- [x] **Task 3: Executar a Suíte de Testes de Saúde e Verificar Status** <!-- id: 3 -->
  - Executar todos os testes da aplicação (`./mvnw test`) e garantir aprovação de 100%.

- [x] **Task 4: Validação Final do Instalador e Redução de Tamanho** <!-- id: 4 -->
  - Compilar o pacote e verificar o tamanho final do instalador obtido.
