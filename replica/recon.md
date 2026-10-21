# Recon — Spotify (Android) como referência para o PlayMix v2

Objetivo: trazer para o PlayMix o **jeito de usar** do Spotify (navegação, telas, fluxos), tocando só os arquivos de música do próprio celular. Nada de código, logo, ícones, textos ou cores da marca do Spotify.

Fontes: conhecimento público do app (páginas oficiais, imprensa de tecnologia, fórum oficial da comunidade). Nada descompilado.

## Navegação principal

Barra inferior com 3 abas + mini player logo acima dela:
- **Início** — atalhos e carrosséis personalizados
- **Buscar** — busca + categorias em blocos coloridos
- **Sua Biblioteca** — playlists, artistas, álbuns, curtidas

## Telas

| # | Tela | O que tem |
|---|------|-----------|
| T1 | Início | Saudação; chips de filtro no topo; grade 2×4 de atalhos recentes; carrosséis ("Tocadas recentemente", mixes diários, "redescubra") |
| T2 | Buscar | Campo de busca; blocos de categorias (gêneros) coloridos; resultados agrupados (músicas, artistas, álbuns, playlists) |
| T3 | Biblioteca | Chips (Playlists / Artistas / Álbuns); ordenar (recentes, adicionados, A–Z); alternar lista/grade; itens fixados no topo; "Músicas Curtidas" |
| T4 | Playlist / Álbum | Capa grande com degradê da cor da capa; botão play + aleatório; lista de faixas; menu "…" por faixa |
| T5 | Artista | Cabeçalho com imagem; "Populares" (mais tocadas); álbuns |
| T6 | Tocando agora | Capa grande; título/artista; botão curtir; barra de progresso; aleatório, anterior, play/pausa, próxima, repetir; fila; cartão de letra ao rolar; fundo com cor da capa |
| T7 | Fila | "Tocando agora", "Próximas na fila" (adicionadas pelo usuário), "Próximas de: <origem>"; arrastar para reordenar; remover |
| T8 | Letra em tela cheia | Letra sincronizada destacando a linha atual |
| T9 | Menu de contexto | Adicionar à playlist, adicionar à fila, ir ao álbum, ir ao artista, curtir, compartilhar |
| T10 | Configurações de reprodução | Crossfade/transição, volume normalizado, equalizador, timer de soneca |
| T11 | Criar / editar playlist | Nome, capa automática em mosaico de 4 capas, adicionar músicas |

## Componentes recorrentes

Mini player (capa, título, play/pausa, linha de progresso, deslizar para trocar faixa) · chips de filtro · cartões de capa quadrados (playlist/álbum) e redondos (artista) · linha de faixa (capa pequena, título, artista, "…") · cabeçalho com degradê da capa · folha inferior (bottom sheet) para menus · notificação e tela de bloqueio com controles · widget.

## Fluxos principais

1. Abrir app → Início → tocar atalho recente
2. Biblioteca → playlist → tocar no modo aleatório
3. Buscar → digitar → tocar música
4. Faixa → "…" → adicionar à fila / à playlist
5. Mini player → Tocando agora → ver letra / abrir fila
6. Curtir música → aparece em "Músicas Curtidas"
7. Criar playlist → adicionar músicas
8. Ajustar transição/timer nas configurações

## Modelo de dados (adaptado a arquivos locais)

- **Track**: id, caminho/URI, título, artista, álbum, gênero, duração, BPM, capa, curtida, vezes tocada, última vez tocada, data adicionada
- **Album**, **Artist** (derivados das tags)
- **Playlist**: id, nome, capa (mosaico), fixada, criada em; **PlaylistTrack** (ordem)
- **PlayHistory**: track, data/hora, tocou até o fim? (alimenta Início e mixes)
- **QueueState**: fila atual, origem, posição, aleatório/repetir (restaurar ao reabrir)

## Fora do escopo (conteúdo ou rede do Spotify, não função)

Catálogo de streaming · podcasts e audiolivros · vídeos e Canvas · Jam, mensagens e atividade de amigos · DJ com voz de IA · anúncios · selos de verificado · recomendações vindas da nuvem.

Equivalentes locais possíveis: mixes do dia e "redescubra" gerados a partir do histórico de reprodução no próprio aparelho.

## Tamanho (estimativa honesta)

Médio-grande. O PlayMix já tem player, biblioteca por pasta, letras, equalizador, mixagem e Modo Treino, então boa parte do motor existe. O trabalho maior é a **casca no estilo Spotify**: navegação em 3 abas, Início personalizado, biblioteca com filtros, fila editável e playlists.

Primeira versão enxuta sugerida (v2.0): navegação + mini player + Tocando agora + fila + biblioteca com filtros + playlists + curtidas + busca + menu de contexto + timer de soneca. Depois (v2.1): Início com mixes do histórico, widget, Android Auto.
