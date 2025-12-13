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
  boards: Array<{ id: string; owner: { id: string; username: string } | null }>;
};

@Component({
  standalone: true,
  selector: 'app-landing',
  imports: [FormsModule, NgIf],
  template: `
    <main class="landing">
      <h1>Battleship</h1>

      <section>
        <h2>Schnelles Spiel</h2>
        <form (ngSubmit)="quickPlay()" #fQuick="ngForm">
          <label>
            Username
            <input
              name="usernameQuick"
              [(ngModel)]="usernameQuick"
              required
              minlength="2"
              autocomplete="off"
              [disabled]="loadingQuick"
            />
          </label>
          <button type="submit" [disabled]="loadingQuick || !fQuick.valid">
            {{ loadingQuick ? 'Verbinde...' : 'Schnelles Spiel starten' }}
          </button>
        </form>
        <p *ngIf="errorQuick" class="error">{{ errorQuick }}</p>
      </section>

      <section>
        <h2>Spiel beitreten</h2>
        <form (ngSubmit)="joinByCode()" #fJoin="ngForm">
          <label>
            Game Code
            <input
              name="gameCode"
              [(ngModel)]="gameCode"
              required
              autocomplete="off"
              [disabled]="loadingJoin"
            />
          </label>
          <label>
            Username
            <input
              name="usernameJoin"
              [(ngModel)]="usernameJoin"
              required
              minlength="2"
              autocomplete="off"
              [disabled]="loadingJoin"
            />
          </label>
          <button type="submit" [disabled]="loadingJoin || !fJoin.valid">
            {{ loadingJoin ? 'Tritt bei...' : 'Beitreten' }}
          </button>
        </form>
        <p *ngIf="errorJoin" class="error">{{ errorJoin }}</p>
      </section>
    </main>
  `,
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
          // Player und Board finden
          const me = res.players.find((p) => p.username === name);
          if (!me) {
            this.loadingJoin = false;
            this.errorJoin = 'Player nicht gefunden.';
            return;
          }
          const myBoard = res.boards.find((b) => b.owner && b.owner.id === me.id);
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
