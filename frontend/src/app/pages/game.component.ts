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

type GameStateDto = {
  gameCode: string;
  status: 'WAITING' | 'SETUP' | 'RUNNING' | 'PAUSED' | 'FINISHED';
  boardWidth: number;
  boardHeight: number;
  youName: string;
  opponentName: string | null;
  yourBoardLocked: boolean;
  opponentBoardLocked: boolean;
  yourTurn: boolean;
  yourBoard: BoardStateDto;
  shotsOnYourBoard: ShotDto[];
  yourShotsOnOpponent: ShotDto[];
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

type ResumeResponseDto = {
  status: string;
  snapshot?: GameStateDto;
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
/**
 * Game view component: renders boards, chat, and game actions.
 */
export class GameComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private cdr = inject(ChangeDetectorRef);
  private zone = inject(NgZone);

  /** Chat scroll container reference for auto-scroll. */
  @ViewChild('messagesContainer') private messagesEl?: ElementRef<HTMLDivElement>;

  // Route/session
  /** Current game code (from query params). */
  gameCode = '';
  /** Current player id (from query params). */
  myPlayerId = '';
  /** Current player name (from query params). */
  myPlayerName = '';
  /** Lobby code used for lobby WS events. */
  lobbyCode = '';

  // Game state
  /** Public game snapshot used for UI state. */
  game?: GamePublicDto;
  /** True if the player's board is confirmed/locked. */
  readyDone = false;
  /** True while resume handshake is pending. */
  waitingForResume = false;
  /** Name of the player who forfeited (if any). */
  forfeitedByName = '';
  /** Resume token for paused games. */
  resumeToken = '';

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

  /** Own board state with ship placements. */
  myBoardState?: BoardStateDto;
  /** Set of ship coordinates for rendering on own board. */
  myShipCoords = new Set<string>();

  // Local shot history
  /** Shots fired by the player onto the opponent board. */
  myShotsOnOpponent = new Map<string, 'hit' | 'sunk' | 'miss'>();
  /** Shots fired by the opponent onto the player's board. */
  shotsOnMyBoard = new Map<string, 'hit' | 'sunk' | 'miss'>();
  /** Last shot sent by this client (used to ignore echo). */
  private lastShotSent?: { x: number; y: number };

  // WebSocket
  private stomp?: Client;
  private eventSub?: StompSubscription;
  private chatSub?: StompSubscription;
  private lobbySub?: StompSubscription;

  // Chat
  chatMessages: ChatDto[] = [];
  chatInput = '';

  protected readonly Math = Math;

  // ----------------------------
  // Lifecycle
  // ----------------------------
  /**
   * Initializes component state from navigation and query params.
   */
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

      this.resumeToken =
        params.get('resumeToken') ??
        localStorage.getItem(`resume:${this.gameCode}`) ??
        '';

      if (this.gameCode && this.myPlayerId) {
        this.loadStateSnapshot();
      } else {
        this.error = 'Fehlende Parameter.';
      }
    });
  }

  /**
   * Cleans up subscriptions and WS connection.
   */
  ngOnDestroy(): void {
    this.eventSub?.unsubscribe();
    this.chatSub?.unsubscribe();
    this.lobbySub?.unsubscribe();
    this.stomp?.deactivate();
  }

  // ----------------------------
  // Helpers
  // ----------------------------
  /**
   * Scrolls chat view to the newest message.
   */
  private scrollChatToBottom() {
    requestAnimationFrame(() => {
      const el = this.messagesEl?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }

  /**
   * Applies a shot result to the local maps for rendering.
   * @param target Which board map to update.
   * @param x X coordinate.
   * @param y Y coordinate.
   * @param result Shot result string.
   */
  private applyShot(target: 'opponent' | 'me', x: number, y: number, result: string) {
    const snapshotKey = `${x},${y}`;
    const map = target === 'opponent' ? this.myShotsOnOpponent : this.shotsOnMyBoard;

    if (result === 'MISS') map.set(snapshotKey, 'miss');
    if (result === 'HIT') map.set(snapshotKey, 'hit');
    if (result === 'SUNK') map.set(snapshotKey, 'sunk');
  }

  // ----------------------------
  // Data loading
  // ----------------------------
  /**
   * Loads the full game snapshot (board + shots) for the player.
   */
  loadStateSnapshot() {
    this.loading = true;
    this.error = '';
    this.http
      .get<GameStateDto>(`${API_BASE_URL}/games/${this.gameCode}/state?playerId=${this.myPlayerId}`)
      .pipe(
        catchError((err: HttpErrorResponse) => {
          console.error('GET /games/{code}/state failed', err, 'body:', err.error);
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
        next: (s) => {
          this.game = {
            gameCode: s.gameCode,
            status: s.status,
            yourBoardLocked: s.yourBoardLocked,
            opponentBoardLocked: s.opponentBoardLocked,
            yourTurn: s.yourTurn,
            opponentName: s.opponentName,
          };

          this.myPlayerName = s.youName || this.myPlayerName;
          this.readyDone = s.yourBoardLocked;

          this.boardWidth = s.boardWidth;
          this.boardHeight = s.boardHeight;

          this.myBoardState = s.yourBoard;
          this.buildMyBoardMaps();

          this.shotsOnMyBoard.clear();
          this.myShotsOnOpponent.clear();
          for (const shot of s.shotsOnYourBoard) this.applyShot('me', shot.x, shot.y, shot.result);
          for (const shot of s.yourShotsOnOpponent) this.applyShot('opponent', shot.x, shot.y, shot.result);

          this.connectWs();
          this.loadChatHistory();
          this.cdr.markForCheck();
        },
      });
  }

  /**
   * Loads chat history for the current game.
   */
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
  /**
   * Returns true if the game is in setup phase.
   */
  isSetup() {
    return this.game?.status === 'SETUP';
  }

  /**
   * Returns true if it is the player's turn.
   */
  isMyTurn() {
    return this.game?.status === 'RUNNING' && this.game?.yourTurn;
  }

  /**
   * Returns true if the player can fire a shot now.
   */
  canShoot() {
    return this.isMyTurn() && !this.shotLoading;
  }

  /**
   * Confirms (locks) the player's board.
   */
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

  /**
   * Fires a shot to the opponent board.
   * @param x X coordinate.
   * @param y Y coordinate.
   */
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

  /**
   * Requests automatic ship placement from the backend.
   */
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

  /**
   * Forfeits the current game.
   */
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

  /**
   * Pauses the game (only allowed when running).
   */
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

  /**
   * Resumes a paused game using the resume token.
   */
  resumeGame() {
    if (!this.gameCode || !this.resumeToken) return;

    this.http
      .post<ResumeResponseDto>(`${API_BASE_URL}/games/resume`, { token: this.resumeToken })
      .subscribe({
        next: (res) => {
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

          this.loadStateSnapshot();
          this.cdr.markForCheck();
        },
      });
  }

  // ----------------------------
  // Board helpers
  // ----------------------------
  /**
   * Builds a fast lookup set of ship coordinates for the player's board.
   */
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

  /**
   * Returns an array with the given length for template iteration.
   */
  cells(n: number) {
    return Array.from({ length: n });
  }

  /**
   * Returns a coordinate label for a grid cell.
   * @param index Linear index.
   * @param width Board width.
   */
  cellLabel(index: number, width: number) {
    const x = index % width;
    const y = Math.floor(index / width);
    return `${x},${y}`;
  }

  /**
   * Computes the UI state for opponent cells based on your shots.
   */
  cellStateForOpp(x: number, y: number): CellState {
    const key = `${x},${y}`;
    const v = this.myShotsOnOpponent.get(key);
    if (v === 'hit') return 'hit';
    if (v === 'sunk') return 'sunk';
    if (v === 'miss') return 'miss';
    return 'empty';
  }

  /**
   * Computes the UI state for your own cells based on ships and incoming shots.
   */
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
  /**
   * Establishes WebSocket connection and subscribes to game/lobby topics.
   */
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
              this.loadStateSnapshot();
            }
          });
        }
      });
    };

    this.stomp.activate();
  }

  /**
   * Handles incoming chat message events.
   */
  handleChat(msg: IMessage) {
    const dto = JSON.parse(msg.body) as ChatDto;
    this.zone.run(() => {
      this.chatMessages = [...this.chatMessages, dto];
      this.cdr.markForCheck();
      this.scrollChatToBottom();
    });
  }

  /**
   * Handles incoming game event messages.
   */
  handleEvent(msg: IMessage) {
    const evt = JSON.parse(msg.body) as GameEventDto;

    this.zone.run(() => {
      if (evt.type === 'SHOT_FIRED') {
        const { x, y, result } = evt.payload || {};
        if (!(this.lastShotSent && this.lastShotSent.x === x && this.lastShotSent.y === y)) {
          this.applyShot('me', x, y, result);
        }
        this.loadStateSnapshot();
        return;
      }

      if (evt.type === 'GAME_FORFEITED') {
        const { forfeitingPlayerName } = evt.payload || {};
        this.forfeitedByName = forfeitingPlayerName || '';
        this.loadStateSnapshot();
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
        this.loadStateSnapshot();
        return;
      }
    });
  }

  // ----------------------------
  // Chat
  // ----------------------------
  /**
   * Publishes a chat message to the game topic.
   */
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
