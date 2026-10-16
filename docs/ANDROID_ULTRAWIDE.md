# 21:9 no Android — beta

Selecione uma opção 21:9 em **Resolução de renderização**, na tela inicial ou nas
configurações durante o jogo. A alteração exige reiniciar o jogo.

| Resolução | Proporção | Pixels |
| --- | --- | ---: |
| 840×360 | 21:9 | 302.400 |
| 1120×480 | 21:9 | 537.600 |
| 1260×540 | 21:9 | 680.400 |
| 1680×720 | 21:9 | 1.209.600 |

Os patches foram adaptados de [FelipePrado0/gow3_pc, PR #202](https://github.com/FelipePrado0/gow3_pc/pull/202)
e dos presets integrados pelo PR #458. Alteram as dimensões, a proporção do vídeo
e quatro pontos de câmera/culling. O fator Hor+ é 21/16: mantém a visão vertical
original e amplia a horizontal, em vez de cortar a imagem em cima e embaixo.

As assinaturas, instruções originais e saltos foram validados no executável
CUSA01715 01.02 de SHA-256
`6e72330e9092e5164049b865e5568f2d450904001a73fe0a2f4b39194c195b3c`.
Isso valida a aplicação do patch; a aparência e o desempenho no Android ainda
precisam ser confirmados em jogo. Uma assinatura incompatível rejeita o patch
inteiro, incluindo os ajustes de câmera.

O aumento da arena continua independente. Ativado usa 2,25 GiB nestes presets;
desativado mantém a arena original. A taxa de quadros também é independente.

A apresentação Android respeita a proporção selecionada. As cutscenes mantêm
seu quadro 16:9, com barras pretas nas laterais. O modo de preenchimento de tela
continua disponível para quem quiser esticar a imagem à proporção do aparelho.

Limitações herdadas do patch original: partes dos menus, HUD e QTE permanecem
no layout 16:9; o escurecimento dos menus pode não cobrir as laterais; logos de
abertura podem ficar esticados e pode ocorrer um breve flash nas transições de
câmera. Consulte [a documentação original](https://github.com/FelipePrado0/gow3_pc/blob/main/docs/ultrawide.md).
