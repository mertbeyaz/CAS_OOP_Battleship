# Battleship Game (CAS OOP 25-20)

A **real-time multiplayer Battleship game** with advanced features including automatic disconnect detection, chat functionality, and token-based resume capability. Developed as part of the **CAS Object-Oriented Programming (CAS OOP 25-20)**.

The project demonstrates **clean object-oriented design**, **production-ready architecture**, and **robust real-time communication**.

## Table of Contents

- [🎯 Overview](#-overview)
    - [Backend Features](#backend-features)
    - [Frontend Features](#frontend-features)
- [🚀 Quick Start](#-quick-start)
    - [Prerequisites](#prerequisites)
    - [Run the Application](#run-the-application)
    - [Access & Management](#access--management)
    - [Development & Reset](#️development--reset)
- [🏗️ Architecture](#️-architecture)
    - [Domain Layer](#domain-layer)
    - [Service Layer](#service-layer)
    - [Web / API Layer](#web--api-layer)
    - [Persistence Layer](#persistence-layer)
- [📊 Domain Model](#-domain-model)
    - [Core Entities](#core-entities)
    - [Domain Model Diagram](#domain-model-diagram)
- [🎮 Game Flow](#-game-flow)
    - [Standard Game Flow](#standard-game-flow)
    - [Disconnect & Resume Flow](#disconnect--resume-flow)
- [🔌 API Documentation](#-api-documentation)
    - [REST Endpoints](#rest-endpoints)
    - [WebSocket Topics](#websocket-topics)
    - [Benefits](#benefits)
- [🐳 Docker Setup](#-docker-setup)
    - [Architecture](#architecture-1)
- [🔒 Security](#-security)
    - [Anti-Cheat](#anti-cheat)
    - [Data Privacy](#data-privacy)
- [🚀 Technology Stack](#-technology-stack)
- [🎓 Key Achievements](#-key-achievements)
    - [Technical Innovations](#technical-innovations)
- [👥 Authors](#-authors)
- [📝 Project Focus](#-project-focus)

## 🎯 Overview

### Backend Features
- **Game & Lobby Management** - Automatic matchmaking system
- **Turn-Based Game Logic** - Complete Battleship rules implementation
- **REST API** - Comprehensive endpoints for game interaction
- **WebSocket Real-Time Events** - Instant updates for all game actions
- **Automatic Disconnect Detection** - Graceful network failure handling
- **Resume/Reconnect System** - Token-based game continuation
- **In-Game Chat** - Real-time messaging between players
- **Scheduled Cleanup** - Automatic maintenance of stale connections
- **Docker Ready** - Complete containerized deployment

### Frontend Features
- Interactive game board with automatic ship placement
- Real-time opponent board with shot feedback
- Live chat system
- Connection status monitoring
- Game state management with resume capability

## 🚀 Quick Start

Get the game running in less than 2 minutes!

### Prerequisites
- Docker & Docker Compose installed

### Run the Application

```
# Clone repository
git clone https://github.com/mertbeyaz/CAS_OOP_Battleship
cd CAS_OOP_Battleship

# Start application (builds images & starts containers)
docker compose -f docker-compose.dev.yml up -d --build
```

### Access & Management
Replace `<ip_address>` with `localhost` or your VM's IP.

| Service | URL | Description                                  |
|---------|-----|----------------------------------------------|
| **Game Frontend** | `http://<ip_address>` | Main Game UI                                 |
| **API Swagger** | `http://<ip_address>/swagger-ui/index.html` | Backend API Documentation                    |
| **Traefik Dashboard** | `http://<ip_address>:8081` | Proxy & Container Status                     |
| **PgAdmin** | `http://<ip_address>/pgadmin` | Database GUI (Login: Use password from .env) |

### Development & Reset

**IntelliJ Configurations:**
* **'Battleship TEST':** Runs locally with H2 (In-Memory).
* **'Battleship DEV':** Connects to Docker DB via PostgreSQL.
  ➡️ *Note: Set `DB_HOST=<ip_address>` in your environment variables.*
* **'Battleship DEV-Reset':** Connects to Docker DB and **resets the data** on start.
  ➡️ *Note: Set `DB_HOST=<ip_address>` in your environment variables.*

**Docker Commands:**

```bash
# 🧨 Reset Database (Wipes data & recreates schema)
APP_PROFILE=dev-reset docker compose -f docker-compose.dev.yml up -d backend

# 🧹 Clean Up (Stops containers & removes volumes)
docker compose -f docker-compose.dev.yml down -v --rmi all --remove-orphans
```

## 🏗️ Architecture

The application follows a **layered architecture** with clear separation of concerns:

### Domain Layer
- Core game logic independent of frameworks
- Entities: `Game`, `Board`, `Player`, `Ship`, `Shot`, `PlayerConnection`
- Business rules encapsulated in domain objects
- No framework dependencies

### Service Layer
- Orchestrates domain logic
- Handles game state transitions
- Publishes WebSocket events
- Manages player connections and disconnect detection

### Web / API Layer
- REST Controllers - HTTP endpoints for game actions
- WebSocket Controllers - STOMP-based real-time communication
- DTOs - Controlled data exposure, no internal IDs leaked
- Event Listeners - Automatic connection monitoring

### Persistence Layer
- Spring Data JPA
- Supports H2 (development / testing) and PostgreSQL (development / production)
- Optimized queries with relationship management

## 📊 Domain Model

### Core Entities

**Game**
- Aggregates players, boards, shots, chat messages, and connections
- Lifecycle: `WAITING` → `SETUP` → `RUNNING` → `PAUSED` → `FINISHED`
- Supports pause/resume with token-based authentication

**Board**
- Owned by exactly one player
- Contains automatic ship placements with validation
- Locked after confirmation

**Ship & ShipPlacement**
- Ship types: Destroyer (2), Cruiser (3), Battleship (4), Carrier (5)
- Placement validation ensures no overlapping
- Covered coordinates tracked for hit detection

**Shot**
- Immutable record of firing action
- Results: `MISS`, `HIT`, `SUNK`, `ALREADY_SHOT`
- Turn logic: Miss switches turn, Hit keeps turn

**PlayerConnection**
- Tracks WebSocket session state
- Enables automatic disconnect detection
- Persists connection history
- Supports reconnection tracking

**Lobby**
- Automatic player pairing system
- Transitions: `WAITING` → `FULL`

### Domain Model Diagram

![PlantUML model](https://www.plantuml.com/plantuml/svg/bLRTZzeu47z7ud-mUBXjUzrLbxOlqTwg0T8IbGMKZwvwBoT3PeDlWuqIfmjRzNy_Uvo41CBIYuJmVFetu_6CFsMIfZ9V9zrEziZuXgPqJPO9pJ9RofGjSWdkKd2VFGDvr-rqEnVahrEV5dxLfi39gv5OKyPVdp7eTkWYaobTIhCf6T0C72wPt96VtGuXVxB88c7eZf1Ofa0bHBQqo4Wj0hO6vEURw9Z_IxitNYTYiTWHs4hWKSiUAWSopuXbz7oaK91eUYWYK39VfOF7o8xfMdE-w3zoGSH6Ci5fGbwzkxgwVeqgFo3D5DCYYT16DIneTHwALgiXqLiU0r4dyy3YGdk8H9vIZGR5cuJk50uT6ClapHp9MnGj14sZIZBGb15aAo4hlgy8mrv3bj4OAibmHUMTuHc5PPwcm6MGfzpiuqQaTd8fPFaQFA3Hhatyn3Y7z4XIIvkHdKvSda7ApRcK2QfPOu2dZPzO95VAad69z0fOl98rXdXIy1HjK1IriNm43NKXH0AqolWIrYeFjHIopRpatZP0DKGlb8HI2tORZAnO6amfKtPusaeqsrDb8QKwWg4GQSIukXK7UuiNjbnMHWSUYfDC988HqaEYB1mX3vEaGCGWJVOjSmeD6BKoEer6AacpxbM7YHfR_JAjwVBKfuLJvzuY7jOX2SGZy3CM--jgZD3c1PGeRi1K3cIOwRwa-2vNHwBEMJKCdYS9NIHms4ngb_OujXVRxZUQv41cYtzXAREpFNSVMQDda9kR7-X7e9_Rsv_xLd5_S4EGEJwy9Wy9W1p10-EiA4kJ3HtqyzNWYlIt_RVzxVktrziFwlUngkyxLIUCbvxg1RAln4DqHTwtjVrt3GzdSkRmV7qkL_jCcLwiG80o0vEm4iEzxGRkz8jU-auGplN22wVJWZUtGz_1FlVPdRh-s1aT3wVVTFyvd4mQnhLhtsuydddktxDfO4yqzStn0dTeJuvwWRd8xIxkNDz7Lsw08ORJh_hVddYEFVh-Zp-U1KTz3HjMgxUHumVUxBlZwQB8zBkYhzb3BtHzpQpOxpLxO0V1nF77xhnaVq1js_FSXlR7tplq0JmpMEKyrcyKEuvJY5LjPUVVAHV58u8c6V5KfxFAGQwQF0FLQjMWbUG9E2yKpL6PTU-wHyJb9Udf1qBFM5eanYklNIEZzDpj85ctwVVA-MUHOgYtQvMTreptGvqtFTFtBO9JlaLSxySMAOT-JL7h5H4qUuT5p6F418GuBiFr0oIguHjKJHZrqOT15NEpqdvJRLQ5ZbFORE3ZC-KF1DpjO5Hj5iLGEjOe4rKRKDrEFVfMRpXs381Ya9YkdqA2MMXBwU40iKYwfvH1uVt_c7GxdnGIVBJ_1m00)

## 🎮 Game Flow

### Standard Game Flow
1. Player joins lobby (automatic matchmaking)
2. Game created when lobby is full
3. Players place ships (auto-generate)
4. Both players confirm boards (locked)
5. Game starts (`RUNNING`)
6. Players alternate turns
7. Game ends when all ships sunk or player forfeits

### Disconnect & Resume Flow 
1. Player loses connection (browser closed, network failure)
2. Backend detects disconnect after **2-minute grace period**
3. Game automatically pauses (`PAUSED` status)
4. Both players receive real-time notification
5. Players reconnect and resume using resume tokens
6. Two-player handshake: `PAUSED` → `WAITING` → `RUNNING`

## 🔌 API Documentation

### REST Endpoints

#### Lobby
``` 
POST    /api/lobbies/auto-join  # Create a new lobby and game
```
#### Game Management
```
POST   /api/games                    # Create new game
POST   /api/games/{gameCode}/join    # Join existing game
GET    /api/games/{gameCode}         # Get public game state
GET    /api/games/{gameCode}/state   # Full snapshot for reconnect
```

#### Game Actions
```
POST   /api/games/{gameCode}/shots           # Fire a shot
POST   /api/games/{gameCode}/pause           # Pause game
POST   /api/games/{gameCode}/resume          # Resume with token
POST   /api/games/{gameCode}/forfeit         # Forfeit game
POST   /api/games/{gameCode}/boards/reroll   # Auto-generate board
POST   /api/games/{gameCode}/boards/confirm  # Lock board
```

#### Development Endpoints
```
GET    /api/dev/games/{gameCode}/boards/{boardId}/state  # Get specific board state
GET    /api/dev/games/{gameCode}/boards/{boardId}/ascii  # ASCII view
GET    /api/dev/games/{gameCode}/connections             # Connection status
```

### WebSocket Topics

```
/topic/games/{gameCode}/events    # Game events
/topic/games/{gameCode}/chat      # Chat messages
/topic/lobbies/{lobbyCode}/events # Lobby events

**Note:** The `/topic/games/{gameCode}/events` topic serves dual purpose:
- Game state updates (shots, turns, pause/resume)
- Connection monitoring (disconnect/reconnect notifications)

Frontend subscribes with `playerId` header for automatic connection tracking.
```

**Event Types:**
- `BOARD_CONFIRMED` / `BOARD_REROLLED`
- `GAME_STARTED` / `GAME_FINISHED` / `GAME_FORFEITED`
- `SHOT_FIRED` / `TURN_CHANGED`
- `GAME_PAUSED` / `GAME_RESUMED` / `GAME_RESUME_PENDING`
- `PLAYER_DISCONNECTED` / `PLAYER_RECONNECTED`

### Benefits
- ✅ Short network issues (< 2min) handled transparently
- ✅ Real disconnects trigger automatic pause
- ✅ Both players notified in real-time
- ✅ Resume via secure token system

## 🐳 Docker Setup

### Architecture

![imgae](./img.png)


## 🔒 Security

### Anti-Cheat
- ✅ Opponent board hidden until game end
- ✅ Shot validation server-side
- ✅ Turn validation prevents cheating

### Data Privacy
- ✅ No player IDs in WebSocket events
- ✅ Only usernames in public DTOs
- ✅ Resume tokens validated server-side
- ✅ Dev endpoints profile-protected

## 🚀 Technology Stack

**Backend**
- Java 25, Spring Boot 3.5.8
- Spring Data JPA, Spring WebSocket (STOMP)
- PostgreSQL / H2
- JUnit 5, Mockito, AssertJ

**Frontend**
- Angular 19, TypeScript
- RxJS, STOMP.js
- Tailwind CSS

**DevOps**
- Docker, Docker Compose
- Traefik, Maven

## 🎓 Key Achievements

1. **Production-Grade Architecture** - Clean, maintainable, scalable
2. **Advanced WebSocket System** - Real-time with disconnect detection
3. **Comprehensive Testing** - 103 tests, all passing
4. **Security-First Design** - No data leaks, anti-cheat
5. **Developer Experience** - Easy setup, Docker-ready

### Technical Innovations
- **Automatic Disconnect Detection** - No frontend polling
- **Graceful Connection Handling** - 2-minute grace period
- **Token-Based Resume** - Secure game continuation
- **Scheduled Cleanup** - Automatic maintenance
- **Multi-Layer Timeout** - Robust failure handling

## 👥 Authors

- **Mert Beyaz** (Frontend) - Angular application, UI/UX, real-time integration
- **Michael Coppola** (Backend) - Architecture, WebSocket system, disconnect detection
> *Transparency Note: This project leveraged AI tools for coding assistance and debugging. The realization of the project in this scope and timeframe would not have been possible without the use of AI.*

## 📝 Project Focus

**CAS Learning Objectives:**
- ✅ Object-oriented design & SOLID principles
- ✅ Clean architecture & separation of concerns
- ✅ Composition over inheritance (only one inheritance)
- ✅ Real-time communication & event-driven design
- ✅ State management & error handling
- ✅ Testing strategies & production readiness
- ✅ Docker deployment & DevOps practices


*Developed as part of CAS Object-Oriented Programming (CAS OOP 25-20)*  
*January 2026*