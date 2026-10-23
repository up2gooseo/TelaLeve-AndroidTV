# TelaLeve · Android TV

Launcher experimental para Android TV 9, desenvolvido em Java e componentes nativos Android.

## Funcionalidades
- Grade dos aplicativos instalados, priorizando os compatíveis com Android TV.
- Navegação pelo controle remoto (setas e OK).
- Segurar OK adiciona ou remove favoritos.
- Data, relógio e atalho para configurações.

## Compilar
A workflow **Compilar TelaLeve APK** do GitHub Actions executa automaticamente na branch de desenvolvimento e em pull requests. Faça download do artefato `TelaLeve-AndroidTV9-debug` e extraia `app-debug.apk`.

## Testar na Android TV por ADB (sem substituir o launcher)
```bash
adb connect IP_DA_TV:5555
adb install -r app-debug.apk
adb shell am start -W -n io.github.up2gooseo.telaleve/.HomeActivity
adb shell dumpsys meminfo io.github.up2gooseo.telaleve
```
Teste as setas, OK, favoritos, streaming, Configurações e botão Voltar. **Não desabilite o launcher original.** O botão HOME pode continuar abrindo a interface original, dependendo do firmware. Caso queira remover o protótipo: `adb uninstall io.github.up2gooseo.telaleve`.

## Escopo e segurança
- Firmware Android TV: PKG6.7220.0365BRA; Android TV 9.
- Sem root, alterações de firmware, remoção de pacotes nem permissões perigosas.
- Entradas HDMI e TV nativa continuam acessíveis pelo controle remoto original.
- A compilação em CI e os testes físicos são etapas separadas; o ganho de desempenho depende das medições realizadas na TV.
