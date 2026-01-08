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
  resumeToken?: string;
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

type ResumePlayer = {
  playerId: string;
  playerName: string;
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

  // Quick Play form state
  usernameQuick = '';
  loadingQuick = false;
  errorQuick = '';

  // Join by code form state
  gameCode = '';
  usernameJoin = '';
  resumeTokenJoin = '';
  loadingJoin = false;
  errorJoin = '';

  // ----------------------------
  // Local storage helpers
  // ----------------------------
  private playerKey(gameCode: string, username: string) {
    return `battleship:${gameCode}:${username}`;
  }

  private tokenKey(gameCode: string) {
    return `resume:${gameCode}`;
  }

  private resumePlayerKey(token: string) {
    return `resumePlayer:${token}`;
  }

  private savePlayer(gameCode: string, username: string, playerId: string, playerName: string) {
    localStorage.setItem(this.playerKey(gameCode, username), JSON.stringify({ playerId, playerName }));
  }

  private saveResumeToken(gameCode: string, resumeToken?: string) {
    if (!resumeToken) return;
    localStorage.setItem(this.tokenKey(gameCode), resumeToken);
  }

  private saveResumePlayer(resumeToken?: string, playerId?: string, playerName?: string) {
    if (!resumeToken || !playerId || !playerName) return;
    localStorage.setItem(this.resumePlayerKey(resumeToken), JSON.stringify({ playerId, playerName }));
  }

  private loadPlayer(gameCode: string, username: string): ResumePlayer | null {
    const raw = localStorage.getItem(this.playerKey(gameCode, username));
    return raw ? JSON.parse(raw) : null;
  }

  private loadResumePlayer(resumeToken: string): ResumePlayer | null {
    const raw = localStorage.getItem(this.resumePlayerKey(resumeToken));
    return raw ? JSON.parse(raw) : null;
  }

  // ----------------------------
  // Actions
  // ----------------------------
  /**
   * Starts quick play by auto-joining a lobby and navigating to the game view.
   */
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
          this.saveResumeToken(res.gameCode, res.resumeToken);
          this.saveResumePlayer(res.resumeToken, res.myPlayerId, res.myPlayerName);

          this.router.navigate(['/game'], {
            queryParams: {
              gameCode: res.gameCode,
              playerId: res.myPlayerId,
              playerName: res.myPlayerName,
              lobbyCode: res.lobbyCode,
              resumeToken: res.resumeToken,
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

  /**
   * Join by:
   * - gameCode + resumeToken (resume, only if PAUSED)
   * - gameCode + username (normal join)
   */
  joinByCode() {
    const code = this.gameCode.trim();
    const name = this.usernameJoin.trim();
    const token = this.resumeTokenJoin.trim();

    if (!code) {
      this.errorJoin = 'Bitte Game Code eingeben.';
      return;
    }

    this.loadingJoin = true;
    this.errorJoin = '';

    // 1) Resume by token
    if (token) {
      this.http
        .post<ResumeResponseDto>(`${API_BASE_URL}/games/resume`, { token })
        .subscribe({
          next: (res) => {
            const resumePlayer = this.loadResumePlayer(token);
            if (!resumePlayer) {
              this.loadingJoin = false;
              this.errorJoin = 'Resume-Token unbekannt. Bitte auf demselben Browser/Device starten.';
              return;
            }

            this.loadingJoin = false;
            this.router.navigate(['/game'], {
              queryParams: {
                gameCode: code,
                playerId: resumePlayer.playerId,
                playerName: resumePlayer.playerName,
                resumeToken: token,
              },
            });
          },
          error: () => {
            this.loadingJoin = false;
            this.errorJoin = 'Resume nur möglich, wenn Spiel PAUSED ist.';
          },
        });

      return;
    }

    // 2) Normal join by username
    if (!name) {
      this.loadingJoin = false;
      this.errorJoin = 'Bitte Username eingeben.';
      return;
    }

    const existing = this.loadPlayer(code, name);
    if (existing) {
      this.http
        .get<GamePublicDto>(`${API_BASE_URL}/games/${code}?playerId=${existing.playerId}`)
        .subscribe({
          next: (g) => {
            if (g.status === 'PAUSED') {
              const savedToken = localStorage.getItem(this.tokenKey(code));
              if (!savedToken) {
                this.loadingJoin = false;
                this.errorJoin = 'Resume-Token fehlt.';
                return;
              }

              this.http
                .post<ResumeResponseDto>(`${API_BASE_URL}/games/resume`, { token: savedToken })
                .subscribe({
                  next: () => {
                    this.loadingJoin = false;
                    this.router.navigate(['/game'], {
                      queryParams: {
                        gameCode: code,
                        playerId: existing.playerId,
                        playerName: existing.playerName,
                        resumeToken: savedToken,
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

    this.http
      .post<JoinGameResponseDto>(`${API_BASE_URL}/games/${code}/join`, { username: name })
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
