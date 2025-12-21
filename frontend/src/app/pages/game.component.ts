import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  NgZone,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
} from '@angular/core';
import { NgIf, NgFor, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { catchError, finalize, throwError } from 'rxjs';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_BASE_URL } from '../app.config';

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

type GameEventDto = {
  type: string;
  payload: Record<string, any>;
};

type ChatDto = { senderId: string; senderName: string; gameCode: string; message: string; timestamp: string };
type CellState = 'empty' | 'ship' | 'miss' | 'hit' | 'sunk';

@Component({
  standalone: true,
  selector: 'app-game',
  imports: [NgIf, NgFor, NgClass, FormsModule],
  templateUrl: './game.component.html',
  styleUrls: ['./game.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GameComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private cdr = inject(ChangeDetectorRef);
  private zone = inject(NgZone);

  @ViewChild('messagesContainer') private messagesEl?: ElementRef<HTMLDivElement>;

  gameCode = '';
  myPlayerId = '';
  myBoardId = '';
  game?: GameDto;
  readyPlayers = new Set<string>();

  autoLoading = false; //autoPlacement
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
  forfeitLoading = false;

  private stomp?: Client;
  private eventSub?: StompSubscription;
  private chatSub?: StompSubscription;

  chatMessages: ChatDto[] = [];
  chatInput = '';

  get myBoard() {
    return this.game?.boards.find((b) => b.id === this.myBoardId);
  }
  get opponentBoard() {
    return this.game?.boards.find((b) => b.ownerId !== this.myPlayerId);
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

  ngOnDestroy(): void {
    this.eventSub?.unsubscribe();
    this.chatSub?.unsubscribe();
    this.stomp?.deactivate();
  }

  private scrollChatToBottom() {
    requestAnimationFrame(() => {
      const el = this.messagesEl?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
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
          this.cdr.markForCheck();
        })
      )
      .subscribe({
        next: (g) => {
          this.game = g;
          if (g.status === 'RUNNING') this.readyDone = true;
          this.loadBoardStates();
          this.loadChatHistory();
          this.connectWs();
          this.cdr.markForCheck();
        },
      });
  }

  loadBoardStates() {
    if (this.myBoardId) this.fetchBoardState(this.myBoardId, true);
    if (this.opponentBoard?.id) this.fetchBoardState(this.opponentBoard.id, false);
  }

  fetchBoardState(boardId: string, isMine: boolean) {
    this.http.get<BoardStateDto>(`${API_BASE_URL}/games/${this.gameCode}/boards/${boardId}/state`)
      .subscribe({
        next: (state) => {
          if (isMine) {
            this.myBoardState = state;
            this.buildMyBoardMaps();
          } else {
            this.opponentBoardState = state;
            this.buildOpponentMaps();
          }

          if (state.locked) {
            this.readyPlayers.add(state.ownerId);
          } else {
            this.readyPlayers.delete(state.ownerId);
          }
          this.cdr.markForCheck();
        },
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
      if (shot.result === 'MISS') this.myMisses.add(key);
      else if (shot.result === 'HIT') this.myHits.set(key, 'hit');
      else if (shot.result === 'SUNK') this.myHits.set(key, 'sunk');
    }
  }

  buildOpponentMaps() {
    if (!this.opponentBoardState) return;
    this.oppHits.clear();
    this.oppMisses.clear();
    for (const shot of this.opponentBoardState.shotsOnThisBoard) {
      const key = `${shot.x},${shot.y}`;
      if (shot.result === 'MISS') this.oppMisses.add(key);
      else if (shot.result === 'HIT') this.oppHits.set(key, 'hit');
      else if (shot.result === 'SUNK') this.oppHits.set(key, 'sunk');
    }
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
          this.cdr.markForCheck();
        },
        error: () => {
          this.readyLoading = false;
          this.error = 'Ready fehlgeschlagen.';
          this.cdr.markForCheck();
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
          this.shotLoading = false;
          this.loadBoardStates();
          this.cdr.markForCheck();
        },
        error: (err) => {
          console.error('Shot failed', err);
          this.shotError = 'Schuss fehlgeschlagen.';
          this.shotLoading = false;
          this.cdr.markForCheck();
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
    if (this.oppHits.has(key)) return this.oppHits.get(key)!;
    if (this.oppMisses.has(key)) return 'miss';
    return 'empty';
  }

  cellStateForMine(x: number, y: number): CellState {
    const key = `${x},${y}`;
    if (this.myHits.has(key)) return this.myHits.get(key)!;
    if (this.myMisses.has(key)) return 'miss';
    if (this.myShipCoords.has(key)) return 'ship';
    return 'empty';
  }

  connectWs() {
    if (this.stomp || !this.gameCode) return;

    this.stomp = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
    });

    this.stomp.onConnect = () => {
      this.zone.run(() => {
        this.eventSub = this.stomp!.subscribe(`/topic/games/${this.gameCode}/events`, (msg) => this.handleEvent(msg));
        this.chatSub = this.stomp!.subscribe(`/topic/games/${this.gameCode}/chat`, (msg) => this.handleChat(msg));
      });
    };

    this.stomp.activate();
  }

  handleChat(msg: IMessage) {
    const dto = JSON.parse(msg.body) as ChatDto;
    this.zone.run(() => {
      this.chatMessages = [...this.chatMessages, dto];
      this.cdr.markForCheck();
      this.scrollChatToBottom();
    });
  }

  handleEvent(msg: IMessage) {
    const evt = JSON.parse(msg.body) as GameEventDto;

    this.zone.run(() => {
      if (evt.type === 'BOARD_CONFIRMED') {
        this.loadBoardStates();
        this.cdr.markForCheck();
        return;
      }

      if (evt.type === 'GAME_STARTED') {
        this.loadGame();
        return;
      }

      if (evt.type === 'SHOT_FIRED') {
        const { currentTurnPlayerId } = evt.payload || {};
        if (this.game && currentTurnPlayerId) {
          this.game = { ...this.game, currentTurnPlayerId };
        }
        this.loadBoardStates();
        this.cdr.markForCheck();
        return;
      }

      if (evt.type === 'TURN_CHANGED') {
        const { currentTurnPlayerId } = evt.payload || {};
        if (this.game && currentTurnPlayerId) {
          this.game = { ...this.game, currentTurnPlayerId };
          this.cdr.markForCheck();
        }
        return;
      }

      if (evt.type === 'GAME_FINISHED') {
        const { winnerPlayerId } = evt.payload || {};
        if (this.game) {
          this.game = {
            ...this.game,
            status: 'FINISHED',
            winnerPlayerId: winnerPlayerId ?? this.game.winnerPlayerId,
          };
          this.cdr.markForCheck();
        }
        return;
      }

      if (evt.type === 'GAME_FORFEITED') {
        const { winnerPlayerId } = evt.payload || {};
        if (this.game) {
          this.game = { ...this.game, status: 'FINISHED', winnerPlayerId };
          this.cdr.markForCheck();
        }
        return;
      }

    });
  }


  loadChatHistory() {
    this.http.get<ChatDto[]>(`${API_BASE_URL}/games/${this.gameCode}/chat/messages`).subscribe({
      next: (msgs) => {
        this.chatMessages = msgs;
        this.cdr.markForCheck();
        this.scrollChatToBottom();
      },
    });
  }

  sendChat() {
    if (!this.chatInput.trim()) return;
    console.log('publish chat', this.chatInput, 'connected?', this.stomp?.connected);
    this.stomp?.publish({
      destination: `/app/games/${this.gameCode}/chat`,
      body: JSON.stringify({
        senderId: this.myPlayerId,
        senderName: this.playerName(this.myPlayerId),
        message: this.chatInput.trim(),
      }),
    });
    this.chatInput = '';
  }

  autoPlacement() {
    if (!this.isSetup() || this.readyDone || !this.myPlayerId) return;
    this.autoLoading = true;

    this.http
      .post<BoardStateDto>(
        `${API_BASE_URL}/games/${this.gameCode}/players/${this.myPlayerId}/board/reroll`,
        {}
      )
      .subscribe({
        next: (state) => {
          this.myBoardState = state;
          this.buildMyBoardMaps();
          this.autoLoading = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.autoLoading = false;
          this.cdr.markForCheck();
        },
      });
  }

  forfeitGame() {
    if (!this.gameCode || !this.myPlayerId) return;
    this.forfeitLoading = true;

    this.http
      .post<GameDto>(`${API_BASE_URL}/games/${this.gameCode}/forfeit`, { playerId: this.myPlayerId })
      .subscribe({
        next: (g) => {
          this.game = g;
          this.forfeitLoading = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.forfeitLoading = false;
          this.cdr.markForCheck();
        },
      });
  }

  protected readonly Math = Math;
}
