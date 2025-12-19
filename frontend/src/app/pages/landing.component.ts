import { Component, inject } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { API_BASE_URL } from '../app.config';

type LobbyResponse = {
  lobbyCode: string;
  gameCode: string;
  status: string;
  myPlayerId: string;
  myBoardId: string;
  players: Array<{ id: string; username: string; boardId: string | null }>;
};

type GameDto = {
  gameCode: string;
  players: Array<{ id: string; username: string }>;
  boards: Array<{ id: string; width: number; height: number; ownerId: string; ownerUsername: string }>;
};

@Component({
  standalone: true,
  selector: 'app-landing',
  imports: [FormsModule, NgIf],
  templateUrl: './landing.component.html',
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
      .post<LobbyResponse>(`${API_BASE_URL}/lobbies/auto-join`, { username: name })
      .subscribe({
        next: (res) => {
          this.loadingQuick = false;
          this.router.navigate(['/game'], {
            queryParams: {
              gameCode: res.gameCode,
              playerId: res.myPlayerId,
              boardId: res.myBoardId,
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
      .post<GameDto>(`${API_BASE_URL}/games/${code}/join`, { username: name })
      .subscribe({
        next: (res) => {
          const me = res.players.find((p) => p.username === name);
          if (!me) {
            this.loadingJoin = false;
            this.errorJoin = 'Player nicht gefunden.';
            return;
          }
          const myBoard = res.boards.find((b) => b.ownerId === me.id);
          if (!myBoard) {
            this.loadingJoin = false;
            this.errorJoin = 'Board nicht gefunden.';
            return;
          }

          this.loadingJoin = false;
          this.router.navigate(['/game'], {
            queryParams: {
              gameCode: res.gameCode,
              playerId: me.id,
              boardId: myBoard.id,
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
