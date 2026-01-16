# Battleship Game (CAS OOP 25-20)

This project is the implementation of a **Battleship game with chat functionality**, developed as part of the **CAS Object-Oriented Programming (CAS OOP 25-20)**.
The focus of this project is **clean object-oriented design**, **clear separation of concerns**, and **robust backend behavior**, rather than UI complexity.

## Overview
The backend provides:
- Game and lobby management
- Turn-based Battleship game logic
- Board setup and validation
- REST API for game interaction
- WebSocket-based real-time events and chat
- Dockerized runtime environment

The frontend provides:
- bla bla

## Architecture

The application follows a **layered architecture**:

- **Domain Layer**
    - Core game logic
    - Entities such as `Game`, `Board`, `Player`, `Ship`, `Shot`
    - No framework dependencies
- **Service Layer**
    - Orchestrates domain logic
    - Handles game state transitions
    - Publishes WebSocket events
- **Web / API Layer**
    - REST controllers (HTTP)
    - WebSocket controllers (STOMP)
    - DTOs to control exposed data
- **Persistence Layer**
    - Spring Data JPA
    - Relational database (H2/PostgreSQL)

## Domain Model (Key Concepts)

- **Game**
    - Holds players, boards, shots, chat messages
    - Controls the game lifecycle (`WAITING`, `SETUP`, `RUNNING`, `PAUSED`, `FINISHED`)
- **Board**
    - Owned by exactly one player
    - Contains ship placements
    - Can be locked after confirmation
- **Ship & ShipPlacement**
    - Ship size derived from `ShipType`
    - Placement determines covered coordinates
- **Shot**
    - Immutable result of a firing action
    - Supports `MISS`, `HIT`, `SUNK`, `ALREADY_SHOT`
- **Lobby**
    - Used to pair players automatically
    - Transitions from `WAITING` to `FULL`

