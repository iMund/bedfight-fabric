# bedfight-fabric

Minigame "Bed Fight" para o servidor UBMC — variante compacta de bedwars (1v1/2v2), sem geradores de
recurso nem loja. Mod Fabric separado do `ubmcrpg`.

## Design confirmado

- Duas ilhas, dois times: **azul** e **vermelho**. Modos **1v1** e **2v2**.
- Sem gerador de recurso, sem vendedor/loja — nenhuma camada de economia.
- Cada cama é protegida por duas camadas de bloco: **madeira**, depois **end stone** (precisa minerar
  as duas pra chegar na cama).
- Mecânica de cama: **cama viva → respawna ao morrer; cama destruída → próxima morte do time é
  eliminação final**.
- Kit fixo pra todos: armadura de couro completa, espada de madeira, 1 stack de lã, tesoura, picareta
  de madeira (Eficiência 1), machado de madeira (Eficiência 1).
- Filas **separadas** para 1v1 e 2v2.
- Entrada: comando `/bedfight` abre uma chest GUI pra escolher o modo (1v1 ou 2v2).

## Alocação de arenas

- Arena construída **uma vez** à mão (creative), capturada como **Structure Template NBT** (vanilla),
  empacotada como recurso do mod.
- Instâncias vivem numa **dimensão dedicada** (fora do overworld de sobrevivência).
- Cada instância = o template colado (`StructureTemplate.placeInWorld`) num offset de grid
  (ex: instância N em `x = N * 1000`). Gerar mais instâncias é só colar o template em outro offset.
- Reset entre partidas = colar o template limpo de novo na mesma coordenada.
- Pool inicial: **4 instâncias simultâneas**, configurável.

## Em aberto

- Como a alocação de instância se conecta com a fila no código (o que acontece com todas as 4
  ocupadas).
- Captura do structure: manual (structure block do vanilla) ou comando admin do próprio mod.
- Scaffold do projeto (toolchain, versão do MC, estrutura de pacotes) — ainda não definido.
