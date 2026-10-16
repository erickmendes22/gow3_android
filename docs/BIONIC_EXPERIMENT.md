# Experimento Bionic — Daedalus Fork

Branch: `experimental-bionic`, criada de `gow3` no commit
`b0f2d8c7be59c3f3e1dc24883c460b63c0eb1c7d`.

## Estado desta etapa

A branch já compila os componentes centrais do jogo com NDK r29/Bionic:

- FEX e `libgow3cpu.so`, preservando a revisão `fb925d6fc5cc8576bf0f9ae6978fc6d912485593`.
  O hook de CMake seleciona os fontes Linux do FEX após o NDK configurar Bionic;
  uma adaptação local usa syscall para getdents64, sem atualizar o tradutor.
- `libgow3gpu.so`, sem SDL, X11 ou libvulkan vinculados. O Vulkan usa um único
  carregador Android ICD/HAL e os ponteiros desse driver também atendem código C de terceiros.
- Apresentação GPU por AHardwareBuffer com ownership FOREIGN, sync-fd e o receptor
  `libgow3present.so`/`Gow3Present.java`, sem Xvfb. A implementação ainda não foi exercitada no aparelho.
- `gow3-probe`, módulos de runtime, áudio PCM por FIFO e entrada PadBridge sem SDL.
  Os controles de gatilho, touchpad e captura de entrada pelo overlay foram exercitados no host.
- `gow3-prepare`, para gerar a imagem base e AppContent nativamente. As saídas de imports,
  relocations, TLS e metadados foram comparadas byte a byte com o Python deste fork em fixtures.
  O compilador XML de patches e a composição de updates ainda precisam ser integrados ao caminho nativo.
- Validador ELF que exige ARM64/Bionic, linker Android, dependências permitidas e
  resolução de símbolos fortes. Todos os sete binários/bibliotecas do conjunto passam.
- Pacote exato `Turnip_Gen8_V37_patch.zip`, fixado por URL e hashes, com seleção automática
  no verificador para chip KGSL `0x44xxxxxx` (Adreno Gen8).

**Não é ainda um APK Bionic jogável.** A inicialização do APK continua no caminho glibc;
launcher, PKG, patches e empacotamento Bionic ainda não foram conectados. Não houve teste
no Redmagic, nem medição de FPS ou validação da imagem. Em especial, desativar o heap tagging
não garante que objetos Scudo entregues ao guest estejam abaixo do limite de ponteiros de
40 bits; as alocações que cruzam essa fronteira precisam ser auditadas antes do teste do jogo.

A branch `gow3` e seu runtime permanecem preservados. Não selecionar V37 Gen8 automaticamente
para Adreno 6xx/7xx, Mali ou GPU desconhecida. Falha no driver solicitado não deve ser escondida
por fallback silencioso ao driver do sistema.

## Driver enviado

| Campo | Valor |
| --- | --- |
| Nome | Turnip Gen8 V37 patched |
| Autor | StevenMXZ |
| Arquitetura / libc | AArch64 / Android Bionic (`libc.so`) |
| Biblioteca | `libvulkan_freedreno.so` |
| Entrada exportada | `HMI` (Android Vulkan HAL) |
| minApi declarado | 28 |
| SHA256 do ZIP | `c03b5791610edfc9a9b1f42faeef6b37b07b332a0d5e3a29baf6ddc0a82e0dac` |
| SHA256 da biblioteca | `31b4f16fed6bc0c60d80715067845d1ae34761fcbc1fc37e41e80d1de29a8b4e` |

O ZIP anexado é idêntico ao arquivo publicado em:
https://github.com/StevenMXZ/Adreno-Tools-Drivers/releases/tag/v37

Não confundir com `Turnip_Gen8_V37.zip` (sem `patch`), incluído no Bloodborne.
A variante enviada incorpora patches/hacks do WinNative segundo as notas do autor.
O `driverVersion` em seu meta.json é `Vulkan 1.4.359`; não é uma versão consultada no hardware.

Dependências declaradas: `libhardware.so`, `liblog.so`, `libnativewindow.so`, `libsync.so`,
`libm.so`, `libz.so`, `libdl.so`, `libc.so`. `hw_get_module` é um símbolo requerido, portanto
esta dependência precisa ser resolvida pelo processo Bionic no telefone. A inspeção estática
não comprova o carregamento nem o desempenho no Redmagic 10 Pro.

O driver declara API 28, mas o verificador desta etapa compila para API 30. Isso não significa
que o futuro runtime ou apresentador completo possam funcionar em API 28. O apresentador
Bloodborne usa ASurfaceControl (API 29) e setOnCommit (API 31) para seu melhor caminho FIFO;
sua alternativa em API 29/30 pode apresentar a metade da frequência. Essa decisão ainda
precisa ser validada na migração do GoW III.

## Compilar e verificar

```sh
GOW3_NDK=/caminho/android-ndk-r29 bash tools/android/bionic/build_driver_check.sh
```

