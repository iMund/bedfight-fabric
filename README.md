# bedfight-fabric

Bed Fight — duelo de cama 1v1/2v2. Variante compacta de bedwars, sem geradores de recurso nem loja,
só ilha, cama e porrada. Mod Fabric, nascido pro servidor UBMC mas feito pra rodar em qualquer
servidor.

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

## Alocação de arenas e mapas

- Suporte a **múltiplos mapas** desde o início.
- Admin seleciona uma região com um item "varinha" do próprio mod (clique esquerdo/direito marca
  canto 1/2, estilo WorldEdit, sem depender de WorldEdit de verdade).
- Ponto de spawn de cada time: admin fica em pé no local e roda `/bedfight admin setspawn <mapId>
  azul|vermelho` — grava a posição **relativa ao canto 1 da região** (+ direção que está olhando), já
  que cada instância cola o mapa num offset de grid diferente.
- Captura: `/bedfight admin capturar <mapId>` — salva a região como **Structure Template NBT** e um
  JSON companheiro com os offsets/yaw de spawn de cada time.
- Instâncias vivem numa **dimensão dedicada** (fora do overworld de sobrevivência). Cada instância =
  o template de um mapa colado (`StructureTemplate.placeInWorld`) num offset de grid (ex: instância N
  em `x = N * 1000`). O pool de arenas escolhe um mapa (aleatório/rotação) entre os cadastrados por
  partida.
- Reset entre partidas = colar o template limpo de novo na mesma coordenada.
- Pool inicial: **4 instâncias simultâneas**, configurável. Se todas estiverem ocupadas quando a fila
  enche, os jogadores continuam esperando na fila com uma mensagem (não são impedidos de entrar).

## Fluxo completo de partida

1. Jogador roda `/bedfight` → chest GUI → escolhe 1v1 ou 2v2 → entra na fila daquele modo (filas não
   são compartilhadas).
2. Ao entrar na fila, é **teleportado na hora** pro spawn do time dele numa instância de arena
   alocada — sem lobby central. **Ainda sem kit.** Pode se mover livremente na ilha enquanto a fila
   não enche (1v1 precisa de 2 jogadores, 2v2 precisa de 4).
3. Fila enche → **kit entregue na hora** pra todo mundo, começa contagem de **5 segundos**. Durante
   esse tempo os jogadores ficam **congelados** (sem mover, sem colocar bloco, sem PvP).
4. Contagem termina → libera movimento, colocar bloco e PvP juntos. Esse é o único período de
   congelamento/graça — não se repete a cada respawn.
5. Durante a partida, só é possível **quebrar** a cama, a camada de madeira ao redor e a camada de
   end stone ao redor da madeira — nenhum outro bloco do mapa é quebrável. Blocos minerados da cama
   inimiga ficam no inventário como recurso. Pode **colocar** qualquer bloco do inventário.
6. **Morte com a própria cama viva**: perde o inventário atual (reseta), fica ~3s (valor exato a
   configurar depois) em modo espectador, e respawna com o kit padrão de novo.
7. **Morte com a própria cama destruída** (eliminação final, relevante no 2v2): vira **espectador da
   partida até ela terminar** — não é kickado da arena na hora.
8. **Desconexão durante a partida**: o personagem é removido da arena na hora. Se reconectar
   **enquanto a partida ainda está rolando**, volta e entra de novo no jogo. Se reconectar depois que
   a partida já terminou, não volta pra ela — segue o fluxo normal de lobby.
9. **Fim de partida**: jogador recebe dois itens especiais na hotbar — uma **cama** (último slot,
   clique direito → volta pro lobby na hora) e um **papel** (primeiro slot, clique → entra de novo na
   fila pra jogar outra partida). Se não fizer nenhuma das duas opções em até **3 segundos**, é
   levado automaticamente pro lobby.

## Em aberto

- Como a alocação de instância se conecta com a fila no código (mensagem de espera quando todas as 4
  instâncias estão ocupadas).
- Nenhum código de gameplay ainda existe (fila, GUI, pool de arenas, lógica de cama, kit, fluxo de
  partida acima) — só o scaffold vazio do mod.
