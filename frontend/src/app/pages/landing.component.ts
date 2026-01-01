import { Component, inject } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { API_BASE_URL } from '../app.config';

type LobbyDto = {
  lobbyCode: string;
  gameCode: string;
  status: string;
  myPlayerId: string;
  myPlayerName: string;
  myBoard?: {
    boardId: string;
    width: number;
    height: number;
    locked: boolean;
    shipPlacements: Array<{
      type: string;
      startX: number;
      startY: number;
      orientation: 'HORIZONTAL' | 'VERTICAL';
      size: number;
    }>;
  } | null;
};

type JoinGameResponseDto = {
  gameCode: string;
  playerId: string;
  playerName: string;
  status: 'WAITING' | 'SETUP' | 'RUNNING' | 'PAUSED' | 'FINISHED';
};

type GamePublicDto = {
  gameCode: string;
  status: 'WAITING' | 'SETUP' | 'RUNNING' | 'PAUSED' | 'FINISHED';
  yourBoardLocked: boolean;
  opponentBoardLocked: boolean;
  yourTurn: boolean;
  opponentName: string | null;
};

type ResumeResponseDto = {
  status: string;
  snapshot?: {
    youName?: string;
  };
};

@Component({
  standalone: true,
  selector: 'app-landing',
  imports: [FormsModule, NgIf],
  templateUrl: './landing.component.html',
  styleUrls: ['./landing.component.scss'],
})
export class LandingComponent {
  private http = inject(HttpClient);
  private router = inject(Router);

  usernameQuick = '';
  loadingQuick = false;
  errorQuick = '';

  gameCode = '';
  usernameJoin = '';
  loadingJoin = false;
  errorJoin = '';

  private playerKey(gameCode: string, username: string) {
    return `battleship:${gameCode}:${username}`;
  }

  private savePlayer(gameCode: string, username: string, playerId: string, playerName: string) {
    localStorage.setItem(this.playerKey(gameCode, username), JSON.stringify({ playerId, playerName }));
  }

  private loadPlayer(gameCode: string, username: string): { playerId: string; playerName: string } | null {
    const raw = localStorage.getItem(this.playerKey(gameCode, username));
    return raw ? JSON.parse(raw) : null;
  }

  quickPlay() {
    const name = this.usernameQuick.trim();
    if (!name) {
      this.errorQuick = 'Bitte Username eingeben.';
      return;
    }
    this.loadingQuick = true;
    this.errorQuick = '';

    this.http
      .post<LobbyDto>(`${API_BASE_URL}/lobbies/auto-join`, { username: name })
      .subscribe({
        next: (res) => {
          this.loadingQuick = false;
          this.savePlayer(res.gameCode, res.myPlayerName, res.myPlayerId, res.myPlayerName);
          this.router.navigate(['/game'], {
            queryParams: {
              gameCode: res.gameCode,
              playerId: res.myPlayerId,
              playerName: res.myPlayerName,
              lobbyCode: res.lobbyCode,
            },
            state: { myBoard: res.myBoard },
          });
        },
        error: () => {
          this.loadingQuick = false;
          this.errorQuick = 'Beitreten fehlgeschlagen.';
        },
      });
  }

  joinByCode() {
    const code = this.gameCode.trim();
    const input = this.usernameJoin.trim();
    if (!code || !input) {
      this.errorJoin = 'Bitte Game Code und Username eingeben.';
      return;
    }
    this.loadingJoin = true;
    this.errorJoin = '';

    // 1) Wenn Input UUID → direkt resume
    const uuidRe = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
    if (uuidRe.test(input)) {
      this.http
        .get<GamePublicDto>(`${API_BASE_URL}/games/${code}?playerId=${input}`)
        .subscribe({
          next: (g) => {
            if (g.status !== 'PAUSED') {
              this.loadingJoin = false;
              this.errorJoin = 'Resume nur möglich, wenn Spiel PAUSED ist.';
              return;
            }

            this.http
              .post<ResumeResponseDto>(`${API_BASE_URL}/games/${code}/resume`, { playerId: input })
              .subscribe({
                next: (res) => {
                  const nameFromResume = res.snapshot?.youName ?? input;
                  this.loadingJoin = false;
                  this.router.navigate(['/game'], {
                    queryParams: { gameCode: code, playerId: input, playerName: nameFromResume },
                  });
                },
                error: () => {
                  this.loadingJoin = false;
                  this.errorJoin = 'Resume fehlgeschlagen.';
                },
              });
          },
          error: () => {
            this.loadingJoin = false;
            this.errorJoin = 'Spiel konnte nicht geladen werden.';
          },
        });

      return;
    }


    // 2) Falls Spieler bekannt (LocalStorage) → Status prüfen
    const existing = this.loadPlayer(code, input);
    if (existing) {
      this.http
        .get<GamePublicDto>(`${API_BASE_URL}/games/${code}?playerId=${existing.playerId}`)
        .subscribe({
          next: (g) => {
            if (g.status === 'PAUSED') {
              this.http
                .post<GamePublicDto>(`${API_BASE_URL}/games/${code}/resume`, { playerId: existing.playerId })
                .subscribe({
                  next: () => {
                    this.loadingJoin = false;
                    this.router.navigate(['/game'], {
                      queryParams: {
                        gameCode: code,
                        playerId: existing.playerId,
                        playerName: existing.playerName,
                      },
                    });
                  },
                  error: () => {
                    this.loadingJoin = false;
                    this.errorJoin = 'Resume fehlgeschlagen.';
                  },
                });
            } else {
              this.loadingJoin = false;
              this.router.navigate(['/game'], {
                queryParams: {
                  gameCode: code,
                  playerId: existing.playerId,
                  playerName: existing.playerName,
                },
              });
            }
          },
          error: () => {
            this.loadingJoin = false;
            this.errorJoin = 'Spiel konnte nicht geladen werden.';
          },
        });
      return;
    }

    // 3) Normaler Join
    this.http
      .post<JoinGameResponseDto>(`${API_BASE_URL}/games/${code}/join`, { username: input })
      .subscribe({
        next: (res) => {
          this.loadingJoin = false;
          this.savePlayer(res.gameCode, res.playerName, res.playerId, res.playerName);
          this.router.navigate(['/game'], {
            queryParams: {
              gameCode: res.gameCode,
              playerId: res.playerId,
              playerName: res.playerName,
            },
          });
        },
        error: () => {
          this.loadingJoin = false;
          this.errorJoin = 'Beitreten fehlgeschlagen.';
        },
      });
  }
}
