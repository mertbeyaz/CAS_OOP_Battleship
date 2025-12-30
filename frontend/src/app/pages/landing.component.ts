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
          this.router.navigate(['/game'], {
            queryParams: {
              gameCode: res.gameCode,
              playerId: res.myPlayerId,
              playerName: res.myPlayerName,
            },
            state: {
              myBoard: res.myBoard,
            },
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
    const name = this.usernameJoin.trim();
    if (!code || !name) {
      this.errorJoin = 'Bitte Game Code und Username eingeben.';
      return;
    }
    this.loadingJoin = true;
    this.errorJoin = '';

    this.http
      .post<JoinGameResponseDto>(`${API_BASE_URL}/games/${code}/join`, { username: name })
      .subscribe({
        next: (res) => {
          this.loadingJoin = false;
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
