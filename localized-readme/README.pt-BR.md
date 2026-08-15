# Ibis Wallet

Ibis é uma carteira Bitcoin modular de autocustódia, com foco em design intuitivo, privacidade e personalização.

Feita para usuários experientes — sem tutoriais nem rodinhas.

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Obter APK no GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

**Idiomas:** [English](../README.md) · [Español](README.es.md) · [Русский](README.ru.md) · Português (Brasil)

## Principais recursos

### Layer 1 — Bitcoin
- **Multi-Wallet** - Criar, importar, exportar e alternar entre várias carteiras
- **Multi-Seed** - Suporte a frases-semente BIP39 ou Electrum na importação
- **Multisig Wallets** - Importar descritores multisig, coordenar assinatura PSBT e assinar localmente como cosignatário
- **Watch-only Wallets** - Importar xpub/zpub, descritores de saída ou um único endereço
- **Import Private Key** - Varrer ou importar chaves privadas (formato WIF)
- **Hardware Wallet Signing** - Usar QR codes animados ou arquivos .psbt para assinatura air-gapped
- **Coin Control** - Selecionar UTXOs específicos, congelar/descongelar, enviar a partir de saídas individuais
- **Require Coin Control** - Opção para forçar a seleção de UTXOs antes de envios on-chain
- **RBF & CPFP** - Aumentar taxas em transações não confirmadas, inclusive fluxos PSBT seguros para multisig
- **RBF by Default** - Envios on-chain sinalizam replace-by-fee por padrão; pode ser desativado
- **Cancel Transactions** - Cancelar transações de saída não confirmadas com RBF
- **Manual Broadcast** - Transmitir qualquer transação bruta assinada diretamente à rede Bitcoin
- **Batch Sending** - Enviar para vários destinatários em uma única transação
- **Silent Payments** - Enviar para endereços Silent Payment BIP-352
- **Message Signing** - Assinar e verificar mensagens com BIP137
- **BIP329 Labels** - Rótulos de carteira no padrão da indústria para transações e endereços
- **Transaction Search** - Buscar o histórico por data, endereço ou rótulo
- **Built on** [BDK](https://bitcoindevkit.org/)

### Layer 2 — Lightning nativo, Liquid e Spark (Ark em breve)
- **Modular Integration** - Escolher qual Layer 2 ativar em cada carteira
- **Lightning** - Conectar um nó remoto via LND (LND REST), CLN (clnrest) ou NWC (NIP-47)
- **Liquid** - Carteira Liquid completa com transações confidenciais (Built on [LWK](https://github.com/Blockstream/lwk))
- **Spark** - Integração Spark com envio e recebimento Lightning e on-chain (Built on [Breez-SDK](https://github.com/breez/spark-sdk))
- **Lightning Payments** - Pagar faturas Bolt 11 e Bolt 12, ou endereços Lightning
- **Lightning Invoices** - Gerar faturas Bolt 11
- **Watch-only Liquid Wallets** - Importar carteiras Liquid somente leitura com descritores SLIP77
- **Liquid USDt** - Guardar e transacionar USDt na Liquid
- **Chain Swaps** - Trocar facilmente entre L1 e L2
- **Coin Control** - Selecionar UTXOs específicos para swaps e pagamentos
- **BIP329 Labels** - Suporte a rótulos para transações Liquid e Spark

### Privacidade e segurança
- **Offline by Default** - O app inicia sem conexões externas
- **Built-in Tor** - Tor nativo, sem Orbot nem proxies externos
- **PIN & Biometrics** - Com tempo de bloqueio configurável
- **Duress PIN** - PIN de coerção que abre uma carteira isca
- **Wipe PIN** - Configurar um PIN secundário que apaga silenciosamente todos os dados de carteira no dispositivo
- **Auto-Wipe** - Limite de desbloqueios falhos que apaga automática e irreversivelmente todos os dados do app
- **Clear Clipboard** - Opção para limpar automaticamente a área de transferência ao bloquear ou fechar o app
- **Cloak Mode** - Disfarçar o Ibis como um app de calculadora
- **Privacy Toggle** - Ocultar todos os valores e saldos
- **Wipe History** - Apagar localmente transações específicas ou todo o histórico
- **Wallet Locks** - Bloquear carteiras específicas de forma independente
- **Hardened Metadata** - Logs e erros de UI sanitizados para reduzir metadados visíveis ao SO

### Conectividade e servidores
- **Custom Servers** - Conectar aos seus próprios servidores Electrum, explorador de blocos e estimativa de taxas
- **NFC Support** - Transmitir e receber pedidos de pagamento via NFC
- **Update Notifications** - Ativar ou desativar avisos de novas versões
- **Bitcoin URI Handling** - Registrar-se como manipulador de links `bitcoin:`

### Localização
- **Languages** - Inglês, russo, espanhol e português (Brasil)
- **Typeface** - Várias tipografias

### Backup e restauração
- **Full Encrypted Backups** - Fazer backup e restaurar todo o estado do app, incluindo carteiras, configurações e rótulos

## Compilação

Requer Android Studio com JDK 17.

```bash
./gradlew :app:assembleDebug      # Debug
./gradlew :app:assembleRelease    # Release
./gradlew testDebugUnitTest       # Testes
./gradlew jacocoUnitTestReport    # Relatório de cobertura
```

**Min SDK:** 26 (Android 8.0) | **Target SDK:** 36 | **ARM** (armeabi-v7a, arm64-v8a)

## Bug Bounty

Oferecemos $1.000 pagos em BTC por vulnerabilidades críticas no Ibis Wallet que possam levar à perda não autorizada de fundos do usuário.

Estão no escopo vulnerabilidades com um caminho de ataque prático e realista que resulte em roubo ou perda permanente de fundos (p. ex. comprometimento remoto da carteira, extração da seed, manipulação de transações etc.).

Problemas de menor gravidade, vulnerabilidades teóricas sem caminho de exploit prático, ou achados que não levem à perda de fundos serão reconhecidos, mas em geral ficam fora do escopo de recompensas monetárias.

## Aviso

O Ibis é codificado e auditado pelos modelos de IA de fronteira mais atuais.

Auditoria mais recente: [15 de agosto de 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Doações

Como originalmente fiz esta carteira para mim, não espero doações. No entanto, tokens não são baratos. Se quiser demonstrar gratidão, fique à vontade.

<img width="173" height="170" alt="image" src="https://github.com/user-attachments/assets/ade56e74-dcd4-4543-a908-b62ed343e883" />

```bash
bc1qk54j45l8s20z6glxnt5zuk7efq2qsjj9n44wc8
```

## Licença

Código aberto. Veja [LICENSE](../LICENSE) para detalhes.
