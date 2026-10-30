# Ibis Wallet

Ibis é uma carteira Bitcoin modular de autocustódia, com foco em design intuitivo, privacidade e personalização.

Feita para usuários experientes — sem tutoriais nem rodinhas.

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Obter APK no GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

**Idiomas:** [English](../README.md) · [Español](README.es.md) · [Русский](README.ru.md) · Português (Brasil) · [Français](README.fr.md) · [Deutsch](README.de.md) · [日本語](README.ja.md) · [简体中文](README.zh-CN.md)

## Principais recursos

### Layer 1 — Bitcoin
- **Multicarteira** - Criar, importar, exportar e alternar entre várias carteiras
- **Multissemente** - Suporte a frases-semente BIP39 ou Electrum na importação
- **Entropia por dados** - Gerar seeds a partir de dados
- **Carteiras multisig** - Importar descritores multisig, coordenar assinatura PSBT e assinar localmente como cosignatário
- **Carteiras somente leitura** - Importar xpub/zpub, descritores de saída ou um único endereço
- **Importar chave privada** - Varrer ou importar chaves privadas (formato WIF)
- **Pagamentos silenciosos** - Suporte total BIP-352: envio e recebimento, com servidor Frigate padrão
- **Assinatura com carteira física** - Usar QR codes animados ou arquivos .psbt para assinatura air-gapped
- **Assinatura PSBT por NFC** - Envie PSBTs para uma carteira física com um toque NFC e receba a assinatura da mesma forma
- **Assinante offline** - Use o Ibis em um telefone sem internet como assinante a frio para PSBT de observação.
- **Proteção contra dust** - Sinaliza dust suspeito e o exclui da seleção automática
- **Controle de moedas** - Selecionar UTXOs específicos, congelar/descongelar, enviar a partir de saídas individuais
- **Exigir controle de moedas** - Opção para forçar a seleção de UTXOs antes de envios on-chain
- **RBF e CPFP** - Aumentar taxas em transações não confirmadas, inclusive fluxos PSBT seguros para multisig
- **RBF por padrão** - Envios on-chain sinalizam replace-by-fee por padrão; pode ser desativado
- **Cancelar transações** - Cancelar transações de saída não confirmadas com RBF
- **Transmissão manual** - Transmitir qualquer transação bruta assinada diretamente à rede Bitcoin
- **Envio em lote** - Enviar para vários destinatários em uma única transação
- **Assinatura de mensagens** - Assinar e verificar mensagens com BIP137
- **Assistente de checksum** - Calcular a palavra de checksum faltante a partir de prefixo de 11/23 palavras
- **Rótulos BIP329** - Rótulos de carteira no padrão da indústria para transações e endereços
- **Busca de transações** - Buscar o histórico por data, endereço ou rótulo
- **Baseado em** [BDK](https://bitcoindevkit.org/)

### Layer 2 — Lightning nativo, Ark, Spark e Liquid
- **Integração modular** - Escolher qual Layer 2 ativar em cada carteira
- **Lightning** - Conectar um nó remoto via LND (LND REST), CLN (clnrest) ou NWC (NIP-47)
- **Ark** - Ark L2 com gerenciamento de VTXO, envio e recebimento Lightning, boarding em transação única e saídas unilaterais (Baseado em [Bark](https://github.com/ark-bitcoin/bark))
- **Spark** - Spark L2 com envio e recebimento Lightning e on-chain, e saídas unilaterais (Baseado em [Breez-SDK](https://github.com/breez/spark-sdk))
- **Liquid** - Carteira Liquid completa com transações confidenciais e suporte a USDt (Baseado em [LWK](https://github.com/Blockstream/lwk))
- **Carteiras Liquid somente leitura** - Importar carteiras Liquid somente leitura com descritores SLIP77
- **Pagamentos Lightning** - Pagar faturas Bolt 11 e Bolt 12, ou endereços Lightning
- **Faturas Lightning** - Gerar faturas Bolt 11
- **Swaps on-chain** - Trocar facilmente entre L1 e L2
- **Controle de moedas** - Selecionar UTXOs específicos para swaps e pagamentos
- **Rótulos BIP329** - Suporte a rótulos para transações Ark, Spark e Liquid

### Privacidade e segurança
- **Offline por padrão** - O app inicia sem conexões externas
- **Tor integrado** - Tor nativo, sem Orbot nem proxies externos
- **PIN e biometria** - Com tempo de bloqueio configurável
- **PIN de coação** - PIN de coerção que abre uma carteira isca
- **PIN de apagamento** - Configurar um PIN secundário que apaga silenciosamente todos os dados de carteira no dispositivo
- **Apagamento automático** - Limite de desbloqueios falhos que apaga automática e irreversivelmente todos os dados do app
- **Limpar área de transferência** - Opção para limpar automaticamente a área de transferência ao bloquear ou fechar o app
- **Modo disfarce** - Disfarçar o Ibis como um app de calculadora
- **Alternar privacidade** - Ocultar todos os valores e saldos
- **Apagar histórico** - Apagar localmente transações específicas ou todo o histórico
- **Bloqueio de carteiras** - Bloquear carteiras específicas de forma independente
- **Metadados reforçados** - Logs e erros de UI sanitizados para reduzir metadados visíveis ao SO

### Conectividade e servidores
- **Servidores personalizados** - Conectar aos seus próprios servidores Electrum, explorador de blocos e estimativa de taxas
- **Suporte a NFC** - Transmitir e receber pedidos de pagamento via NFC
- **Notificações de atualização** - Ativar ou desativar avisos de novas versões
- **Tratamento de URI Bitcoin** - Registrar-se como manipulador de links `bitcoin:`

### Localização
- **Idiomas** - Inglês, espanhol, russo, português (Brasil), francês, alemão, japonês e chinês simplificado
- **Tipografia** - Várias tipografias

### Backup e restauração
- **Backups criptografados completos** - Fazer backup e restaurar todo o estado do app, incluindo carteiras, configurações e rótulos

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

Auditorias: [29 de outubro de 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v5.1-beta) · [15 de agosto de 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Doações

Como originalmente fiz esta carteira para mim, não espero doações. No entanto, tokens não são baratos. Se quiser demonstrar gratidão, fique à vontade.

<img width="217" height="200" alt="image" src="https://github.com/user-attachments/assets/ea7993d0-ce33-4578-b534-5ea86cd74038" />

```bash
bc1qwjvn8qf27g6fesna35828u4wjpeh325rlu05a8
```


## Licença

Código aberto. Veja [LICENSE](../LICENSE) para detalhes.
