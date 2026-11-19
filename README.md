# Faixa Livre TV: apps para Windows e Android TV

Desenvolvido por Josmar Braga, professor especialista em tecnologias e IA na educação.

Estes apps abrem o **Faixa Livre TV** (https://faixa-livre-tv.web.app) numa janela própria e liberam
o que o navegador bloqueia em sites. Assim abrem também os canais marcados como **"limitado"**:

- canais com endereço **sem https**;
- canais cujo servidor **não libera a leitura por sites**;
- canais que exigem um **cabeçalho especial**.

Como os apps carregam o site publicado, **toda atualização do site chega aos apps sozinha**.
Só é preciso gerar um app novo quando mudar algo nesta pasta.

## Onde baixar

Na página **Releases** deste repositório (menu à direita), na versão **"Faixa Livre TV - versão mais recente"**:

| Aparelho | Arquivo |
|---|---|
| Windows (instalar) | `FaixaLivreTV-instalador.exe` |
| Windows (sem instalar) | `FaixaLivreTV-portatil.exe` |
| Android TV, Google TV, celular | `FaixaLivreTV.apk` |

Os arquivos são gerados sozinhos pelo GitHub a cada mudança (aba **Actions**), em cerca de 10 minutos.
Como este repositório é **privado**, os downloads da página Releases só funcionam para quem está logado
na conta dona do repositório. Para outras pessoas, use o endereço do site (TV) ou torne o repositório público.

## Instalar no Windows

1. Baixe `FaixaLivreTV-instalador.exe` e abra.
2. Se aparecer **"O Windows protegeu o computador"**, clique em **Mais informações** e depois em **Executar assim mesmo**.
   (O aviso aparece porque o programa ainda não tem assinatura digital paga.)
3. O Faixa Livre TV abre e fica no menu Iniciar.
   - **F11**: tela cheia.
   - **F12**: ferramentas de diagnóstico (para ver o erro exato de um canal).
   - **F5**: recarregar.
   - **Ctrl+Shift+R**: recarregar limpando a cópia guardada do site (use depois de publicar uma versão nova do site).

## Instalar na TV (Android TV / Google TV)

1. Na TV, abra a Play Store e instale o app gratuito **Downloader** (da AFTVnews).
2. Nas configurações da TV, permita que o **Downloader** instale apps desconhecidos
   (Configurações > Apps > Segurança e restrições > Fontes desconhecidas > Downloader).
3. No Downloader, digite o endereço:
   `https://github.com/bragajosmar-glitch/faixa-livre-tv-app/releases/latest/download/FaixaLivreTV.apk`
   (funciona só com o repositório **público**; o site no Firebase gratuito não aceita arquivos .apk)
4. Confirme a instalação. O app aparece na tela inicial da TV com o banner do Faixa Livre TV.

No controle remoto: setas e OK navegam, **Voltar** sai da tela cheia e volta ao Início,
**CH+ / CH−** trocam de canal e **Play/Pausa** pausa.

## Limites conhecidos

- O **login com o Google** pode não funcionar dentro dos apps (o Google bloqueia login em janelas embutidas).
  Os favoritos ficam salvos no próprio aparelho.
- "Abrir arquivos" e "Baixar para outro player" funcionam no Windows; no Android, use o endereço da lista.
- Canais que estão fora do ar ou bloqueados por país continuam sem abrir.

## Para a Play Store (depois)

O app de teste usa a chave `android/app/faixalivretv-teste.keystore`, que serve só para instalar direto.
Para publicar na Play Store, crie uma chave oficial e guarde nos **segredos** do repositório
(Settings > Secrets and variables > Actions): `ANDROID_KEYSTORE_B64` (a chave em base64),
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD`. A montagem passa a usá-la sozinha.

## Estrutura

- `desktop/`: app para Windows (também gera Mac e Linux), feito com Electron.
- `android/`: app nativo para Android TV e celular (Kotlin, WebView).
- `.github/workflows/apps.yml`: montagem automática.
