import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import {NgIf, NgFor, NgClass} from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { API_BASE_URL } from '../app.config';
import { catchError, finalize, throwError } from 'rxjs';

type GameDto = {
  id: string;
  gameCode: string;
  status: 'WAITING' | 'SETUP' | 'READY' | 'RUNNING' | 'PAUSED' | 'FINISHED';
  boardWidth: number;
  boardHeight: number;
  currentTurnPlayerId: string | null;
  winnerPlayerId: string | null;
  players: Array<{ id: string; username: string }>;
  boards: Array<{ id: string; width: number; height: number; ownerId: string; ownerUsername: string }>;
};

type BoardStateDto = {
  boardId: string;
  width: number;
  height: number;
  ownerId: string;
  ownerUsername: string;
  locked: boolean;
  ships: Array<{ type: string; startX: number; startY: number; orientation: 'HORIZONTAL' | 'VERTICAL'; size: number }>;
  shotsOnThisBoard: Array<{ x: number; y: number; result: 'MISS' | 'HIT' | 'SUNK' | 'ALREADY_SHOT' }>;
};

type CellState = 'empty' | 'ship' | 'miss' | 'hit' | 'sunk';

@Component({
  standalone: true,
  selector: 'app-game',
  imports: [NgIf, NgFor, NgClass],
  templateUrl: './game.component.html',
  styleUrls: ['./game.component.scss'],
})
export class GameComponent implements OnInit {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private cdr = inject(ChangeDetectorRef);

  gameCode = '';
  myPlayerId = '';
  myBoardId = '';
  game?: GameDto;
  opponentBoardId = '';

  loading = false;
  readyLoading = false;
  readyDone = false;
  shotLoading = false;
  error = '';
  shotError = '';

  myBoardState?: BoardStateDto;
  opponentBoardState?: BoardStateDto;

  myShipCoords = new Set<string>();
  myHits = new Map<string, CellState>();
  myMisses = new Set<string>();
  oppHits = new Map<string, CellState>();
  oppMisses = new Set<string>();

  get myBoard() {
    return this.game?.boards.find((b) => b.id === this.myBoardId);
  }

