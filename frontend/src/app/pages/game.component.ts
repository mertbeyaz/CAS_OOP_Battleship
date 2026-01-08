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
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, finalize, throwError } from 'rxjs';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_BASE_URL } from '../app.config';

type GamePublicDto = {
  gameCode: string;
  status: 'WAITING' | 'SETUP' | 'RUNNING' | 'PAUSED' | 'FINISHED';
  yourBoardLocked: boolean;
  opponentBoardLocked: boolean;
  yourTurn: boolean;
  opponentName: string | null;
};

type BoardStateDto = {
  boardId: string;
  width: number;
  height: number;
  locked: boolean;
  shipPlacements: Array<{ type: string; startX: number; startY: number; orientation: 'HORIZONTAL' | 'VERTICAL'; size: number }>;
};

type GameEventDto = {
  type: string;
  payload: Record<string, any>;
};

type ShotResultDto = {
  result: 'MISS' | 'HIT' | 'SUNK' | 'ALREADY_SHOT';
  hit: boolean;
  shipSunk: boolean;
  yourTurn: boolean;
};

type ShotDto = { x: number; y: number; result: 'MISS' | 'HIT' | 'SUNK' | 'ALREADY_SHOT' };

type GameSnapshotDto = {
  boardWidth: number;
  boardHeight: number;
  yourBoard: BoardStateDto;
  shotsOnYourBoard: ShotDto[];
  yourShotsOnOpponent: ShotDto[];
};