![PlantUML model](https://www.plantuml.com/plantuml/svg/bLTRRzfA47v7uZ-iU3X9sYPgQFjYfL4DkCKg0UHBevwNemLlp9wODRBN3QJgV-_ih6riC914YkAvxZSpkpEJBwcaYSoMKRkLFdAneWbTadb4qzIKCk6pJ39x7WkNFpFoljrgjyx8vqC_IlXTcJ91pWVnad9nSSAWtQApL2PqBlMXf4TJPWd9vORyRhS8USi3W_Y-FK1YdZ0gMM1AWmpWm-DBXlni5Upp_oZtskiediqsXAqb4q736P03cQN4q7ekKYY85DsF0mOo5z8XGcI7TAautzGVSaRYbUIne14vVtzrTNs1AhzOaWBJ856iZ2fOqEeq5ALMGQAEt0S2bNdWOQxTHo9Bg4A3ubL4Du131SPJUJD5yXP5ivWcGIdfAKeBqaKiIxuBX6OlMPhIa9MI_eBAEytJ2lCiIPYGNfO8VOuTa4hb4fPcI-Om6coQn4zS29RiIIffqxB3YSjIbWXTbo915MiCyB3n4m_a0Yova4Wl60yNiiA8dm0Uq1f5VZVcZqn1dSLnn6YPyJbRGXugAUIhQS6zhO6gYOvS2L3pDYkCBF_Kc18ETbXj44ssL76H4XL1FuwJW0ju5JjdnqUEd9T6EnxoIaOImSJIGQAuEB7SJHA029dKsJTq5MemAjVeC1e9algzXmuJr8xwerlJvQ4V9NJm2X8lVcJYXFBsfMeZj3a24CKrXE10-gbgIS2t1LG3IWozGcHHH6SHs-qZbVVwoh36rdrX0AM2PpJNT-050MghhT6HEmWr0WLyclN-eqptde6AtcMOappsLyLxTCBye576o6JsFnoSdXmlsqhMsYEvkVc5VcBqStlxUziLxtUQ0JA7kntX8M9C3jW35pn_WJelgiZFLxqhqbrtttNN7zvThp_2xwVoAT_1a-2Yy5Hz2zj7juikhyAwSlwcvk54pYoHBL-JApru00O3GwPJLg2uDstF7dzJdwxb-LFruVZZSSwRchvhOLF_QezjTsWDzYVn2zr_zKUZcd6bnpKR3oUE_Uzax9aZHVsm7C_kcwEzngUxLhEBEzjrqPNjOOZ--BlwQuuSonpy_CyTJhoz9exTkT7Rm78zP_BJSjIrIDKIrLNilkFRhcAMx0-AtJCzRsIvGtjQi3-YjkauTatxqylAtcFFd2qo4Qg5p0p3X8LmkzBJIzbPlZ7HA2KEj7MZs5fWed46SmMs2acUc12veYwL_kvSTqXyUKawQXlgQ4k3XFZebMja59xRBIIhDjrEqSuCacympLh5MD6xpAxEcuuUSWR1bQP1N1q-1Yasd8gYqiiZg7SFW-ZDImU4E2xzvGEBOBffL7KOrJcFGULBGgdzfZoi31rN3ds0YplPFmYutSAecYpo2RolKIIgCetRhMhqZTvmnce0-UZKSovX4MQXAQMx8zCWoPO2WzpxQqpK-RdPV3kfIX3a70RWHGEKlU5cLEoRePmoQRU-G5BWdwM_)


## Game Flow

1. Player joins a lobby
2. Game is created automatically if needed
3. Players place or auto-generate their fleets
4. Boards are confirmed (locked)
5. Game starts (`RUNNING`)
6. Players alternate turns
7. Game can be paused, resumed, or forfeited
8. Game ends when a player wins or forfeits

## API Overview (REST)

Examples of available endpoints:

- `POST /api/games` – Create a new game
- `POST /api/games/{gameCode}/join` – Join a game
- `GET /api/games/{gameCode}` – Get public game state
- `GET /api/games/{gameCode}/state` – Full snapshot (reconnect)
- `POST /api/games/{gameCode}/shots` – Fire a shot
- `POST /api/games/{gameCode}/pause`
- `POST /api/games/{gameCode}/resume`
- `POST /api/games/{gameCode}/forfeit`

Development-only endpoints are available under `/api/dev/**`.

## Real-Time Communication (WebSocket)

The backend uses **STOMP over WebSocket** for:

- Game events (turn changes, shots, pause/resume)
- Lobby events
- In-game chat

Topics follow a clear structure, for example:

- `/topic/games/{gameCode}/events`
- `/topic/games/{gameCode}/chat`
- `/topic/lobbies/{lobbyCode}/events`

All WebSocket payloads are **DTO-based** and do not expose internal IDs unnecessarily.

## Testing Strategy

The project includes a comprehensive test suite:

- **Domain tests**
    - Validate pure game logic without Spring
- **Service tests**
    - Verify state transitions and rules
    - Mock repositories and messaging
- **Controller contract tests**
    - Ensure REST APIs expose only intended data
    - Prevent information leaks
- **WebSocket tests**
    - Validate emitted events and payloads

Mockito is used to isolate components and focus on **behavior**, not implementation details.

## Docker Setup

The complete application can be started using Docker.

- Backend runs in a container
- Database runs in a separate container
- Networking is handled via Docker Compose


This ensures:
- Reproducible setup
- No local environment dependencies
- Easy startup for reviewers

![img.png](img.png)

Setup:
- Clone the git repo and run the command below

```bash
docker compose -f docker-compose.dev.yml up -d --build
```
## Scope & Focus

This project focuses on:

- Object-oriented design
- Clean domain modeling
- Correct state handling
- Secure data exposure
- Testability

## Authors

- **Mert Beyaz** (Frontend)
- **Michael Coppola** (Backend)