  get opponentBoard() {
    return this.game?.boards.find((b) => b.id === this.opponentBoardId);
  }

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((params) => {
      this.gameCode = params.get('gameCode') ?? '';
      this.myPlayerId = params.get('playerId') ?? '';
      this.myBoardId = params.get('boardId') ?? '';
      if (this.gameCode) {
        this.loadGame();
      } else {
        this.error = 'Fehlender gameCode.';
      }
    });
  }

  loadGame() {
    this.loading = true;
    this.error = '';
    this.http
      .get<GameDto>(`${API_BASE_URL}/games/${this.gameCode}`)
      .pipe(
        catchError((err: HttpErrorResponse) => {
          console.error('GET /games failed', err, 'body:', err.error);
          const status = err.status ? `Status ${err.status}` : 'keine Antwort';
          this.error = `Spiel konnte nicht geladen werden (${status}: ${err.message})`;
          return throwError(() => err);
        }),
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (g) => {
          this.game = g;
          this.setOpponentBoard();
          if (g.status === 'RUNNING') this.readyDone = true;
          this.loadBoardStates();
        },
      });
  }

  loadBoardStates() {
    if (this.myBoardId) {
      this.fetchBoardState(this.myBoardId, true);
    }
    if (this.opponentBoardId) {
      this.fetchBoardState(this.opponentBoardId, false);
    }
  }

  fetchBoardState(boardId: string, isMine: boolean) {
    this.http.get<BoardStateDto>(`${API_BASE_URL}/games/${this.gameCode}/boards/${boardId}/state`).subscribe({
      next: (state) => {
        if (isMine) {
          this.myBoardState = state;
          this.buildMyBoardMaps();
        } else {
          this.opponentBoardState = state;
          this.buildOpponentMaps();
        }
      },
      error: (err) => console.error('Board state failed', err),
    });
  }

  buildMyBoardMaps() {
    if (!this.myBoardState) return;
    this.myShipCoords.clear();
    this.myHits.clear();
    this.myMisses.clear();

    for (const s of this.myBoardState.ships) {
      for (let i = 0; i < s.size; i++) {
        const x = s.orientation === 'HORIZONTAL' ? s.startX + i : s.startX;
        const y = s.orientation === 'VERTICAL' ? s.startY + i : s.startY;
        this.myShipCoords.add(`${x},${y}`);
      }
    }
    for (const shot of this.myBoardState.shotsOnThisBoard) {
      const key = `${shot.x},${shot.y}`;
      if (shot.result === 'MISS') {
        this.myMisses.add(key);
      } else if (shot.result === 'HIT') {
        this.myHits.set(key, 'hit');
      } else if (shot.result === 'SUNK') {
        this.myHits.set(key, 'sunk');
      }
    }
  }

  buildOpponentMaps() {
    if (!this.opponentBoardState) return;
    this.oppHits.clear();
    this.oppMisses.clear();
    for (const shot of this.opponentBoardState.shotsOnThisBoard) {
      const key = `${shot.x},${shot.y}`;
      if (shot.result === 'MISS') {
        this.oppMisses.add(key);
      } else if (shot.result === 'HIT') {
        this.oppHits.set(key, 'hit');
      } else if (shot.result === 'SUNK') {
        this.oppHits.set(key, 'sunk');
      }
    }
  }

  setOpponentBoard() {
    if (!this.game) return;
    const opp = this.game.boards.find((b) => b.ownerId !== this.myPlayerId);
    this.opponentBoardId = opp?.id ?? '';
  }

  isSetup() {
    return this.game?.status === 'SETUP';
  }

  isMyTurn() {
    return this.game?.status === 'RUNNING' && this.game?.currentTurnPlayerId === this.myPlayerId;
  }

  canShoot() {
    return !!this.opponentBoard && this.isMyTurn() && !this.shotLoading;
  }

  markReady() {
    if (!this.isSetup() || !this.myPlayerId) return;
    this.readyLoading = true;
    this.error = '';
    this.http
      .post<GameDto>(`${API_BASE_URL}/games/${this.gameCode}/players/${this.myPlayerId}/board/confirm`, {})
      .subscribe({
        next: (g) => {
          this.game = g;
          this.readyDone = true;
          this.readyLoading = false;
        },
        error: () => {
          this.readyLoading = false;
          this.error = 'Ready fehlgeschlagen.';
        },
      });
  }

  fireShot(x: number, y: number) {
    if (!this.opponentBoard || !this.canShoot()) return;
    this.shotLoading = true;
    this.shotError = '';
    this.http
      .post<GameDto>(
        `${API_BASE_URL}/games/${this.gameCode}/boards/${this.opponentBoard.id}/shots`,
        { shooterId: this.myPlayerId, x, y }
      )
      .subscribe({
        next: (g) => {
          this.game = g;
          this.setOpponentBoard();
          this.shotLoading = false;
          this.loadBoardStates(); // nach Schuss neu laden, um Treffer/Miss/Sunk zu sehen
        },
        error: (err) => {
          console.error('Shot failed', err);
          this.shotError = 'Schuss fehlgeschlagen.';
          this.shotLoading = false;
        },
      });
  }

  playerName(id: string | null) {
    if (!id || !this.game) return '';
    return this.game.players.find((p) => p.id === id)?.username ?? '';
  }

  cells(n: number) {
    return Array.from({ length: n });
  }

  cellLabel(index: number, width: number) {
    const x = index % width;
    const y = Math.floor(index / width);
    return `${x},${y}`;
  }

  cellStateForOpp(x: number, y: number): CellState {
    const key = `${x},${y}`;
    if (this.oppHits.has(key)) return this.oppHits.get(key)!; // hit oder sunk
    if (this.oppMisses.has(key)) return 'miss';
    return 'empty';
  }

  cellStateForMine(x: number, y: number): CellState {
    const key = `${x},${y}`;
    if (this.myHits.has(key)) return this.myHits.get(key)!; // hit oder sunk
    if (this.myMisses.has(key)) return 'miss';
    if (this.myShipCoords.has(key)) return 'ship';
    return 'empty';
  }

  protected readonly Math = Math;
}
