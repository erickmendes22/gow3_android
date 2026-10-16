# Experimento Bionic — Daedalus Fork

Branch: `experimental-bionic`, criada de `gow3` no commit
`b0f2d8c7be59c3f3e1dc24883c460b63c0eb1c7d`.

## Estado desta etapa

Esta branch começa a migração pelo carregamento Vulkan Android, uma condição necessária
para utilizar o driver enviado pelo usuário. Ainda não substitui o runtime jogável glibc,
nem integra a apresentação AHardwareBuffer ao jogo. O APK existente continua usando seu
runtime e drivers atuais. Não se deve publicar esta etapa como uma versão Bionic jogável.

Implementado:

- Carregador independente de libvulkan, com entradas ICD, Vulkan loader e Android HAL `HMI`.
- Pacote exato `Turnip_Gen8_V37_patch.zip`, de StevenMXZ, fixado por URL e SHA256.
- Seleção automática desse driver no verificador Bionic para chip KGSL `0x44xxxxxx`
  (Adreno Gen8), com escolha explícita `system` ou `gen8`.
- Compilação NDK/ARM64 do verificador, que cria uma instância e um dispositivo Vulkan e
  verifica AHardwareBuffer, queue-family foreign e semáforos SYNC_FD importáveis/exportáveis.
- Testes executáveis do carregador com bibliotecas ICD e HAL de teste, rejeição de falhas,
  seleção por GPU e rejeição de arquivos diferentes do pacote fixado.

O driver não deve ser selecionado automaticamente para Adreno 6xx/7xx, Mali ou GPU desconhecida.
Um driver personalizado solicitado que falha ao abrir não cai silenciosamente no driver do sistema.

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

## Validação desta revisão

- 81 testes Android passaram, incluindo 10 testes novos do experimento.
- Verificador e `kgsl-chip-id` compilados com NDK r29, ARM64, API 30.
- ELF do verificador usa `/system/bin/linker64` e depende de `libdl.so`, `libm.so`,
  `libc.so`; não depende de glibc, X11 ou libvulkan.
- ZIP local e download do release têm o mesmo SHA256 fixado no manifesto.
- Sem execução em hardware Android nesta etapa; desempenho e apresentação no jogo não validados.

## Próximas integrações necessárias

1. Compilar FEX/libgow3cpu, GPU/HLE e dependências com NDK/Bionic; adaptar os usos específicos
   de glibc em probe/runtime; usar uma única STL compartilhada.
2. Adaptar a apresentação Vulkan/AHardwareBuffer e o receptor Android com fences de aquisição
   e liberação. Substituir X11/Xvfb somente depois de validar o fluxo completo.
3. Portar a preparação nativa de executáveis e a instalação PKG para Bionic, preservando o
   comportamento específico de GoW III e os dados/configurações existentes.
4. Integrar inicialização, áudio, entrada, IME e configurações ao launcher. Conectar todos os
   usuários Vulkan ao mesmo carregador, sem misturar handles do driver customizado e do sistema.
5. Validar CUSA01715/01.02, patches de resolução/sombras/tempo, readbacks, coerência de texturas,
   sincronização, saves e desempenho antes de oferecer um APK experimental jogável.

Não copiar endereços, patches, hooks de câmera ou políticas de memória específicas do Bloodborne.
Não mudar driver ou readbacks do runtime glibc ao integrar esta etapa.

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
