# TelaLeve v0.3 — Android TV

Tela inicial nativa, leve e experimental para Android TV 6+ (API 23). Testes reais iniciais. A compatibilidade de outras marcas/modelos e do botão HOME não é garantida.

## Funcionalidades

- Carrossel horizontal dos aplicativos instalados. Primeira ordem: YouTube, Netflix, Prime Video, Disney+, Apple TV, HBO Max e Crunchyroll; os demais seguem.
- OK abre app; segurar OK permite reordenar, favoritar ou ocultar; botão ORGANIZAR reexibe apps e restaura a ordem.
- Histórico local dos últimos 5 aplicativos **abertos pelo TelaLeve** (não acompanha episódios ou filmes assistidos dentro dos streamings).
- Busca de filmes e séries em português pelo TMDB.
- Catálogo de **títulos recentes disponíveis por assinatura no Brasil**, filtrável por streaming e por filmes/séries. A data de estreia de um filme não é a data de entrada dele em cada catálogo.
- Detalhes com pôsteres, sinopses e nota **TMDB**. Notas IMDb e Rotten Tomatoes não estão incluídas (exigem acesso/licenciamento apropriado).
- Disponibilidade de streaming via JustWatch/TMDB, com informações condicionadas à cobertura.
- Interface nativa com cache de imagens limitado e sem carregamento de catálogo antes da grade de apps. Requer conexão com internet para TMDB.
- Sem anúncios, rastreadores ou chaves inseridas no código-fonte.

## Obter acesso à API TMDB

Crie uma conta em https://www.themoviedb.org e solicite acesso em https://www.themoviedb.org/settings/api. Use o **API Read Access Token**, não a chave v3 curta.

### Método 1: copiar no Mac e enviar pelo ADB (recomendado)

1. Instale e abra o TelaLeve v0.3 **uma vez** (isso cria o diretório de dados).
2. No Safari ou navegador do Mac, copie o token TMDB para a área de transferência. Não o digite no Terminal e não o compartilhe em chat.
3. Com o Mac conectado à mesma rede da TV e ADB autorizado, execute (alterando o IP):

       bash scripts/importar-tmdb-mac.sh IP_DA_TV

   Sem clonar o repositório, também é possível executar:

       adb connect IP_DA_TV:5555
       adb -s IP_DA_TV:5555 shell "run-as io.github.up2gooseo.telaleve mkdir -p files"
       pbpaste | adb -s IP_DA_TV:5555 shell "run-as io.github.up2gooseo.telaleve sh -c 'cat > files/tmdb_access_token'"
       adb -s IP_DA_TV:5555 shell am force-stop io.github.up2gooseo.telaleve
       adb -s IP_DA_TV:5555 shell am start -W -n io.github.up2gooseo.telaleve/.HomeActivity

O TelaLeve importa o token no armazenamento privado ao iniciar e apaga o arquivo temporário. Isso depende do APK **debuggable**; para uma futura versão release assinada, será necessário outro fluxo, como um QR Code de pareamento.

### Método 2: controle remoto / teclado remoto

Abra **CONFIGURAR TMDB**, cole o token no campo seguro e salve. Pode utilizar um aplicativo de controle remoto para Android TV que ofereça entrada por teclado.

## Compilar

No GitHub Actions, execute testDebugUnitTest e assembleDebug. Baixe o artefato TelaLeve-AndroidTV9-debug e extraia app-debug.apk.

    adb connect IP_DA_TV:5555
    adb install -r app-debug.apk
    adb shell am start -W -n io.github.up2gooseo.telaleve/.HomeActivity

Cada build debug no GitHub Actions pode gerar uma assinatura diferente. Se receber INSTALL_FAILED_UPDATE_INCOMPATIBLE, execute adb uninstall io.github.up2gooseo.telaleve e reinstale. Isso remove somente as preferências do TelaLeve, não os aplicativos de streaming. Uma versão de distribuição precisará usar chave de assinatura estável.

## Segurança e distribuição

- Nunca adicione o token TMDB a commits, segredos expostos, screenshots ou exemplos.
- O protótipo guarda a credencial nas preferências privadas da TV. Não distribua um APK com um token pessoal embutido.
- TMDB API: https://developer.themoviedb.org. Atribuição dentro do menu Sobre: **This product uses the TMDB API but is not endorsed or certified by TMDB.**
- Antes de distribuição pública, revisar termos de utilização, atribuições e imagens de marca, inclusive JustWatch.
- Sem root, alteração de firmware ou desativação do launcher original. Só escolher como HOME depois de validar compatibilidade e reversão.
- TVs Samsung Tizen e LG webOS não são compatíveis diretamente.

## Medições anteriores na Android TV

Medições de desempenho são preliminares e não comprovam economia líquida de RAM.
