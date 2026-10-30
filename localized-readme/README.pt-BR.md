# Ibis Wallet

Ibis é uma carteira Bitcoin modular de autocustódia, com foco em design intuitivo, privacidade e personalização.

Feita para usuários experientes — sem tutoriais nem rodinhas.

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Obter APK no GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

**Idiomas:** [English](../README.md) · [Español](README.es.md) · [Русский](README.ru.md) · Português (Brasil) · [Français](README.fr.md) · [Deutsch](README.de.md) · [日本語](README.ja.md) · [简体中文](README.zh-CN.md)

## Principais recursos

### Layer 1 — Bitcoin
- **Multi-Wallet** - Criar, importar, exportar e alternar entre várias carteiras
- **Multi-Seed** - Suporte a frases-semente BIP39 ou Electrum na importação
- **Dice Entropy** - Gerar seeds a partir de dados
- **Multisig Wallets** - Importar descritores multisig, coordenar assinatura PSBT e assinar localmente como cosignatário
- **Watch-only Wallets** - Importar xpub/zpub, descritores de saída ou um único endereço
- **Import Private Key** - Varrer ou importar chaves privadas (formato WIF)
- **Silent Payments** - Suporte total BIP-352: envio e recebimento, com servidor Frigate padrão
- **Hardware Wallet Signing** - Usar QR codes animados ou arquivos .psbt para assinatura air-gapped
- **NFC PSBT Signing** - Envie PSBTs para uma carteira física com um toque NFC e receba a assinatura da mesma forma
- **Offline Signer** - Use o Ibis em um telefone sem internet como assinante a frio para PSBT de observação.
- **Dust Protection** - Sinaliza dust suspeito e o exclui da seleção automática
- **Coin Control** - Selecionar UTXOs específicos, congelar/descongelar, enviar a partir de saídas individuais
- **Require Coin Control** - Opção para forçar a seleção de UTXOs antes de envios on-chain
- **RBF & CPFP** - Aumentar taxas em transações não confirmadas, inclusive fluxos PSBT seguros para multisig
- **RBF by Default** - Envios on-chain sinalizam replace-by-fee por padrão; pode ser desativado
- **Cancel Transactions** - Cancelar transações de saída não confirmadas com RBF
- **Manual Broadcast** - Transmitir qualquer transação bruta assinada diretamente à rede Bitcoin
- **Batch Sending** - Enviar para vários destinatários em uma única transação
- **Message Signing** - Assinar e verificar mensagens com BIP137
- **Checksum Helper** - Calcular a palavra de checksum faltante a partir de prefixo de 11/23 palavras
- **BIP329 Labels** - Rótulos de carteira no padrão da indústria para transações e endereços
- **Transaction Search** - Buscar o histórico por data, endereço ou rótulo
- **Built on** [BDK](https://bitcoindevkit.org/)

### Layer 2 — Lightning nativo, Ark, Spark e Liquid
- **Modular Integration** - Escolher qual Layer 2 ativar em cada carteira
- **Lightning** - Conectar um nó remoto via LND (LND REST), CLN (clnrest) ou NWC (NIP-47)
- **Ark** - Ark L2 com gerenciamento de VTXO, envio e recebimento Lightning, boarding em transação única e saídas unilaterais (Built on [Bark](https://github.com/ark-bitcoin/bark))
- **Spark** - Spark L2 com envio e recebimento Lightning e on-chain, e saídas unilaterais (Built on [Breez-SDK](https://github.com/breez/spark-sdk))
- **Liquid** - Carteira Liquid completa com transações confidenciais e suporte a USDt (Built on [LWK](https://github.com/Blockstream/lwk))
- **Watch-only Liquid Wallets** - Importar carteiras Liquid somente leitura com descritores SLIP77
- **Lightning Payments** - Pagar faturas Bolt 11 e Bolt 12, ou endereços Lightning
- **Lightning Invoices** - Gerar faturas Bolt 11
- **Chain Swaps** - Trocar facilmente entre L1 e L2
- **Coin Control** - Selecionar UTXOs específicos para swaps e pagamentos
- **BIP329 Labels** - Suporte a rótulos para transações Ark, Spark e Liquid

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
- **Languages** - Inglês, espanhol, russo, português (Brasil), francês, alemão, japonês e chinês simplificado
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

> [!IMPORTANT]
> **Programa de bug bounty suspenso por falta de fundos.** Relatos são bem-vindos e serão reconhecidos, mas nenhuma recompensa monetária será paga até que o programa seja retomado.

Oferecemos $1.000 pagos em BTC por vulnerabilidades críticas no Ibis Wallet que possam levar à perda não autorizada de fundos do usuário.

Estão no escopo vulnerabilidades com um caminho de ataque prático e realista que resulte em roubo ou perda permanente de fundos (p. ex. comprometimento remoto da carteira, extração da seed, manipulação de transações etc.).

O Ibis é um software em beta feito para usuários experientes de Bitcoin. Backups da seed fora do dispositivo são prática padrão esperada: problemas totalmente recuperáveis restaurando da seed, sem roubo por terceiros, são reconhecidos, mas ficam fora das recompensas.

Problemas de menor gravidade, vulnerabilidades teóricas sem caminho de exploit prático, ou achados que não levem à perda de fundos serão reconhecidos, mas em geral ficam fora do escopo de recompensas monetárias.

Premiações do bug bounty: [15 de agosto de 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Aviso

O Ibis é codificado e auditado pelos modelos de IA de fronteira mais atuais.

Auditoria mais recente: [15 de agosto de 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Doações

Como originalmente fiz esta carteira para mim, não espero doações. No entanto, tokens não são baratos. Se quiser demonstrar gratidão, fique à vontade.

<img width="217" height="200" alt="image" src="https://github.com/user-attachments/assets/ea7993d0-ce33-4578-b534-5ea86cd74038" />

```bash
bc1qwjvn8qf27g6fesna35828u4wjpeh325rlu05a8
```


## Licença

Código aberto. Veja [LICENSE](../LICENSE) para detalhes.
