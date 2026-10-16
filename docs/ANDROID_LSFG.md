# Lossless Scaling no Bionic

O Daedalus Fork usa o backend Android de geração de quadros do
[Bloodborne-Android](https://github.com/ducvd89/Bloodborne-Android), adaptado do commit
`856bd99b05ee088782e881b99fca7945110ea0ca`. A biblioteca `liblsfg-gow3.so` compila o
backend MIT de [lsfg-vk-android](https://github.com/Ragnarok93/lsfg-vk-android), fixado
em `0802cd33319c101e4c41f6828153d3331d8a3258`. A camada Vulkan Linux não é usada no Bionic.

## Uso

Importe seu próprio `Lossless.dll` na tela inicial. No menu durante o jogo,
**Lossless Scaling** oferece 2×, 3×, 4×, escala de fluxo e modo de desempenho.
Ativar, desativar ou alterar essas opções é aplicado durante o jogo. A
inicialização dos shaders pode causar uma pausa. O APK não inclui a DLL nem seus shaders.

A procura mantém a ordem usada pelo launcher: DLL ao lado da pasta selecionada do
jogo, depois `files/gow3port/Lossless.dll`. `GOW3_LSFG_DLL` permite substituir o
caminho para diagnóstico. O estado atual é gravado atomicamente em
`files/gow3port/lsfg.state`, consultado entre quadros a cada 250 ms.

## Integração

- O backend recebe o `vkGetInstanceProcAddr` do jogo e a identidade do dispositivo:
  usa o mesmo driver do sistema, Turnip integrado ou driver personalizado selecionado.
- Dois buffers de entrada e até três de saída são compartilhados por
  `AHardwareBuffer`. Semáforos SYNC_FD e transferências de propriedade para a família
  EXTERNAL sincronizam os dois dispositivos Vulkan.
- Os quatro primeiros quadros preenchem o histórico. Depois, os quadros gerados
  são apresentados antes do quadro real, com espaçamento no apresentador.
- A apresentação mantém FIFO enquanto o LSFG está ativo. Os buffers de saída
  passam para multiplicador + 3 e retornam à quantidade normal ao desativar.
- Falhas registram `LSFG: off (...)` e retornam à apresentação normal. O marcador
  de fallback ajusta a seleção de taxa de atualização do app; reaplicar permite tentar novamente.
- O GoW III mantém sua submissão síncrona e seus patches de tempo atuais. Não foram
  copiados hooks específicos do Bloodborne, nem introduzidas esperas de conclusão
  da GPU em cada apresentação quando o LSFG está desligado.

`GOW3_LSFG_STATS=1` registra quadros reais/gerados e esperas. Para diagnóstico,
`GOW3_LSFG_SYNC=host` força sincronização pela CPU e `GOW3_LSFG_PACE=0` desativa
o espaçamento. O padrão usa SYNC_FD e espaçamento ativo.

## Validação

Backend compilado com NDK r29 para Android ARM64/API 30. O código de integração
Vulkan e o apresentador foram verificados com o compilador do mesmo NDK, usando
os headers reais do projeto. O diálogo Java foi compilado contra Android API 37.
O teste `test_bionic_lsfg.py` executa o leitor C++ real, verificando atualização
atômica, estado inicial, controle de consultas e prioridade da DLL. O empacotamento
exige a biblioteca e inclui os avisos das dependências.

A geração de quadros, os artefatos visuais e o frame pacing ainda precisam ser
validados no aparelho. A compilação não comprova o resultado visual.
