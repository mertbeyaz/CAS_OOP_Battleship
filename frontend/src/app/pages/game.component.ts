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

type ChatDto = { senderId: string; senderName: string; gameCode: string; message: string; timestamp: string };
type CellState = 'empty' | 'ship';

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
  private router = inject(Router);

  @ViewChild('messagesContainer') private messagesEl?: ElementRef<HTMLDivElement>;

  gameCode = '';
  myPlayerId = '';
  myPlayerName = '';
  game?: GamePublicDto;

  loading = false;
  readyLoading = false;
  readyDone = false;
  shotLoading = false;
  error = '';
  shotError = '';
  autoLoading = false;
  forfeitLoading = false;

  myBoardState?: BoardStateDto;

  myShipCoords = new Set<string>();

  private stomp?: Client;
  private eventSub?: StompSubscription;
  private chatSub?: StompSubscription;

  chatMessages: ChatDto[] = [];
  chatInput = '';

  ngOnInit(): void {
    const state = (history.state as { myBoard?: BoardStateDto }) || {};
    if (state.myBoard) {
      this.myBoardState = state.myBoard;
      this.buildMyBoardMaps();
    }

    this.route.queryParamMap.subscribe((params) => {
      this.gameCode = params.get('gameCode') ?? '';
      this.myPlayerId = params.get('playerId') ?? '';
      this.myPlayerName = params.get('playerName') ?? '';
      if (this.gameCode && this.myPlayerId) this.loadGame();
      else this.error = 'Fehlende Parameter.';
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
          this.connectWs();
          this.loadChatHistory();
          this.cdr.markForCheck();
        },
      });
  }

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

    this.http
      .post<ShotResultDto>(`${API_BASE_URL}/games/${this.gameCode}/shots`, {
        shooterId: this.myPlayerId,
        x,
        y,
      })
      .subscribe({
        next: (res) => {
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

  cells(n: number) {
    return Array.from({ length: n });
  }

  cellLabel(index: number, width: number) {
    const x = index % width;
    const y = Math.floor(index / width);
    return `${x},${y}`;
  }

  cellStateForMine(x: number, y: number): CellState {
    const key = `${x},${y}`;
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
      if ([
        'BOARD_CONFIRMED',
        'GAME_STARTED',
        'SHOT_FIRED',
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

  autoPlacement() {
    if (!this.isSetup() || this.readyDone || !this.myPlayerId) return;
    this.autoLoading = true;

    this.http
      .post<BoardStateDto>(`${API_BASE_URL}/games/${this.gameCode}/players/${this.myPlayerId}/board/reroll`, {})
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

  protected readonly Math = Math;
}