Opcionalmente, use o ZIP local (mesmos hashes obrigatórios):

```sh
GOW3_NDK=/caminho/android-ndk-r29 \
GOW3_BIONIC_DRIVER_ZIP=/caminho/Turnip_Gen8_V37_patch.zip \
bash tools/android/bionic/build_driver_check.sh
```

Saída: `out/bionic-driver-check/{bin,drivers,check-driver.sh}`. Esta não é uma distribuição
do runtime completo. A STL estática é usada somente neste executável de diagnóstico isolado;
o runtime com várias bibliotecas deve compartilhar uma única `libc++_shared.so`.

Dentro de um diretório executável privado do app, com seus binários e drivers adjacentes:

```sh
/system/bin/sh check-driver.sh
GOW3_BIONIC_DRIVER=system /system/bin/sh check-driver.sh
GOW3_BIONIC_DRIVER=gen8 /system/bin/sh check-driver.sh
```

O código de saída 0 confirma apenas os pré-requisitos consultados, não apresentação de
quadros, jogo funcional ou vantagem de desempenho. Falhas de `dlopen` registram a dependência
ou erro do linker. Não instale o verificador como substituto de `gow3-probe`.

## Compilar os componentes do jogo

Pré-requisitos do host: CMake, Ninja, pkg-config, compilador, Python, NDK r29 e um prefixo de
headers contendo Boost, robin-map, VMA e o executável host glslangValidator. Em CI pode ser
usado o mesmo prefixo de dependências da build glibc. Os submódulos devem estar inicializados.

```sh
GOW3_NDK=/caminho/android-ndk-r29 \
GOW3_BIONIC_HEADERS=/caminho/prefixo/usr \
GOW3_BIONIC_DRIVER_ZIP=/caminho/Turnip_Gen8_V37_patch.zip \
bash tools/android/bionic/build_runtime.sh
```

Saída: `out/bionic/bin`, `out/bionic/lib` e `out/bionic/drivers/gen8`. O runtime usa uma única
`libc++_shared.so`; as ferramentas independentes de preparação e diagnóstico têm STL estática.
`--link-only` reaproveita CPU/GPU já compiladas para refazer o executável e verificações.
O script não produz APK nem substitui o runtime instalado.

## Validação desta revisão

- 82 testes Android passaram, incluindo o caminho PadBridge sem SDL.
- 4 testes de preparação nativa passaram, incluindo comparação byte a byte do boot-linked.bin,
  content.bin e marcador com o Python do GoW III, reutilização do marcador e rejeição de entradas ruins.
- FEX, GPU, probe, preparação e receptor foram compilados com NDK r29, ARM64, API 30.
- Verificação de dependências/símbolos passou nos sete executáveis/bibliotecas do runtime.
- Shader de pós-processamento de cor, modos de readback e coerência de texturas preservados.
- ZIP local e download do release têm o mesmo SHA256 fixado no manifesto.
- Sem execução em hardware Android nesta etapa; desempenho e apresentação no jogo não validados.

## Próximas integrações necessárias

1. Portar compilação/validação dos patches XML e seleção de resolução/FPS/sombras, composição
   de updates e instalação PKG para Bionic, sem reduzir as verificações atuais do GoW III.
2. Integrar launcher, extração do runtime e receptor AHB ao APK experimental. Conectar o FIFO
   de áudio e o IME aos caminhos existentes e oferecer o driver Gen8 como padrão do caminho Bionic.
3. Auditar memória/ponteiros que cruzam a fronteira guest/host no heap Bionic.
4. Validar no aparelho CUSA01715/01.02, patches, readbacks, coerência de texturas, sincronização,
   cores, saves, áudio e desempenho antes de oferecer um APK experimental jogável.

Não copiar endereços, patches, hooks de câmera ou políticas de memória específicas do Bloodborne.
Não mudar driver ou readbacks do runtime glibc ao integrar estas etapas.

## Origem e créditos

O carregador `gpu/shim/vk_driver_loader.{cpp,h}` foi adaptado, preservando a arquitetura ICD/HAL,
do Bloodborne Android, commit `2e4726530b04f76e513e232d272d386e9973c73b`:
https://github.com/ducvd89/Bloodborne-Android/blob/2e4726530b04f76e513e232d272d386e9973c73b/gpu/shim/vk_driver_loader.cpp

O ABI HAL segue AOSP hardware/libhardware (Apache-2.0), descrito nos comentários do carregador.
Isto não é uma integração de `libadrenotools`: utiliza diretamente drivers Android compatíveis
com o formato de pacote AdrenoTools. A futura integração dessa biblioteca é uma opção separada.

Driver: Mesa/StevenMXZ e contribuidores; fontes/receita em
https://github.com/StevenMXZ/Adreno-Tools-Drivers/tree/v37 e patches adicionais em
https://github.com/WinNative-Emu/Drivers/tree/main/patches . Antes de distribuir o driver em um
APK, incluir os avisos/licenças e as fontes correspondentes exigidas pelas respectivas licenças.
