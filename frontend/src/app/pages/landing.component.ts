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

type ResumeResponseDto = {
  status: string;
  snapshot?: {
    youName?: string;
  };
};

type ResumePlayer = {
  gameCode: string;
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

  // Resume by token form state
  resumeTokenJoin = '';
  loadingJoin = false;
  errorJoin = '';


  // ----------------------------
  // Local storage helpers
  // ----------------------------
  private resumePlayerKey(token: string) {
    return `resumePlayer:${token}`;
  }

  private saveResumePlayer(resumeToken?: string, gameCode?: string, playerId?: string, playerName?: string) {
    if (!resumeToken || !gameCode || !playerId || !playerName) return;
    localStorage.setItem(
      this.resumePlayerKey(resumeToken),
      JSON.stringify({ gameCode, playerId, playerName })
    );
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
          this.saveResumePlayer(res.resumeToken, res.gameCode, res.myPlayerId, res.myPlayerName);

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
   * Resume by resumeToken (only if PAUSED or WAITING).
   */
  joinByCode() {
    const token = this.resumeTokenJoin.trim();

    if (!token) {
      this.errorJoin = 'Bitte Resume-Token eingeben.';
      return;
    }

    this.loadingJoin = true;
    this.errorJoin = '';

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
          if (res.status !== 'PAUSED' && res.status !== 'WAITING') {
            this.loadingJoin = false;
            this.errorJoin = 'Resume nur moeglich, wenn Spiel PAUSED oder WAITING ist.';
            return;
          }

          this.loadingJoin = false;
          this.router.navigate(['/game'], {
            queryParams: {
              gameCode: resumePlayer.gameCode,
              playerId: resumePlayer.playerId,
              playerName: resumePlayer.playerName,
              resumeToken: token,
            },
          });
        },
        error: () => {
          this.loadingJoin = false;
          this.errorJoin = 'Resume nur moeglich, wenn Spiel PAUSED ist.';
        },
      });
  }
}
