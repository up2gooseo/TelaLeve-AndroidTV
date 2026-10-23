# TelaLeve — Android TV

Launcher experimental Android TV 9, pensado para a Android TV com Android TV. Java e widgets nativos, sem anúncios, bibliotecas gráficas pesadas ou serviços persistentes.

## v0.2 — Personalização

Streamings prioritários na ordem sugerida: **YouTube → Netflix → Amazon Prime Video → Disney+ → Apple TV → HBO Max → Crunchyroll**. Outros apps aparecem depois, em ordem alfabética. Favoritos do v0.1 continuam disponíveis.

A identificação prioriza pacotes Android, com fallback pelos nomes. A lista contém somente apps instalados.

- OK: abrir aplicativo.
- Segurar OK: mover uma posição à esquerda/direita, favoritar ou ocultar.
- ORGANIZAR: mostrar aplicativo oculto, restaurar ordem sugerida ou reexibir todos.
- CONFIGURAÇÕES: abrir Android TV Settings.
- A ordenação manual, aplicativos ocultos e favoritos ficam salvos em SharedPreferences.
- Sete colunas acomodam os streamings prioritários na primeira linha.

## Compilar e instalar

GitHub Actions executa testes unitários e o build debug (testDebugUnitTest assembleDebug). Baixe o artefato TelaLeve-AndroidTV9-debug e extraia app-debug.apk.

Na Android TV, pelo Mac:

    adb connect IP_DA_TV:5555
    adb install -r app-debug.apk
    adb shell am start -W -n io.github.up2gooseo.telaleve/.HomeActivity
    adb shell dumpsys meminfo io.github.up2gooseo.telaleve

ATENÇÃO: cada job GitHub Actions pode usar assinatura debug diferente. Se a atualização indicar INSTALL_FAILED_UPDATE_INCOMPATIBLE, execute adb uninstall io.github.up2gooseo.telaleve e depois adb install app-debug.apk. A remoção apaga apenas as preferências deste launcher, sem afetar os serviços de streaming. Para preservar configurações em futuras atualizações, precisaremos configurar uma chave de assinatura estável.

Este protótipo não exige root, não substitui o firmware e não desativa o launcher original. Não definir como tela inicial permanente antes dos testes.

## Diagnóstico histórico

- Launcher Google: cerca de .
- TelaLeve v0.1: cerca de .
- Medições em momentos diferentes. Avaliar RAM e responsividade com os mesmos processos e após reinicialização.