type ResumeResponseDto = {
  status: string;
  snapshot?: GameSnapshotDto;
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
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  private zone = inject(NgZone);

  @ViewChild('messagesContainer') private messagesEl?: ElementRef<HTMLDivElement>;

  // Route/session
  gameCode = '';
  myPlayerId = '';
  myPlayerName = '';
  lobbyCode = '';

  // Game state
  game?: GamePublicDto;
  readyDone = false;
  waitingForResume = false; // Due to hand-shake from the oppenent player
  forfeitedByName = '';

  // Loading flags / errors
  loading = false;
  readyLoading = false;
  shotLoading = false;
  autoLoading = false;
  forfeitLoading = false;
  pauseLoading = false;
  error = '';
  shotError = '';

  // Board sizes (fixed to 10x10, can be overwritten by snapshot)
  boardWidth = 10;
  boardHeight = 10;

  myBoardState?: BoardStateDto;
  myShipCoords = new Set<string>();

  // Local shot history (no API available)
  myShotsOnOpponent = new Map<string, 'hit' | 'sunk' | 'miss'>();
  shotsOnMyBoard = new Map<string, 'hit' | 'sunk' | 'miss'>();
  private lastShotSent?: { x: number; y: number };

  // WebSocket
  private stomp?: Client;
  private eventSub?: StompSubscription;
  private chatSub?: StompSubscription;
  private lobbySub: any;

  // Chat
  chatMessages: ChatDto[] = [];
  chatInput = '';

  protected readonly Math = Math;

  // ----------------------------
  // Lifecycle
  // ----------------------------
  ngOnInit(): void {
    const state = (history.state as { myBoard?: BoardStateDto }) || {};
    if (state.myBoard) {
      this.myBoardState = state.myBoard;
      this.boardWidth = state.myBoard.width;
      this.boardHeight = state.myBoard.height;
      this.buildMyBoardMaps();
    }

    this.route.queryParamMap.subscribe((params) => {
      this.gameCode = params.get('gameCode') ?? '';
      this.myPlayerId = params.get('playerId') ?? '';
      this.myPlayerName = params.get('playerName') ?? '';
      this.lobbyCode = params.get('lobbyCode') ?? '';

      if (this.gameCode && this.myPlayerId) {
        this.loadGame();
      } else {
        this.error = 'Fehlende Parameter.';
      }
    });
  }

  ngOnDestroy(): void {
    this.eventSub?.unsubscribe();
    this.chatSub?.unsubscribe();
    this.stomp?.deactivate();
    this.lobbySub?.unsubscribe();
  }

  // ----------------------------
  // Helpers
  // ----------------------------
  private scrollChatToBottom() {
    requestAnimationFrame(() => {
      const el = this.messagesEl?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }

  private applyShot(target: 'opponent' | 'me', x: number, y: number, result: string) {
    const key = `${x},${y}`;
    const map = target === 'opponent' ? this.myShotsOnOpponent : this.shotsOnMyBoard;

    if (result === 'MISS') map.set(key, 'miss');
    if (result === 'HIT') map.set(key, 'hit');
    if (result === 'SUNK') map.set(key, 'sunk');
  }

  // ----------------------------
  // Data loading
  // ----------------------------
  loadGame() {
    this.loading = true;
    this.error = '';
    this.http
      .get<GamePublicDto>(`${API_BASE_URL}/games/${this.gameCode}?playerId=${this.myPlayerId}`)
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
          this.readyDone = g.yourBoardLocked;

          // Optional: falls Setup und kein Board, automatisch platzieren
          if (g.status === 'SETUP' && !g.yourBoardLocked && !this.myBoardState) {
            this.autoPlacement();
          }

          this.connectWs();
          this.loadChatHistory();
          this.cdr.markForCheck();
        },
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

  // ----------------------------
  // Game actions
  // ----------------------------
  isSetup() {
    return this.game?.status === 'SETUP';
  }

  isMyTurn() {
    return this.game?.status === 'RUNNING' && this.game?.yourTurn;
  }

  canShoot() {
    return this.isMyTurn() && !this.shotLoading;
  }

  markReady() {
    if (!this.isSetup() || !this.myPlayerId) return;
    this.readyLoading = true;
    this.error = '';
    this.http
      .post<GamePublicDto>(`${API_BASE_URL}/games/${this.gameCode}/players/${this.myPlayerId}/board/confirm`, {})
      .subscribe({
        next: (g) => {
          this.game = g;
          this.readyDone = g.yourBoardLocked;
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
    if (!this.canShoot()) return;
    this.shotLoading = true;
    this.shotError = '';

    this.lastShotSent = { x, y };
    this.http
      .post<ShotResultDto>(`${API_BASE_URL}/games/${this.gameCode}/shots`, {
        shooterId: this.myPlayerId,
        x,
        y,
      })
      .subscribe({
        next: (res) => {
          this.applyShot('opponent', x, y, res.result);

          if (this.game) {
            this.game = { ...this.game, yourTurn: res.yourTurn };
          }
          this.shotLoading = false;
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

  autoPlacement() {
    if (!this.isSetup() || this.readyDone || !this.myPlayerId) return;
    this.autoLoading = true;

    this.http
      .post<BoardStateDto>(`${API_BASE_URL}/games/${this.gameCode}/players/${this.myPlayerId}/board/reroll`, {})
      .subscribe({
        next: (state) => {
          this.myBoardState = state;
          this.boardWidth = state.width;
          this.boardHeight = state.height;
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
      .post<GamePublicDto>(`${API_BASE_URL}/games/${this.gameCode}/forfeit`, { playerId: this.myPlayerId })
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

  pauseGame() {
    if (!this.gameCode || !this.myPlayerId) return;
    this.pauseLoading = true;

    this.http
      .post<GamePublicDto>(`${API_BASE_URL}/games/${this.gameCode}/pause`, { playerId: this.myPlayerId })
      .subscribe({
        next: (g) => {
          this.game = g;
          this.pauseLoading = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.pauseLoading = false;
          this.cdr.markForCheck();
        },
      });
  }

  resumeGame() {
    if (!this.gameCode || !this.myPlayerId) return;

    this.http
      .post<ResumeResponseDto>(`${API_BASE_URL}/games/${this.gameCode}/resume`, { playerId: this.myPlayerId })
      .subscribe({
        next: (res) => {
          // Resume-Handshake UI
          this.waitingForResume = res.status === 'WAITING';

          if (res.snapshot) {
            this.boardWidth = res.snapshot.boardWidth;
            this.boardHeight = res.snapshot.boardHeight;

            this.myBoardState = res.snapshot.yourBoard;
            this.buildMyBoardMaps();

            this.shotsOnMyBoard.clear();
            this.myShotsOnOpponent.clear();

            for (const s of res.snapshot.shotsOnYourBoard) {
              this.applyShot('me', s.x, s.y, s.result);
            }
            for (const s of res.snapshot.yourShotsOnOpponent) {
              this.applyShot('opponent', s.x, s.y, s.result);
            }
          }

          // Nach Resume Status aktualisieren
          this.loadGame();
          this.cdr.markForCheck();
        },
        error: () => {
          this.cdr.markForCheck();
        },
      });
  }

  // ----------------------------
  // Board helpers
  // ----------------------------
  buildMyBoardMaps() {
    if (!this.myBoardState) return;
    this.myShipCoords.clear();

    for (const s of this.myBoardState.shipPlacements) {
      for (let i = 0; i < s.size; i++) {
        const x = s.orientation === 'HORIZONTAL' ? s.startX + i : s.startX;
        const y = s.orientation === 'VERTICAL' ? s.startY + i : s.startY;
        this.myShipCoords.add(`${x},${y}`);
      }
    }
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
    const v = this.myShotsOnOpponent.get(key);
    if (v === 'hit') return 'hit';
    if (v === 'sunk') return 'sunk';
    if (v === 'miss') return 'miss';
    return 'empty';
  }

  cellStateForMine(x: number, y: number): CellState {
    const key = `${x},${y}`;
    const v = this.shotsOnMyBoard.get(key);
    if (v === 'hit') return 'hit';
    if (v === 'sunk') return 'sunk';
    if (v === 'miss') return 'miss';
    if (this.myShipCoords.has(key)) return 'ship';
    return 'empty';
  }

  // ----------------------------
  // WebSocket
  // ----------------------------
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

        if (this.lobbyCode) {
          this.lobbySub = this.stomp!.subscribe(`/topic/lobbies/${this.lobbyCode}/events`, (msg) => {
            const evt = JSON.parse(msg.body);
            if (evt.type === 'LOBBY_FULL') {
              this.loadGame();
            }
          });
        }
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
      if (evt.type === 'SHOT_FIRED') {
        const { x, y, result } = evt.payload || {};
        if (!(this.lastShotSent && this.lastShotSent.x === x && this.lastShotSent.y === y)) {
          this.applyShot('me', x, y, result);
        }
        this.loadGame();
        return;
      }

      if (evt.type === 'GAME_FORFEITED') {
        const { forfeitingPlayerName } = evt.payload || {};
        this.forfeitedByName = forfeitingPlayerName || '';
        this.loadGame();
        return;
      }

      if ([
        'BOARD_CONFIRMED',
        'GAME_STARTED',
        'TURN_CHANGED',
        'GAME_FINISHED',
        'GAME_FORFEITED',
        'GAME_PAUSED',
        'GAME_RESUMED'
      ].includes(evt.type)) {
        this.loadGame();
        return;
      }
    });
  }

  // ----------------------------
  // Chat
  // ----------------------------
  sendChat() {
    if (!this.chatInput.trim()) return;
    this.stomp?.publish({
      destination: `/app/games/${this.gameCode}/chat`,
      body: JSON.stringify({
        senderId: this.myPlayerId,
        senderName: this.myPlayerName,
        message: this.chatInput.trim(),
      }),
    });
    this.chatInput = '';
  }
}
