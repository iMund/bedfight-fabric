# bedfight-fabric

Bed Fight - minigame 1v1 e 2v2. Mod Fabric.

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
  azul|vermelho` — grava a posição **relativa ao canto mínimo da seleção** (+ direção que está
  olhando), já que cada instância cola o mapa num offset de grid diferente. Precisa da seleção da
  varinha ainda ativa (mesma região) quando roda o comando.
- Captura: `/bedfight admin capturar <mapId>` — salva a região como **Structure Template NBT** e um
  `map.yml` companheiro com o tamanho da região e os offsets/yaw de spawn de cada time.
- `/bedfight admin wand` entrega a varinha (machado dourado renomeado — mesma convenção do WorldEdit):
  clique esquerdo marca o canto 1, clique direito marca o canto 2, sem quebrar/interagir de verdade
  com o bloco.
- Instâncias vivem numa **dimensão dedicada** (`bedfight:arena`, void/flat, registrada via datapack
  embutido no mod — sem geração de terreno, já que todo cenário vem dos mapas colados). Cada instância =
  o template de um mapa colado (`StructureTemplate.placeInWorld`) num offset de grid (ex: instância N
  em `x = N * 1000`). O pool de arenas escolhe um mapa (aleatório/rotação) entre os cadastrados por
  partida.
- Reset entre partidas = colar o template limpo de novo na mesma coordenada (já implementado: colar é
  sempre a mesma operação, seja a primeira vez ou um reset).
- Pool inicial: **4 instâncias simultâneas**, configurável. Se todas estiverem ocupadas quando a fila
  enche, os jogadores continuam esperando na fila com uma mensagem (não são impedidos de entrar).
- `/bedfight admin testarena <instancia> <mapId>` cola um mapa numa instância e teleporta o admin pro
  spawn do time azul — comando de verificação manual, não faz parte do fluxo de partida.

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
   end stone ao redor da madeira — nenhum outro bloco do mapa é quebrável. **Implementado**: ao colar
   um mapa numa instância, o mod escaneia a estrutura procurando cama (`#minecraft:beds`), depois
   preenchimento por adjacência através de madeira (`#minecraft:planks`/`#minecraft:logs`) e depois
   end stone — só essas posições ficam quebráveis (`PlayerBlockBreakEvents.BEFORE` nega o resto dentro
   da dimensão `bedfight:arena`). Blocos minerados da cama inimiga ficam no inventário como recurso.
   Pode **colocar** qualquer bloco do inventário.
6. **Morte com a própria cama viva**: perde o inventário atual (reseta), fica ~3s (valor exato a
   configurar depois) em modo espectador, e respawna com o kit padrão de novo. `KitService.giveKit`
   já entrega o kit fixo do `kit.yml` (limpa inventário, equipa armadura nos slots certos, aplica
   encantamentos) — falta ligar isso ao momento certo do fluxo de partida (contagem/respawn), hoje só
   dá pra testar via `/bedfight admin testkit`.
7. **Morte com a própria cama destruída** (eliminação final, relevante no 2v2): vira **espectador da
   partida até ela terminar** — não é kickado da arena na hora.
8. **Desconexão durante a partida**: o personagem é removido da arena na hora. Se reconectar
   **enquanto a partida ainda está rolando**, volta e entra de novo no jogo. Se reconectar depois que
   a partida já terminou, não volta pra ela — segue o fluxo normal de lobby.
9. **Fim de partida**: jogador recebe dois itens especiais na hotbar — uma **cama** (último slot,
   clique direito → volta pro lobby na hora) e um **papel** (primeiro slot, clique → entra de novo na
   fila pra jogar outra partida). Se não fizer nenhuma das duas opções em até **3 segundos**, é
   levado automaticamente pro lobby.

## Configuração

Tudo em YAML (comentado, editável à mão), em `config/bedfight/` na pasta do servidor:

- `arena.yml` — dimensão dedicada, tamanho do pool de instâncias, espaçamento do grid.
- `kit.yml` — lista do kit fixo (item, quantidade, encantamentos opcionais).
- `match.yml` — tempos da partida (contagem regressiva, delay de respawn, janela de escolha no fim).
- `maps/<mapId>/` — cada mapa capturado tem sua própria pasta com `map.yml` (tamanho da região,
  offsets/yaw de spawn de cada time) e `structure.nbt` (a estrutura capturada). Gerado pelo fluxo
  varinha → `setspawn` → `capturar` descrito acima.

Um arquivo só é escrito a partir do default embutido no mod se ainda não existir em disco — o mod
nunca sobrescreve uma config já editada. `./gradlew runServer` gera os arquivos automaticamente na
primeira execução.

## PvP

Combate estilo 1.8: sem cooldown de ataque do 1.9+ (dano cheio em todo clique) e sem escudo. Vale só
dentro da dimensão da arena, via mixin — ainda não implementado.

## Em aberto

- Como a alocação de instância se conecta com a fila no código (mensagem de espera quando todas as 4
  instâncias estão ocupadas) — a escolha aleatória/rotação de mapa entre os cadastrados também não
  está implementada, hoje `testarena` exige informar o mapId manualmente.
- PvP estilo 1.8 (ver acima), fila, GUI, lógica de cama, kit-on-join e o resto do fluxo de partida
  ainda não implementados.
- **Não testado com cliente real** (mesma limitação que o ubmcrpg tinha na migração): a captura e a
  colagem de estrutura foram verificadas por build limpo, boot limpo do servidor e conferência das
  assinaturas de API contra o jar real, mas ninguém ainda construiu um mapa de verdade, capturou e
  colou numa instância pra confirmar visualmente o resultado.
