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

![PlantUML model](https://www.plantuml.com/plantuml/svg/ZLRTZzeu47z7ud-mUBXjUzrLbxOlqTwg0T8IbGMKZrQzbvD9PeElYOqIfmjRzN-lFKv20cnP98Jd-pVZyGmV2abpMMPflrTyPtn3SvgHAAL5OKkPirKfmOq4zzaZa3VzNhztGVvvwgE5dxKfSF9Y8ZBA-Cip1lqUNHKofv4qGSc85k1moUIE_Ept2FcRnHO9GtU2H9G3bH3RqY8JTGXO1iWlD_4n_vTsRxpEn6gr8x2LmECJCLGDP5aGo-ZvIA4WrVHOnA1alYe7Jv2Tq4npFkaVSa74HZB1QKfUlBcwkdw9AZyWBnJJ8gdGHYqiQ7KUYbPh8T4XZu6ecxbdIQKzHgBCgKQ3uarATmg7JemLycQ9v2sALeBcSICPQSe8YhMG3TzNX65dK1GqWKOoNbDvPtX68Ihp7BWCofoRE6vCMZUNGr5cu06DTmTYWl87nZdaHvQjkXdPqvKh2yYvkPYwGYrhpF1fumSMoxMoP5mYlGQMh6MB8HuKF4KR55LpHDz1Grq9aG9jIXv1fWhHgY7RB6lkRGTKH_I7db1gmMw3cLL7WobdoWwlMuaMUwhkf5ndC1OYZnbNp-8WjlX3oAWnEl1GNMKg4OoG7X9rvmXvM2G58W5fgc-PM-Z0QFNHqsYaadGRNusOk1FgrpDcqnUQba0Mg_yXaiNPmRH_hPs7HMvkVg0VWNvkRt_ktzFNWov2vlImbUvJ03c1UyPPTNKcCJrA7gz6LsIu7RuURj--ljw-Kz_tJG_SgLeoNdjg5t5_MuVegWfkM_5t7GzdQkRmCZjNgtsbpC1I842PWIbOZU6hxGRk_9C--auGBlN12-VpYhUqGz_1MV7HdRl-r9aSJ_1dklyOpcOTuzRJE6qyNNZklujvOCyqzSNn0dTipuxUYsdsqoxkNDz7Lsw0AORpp_hNddcEFVdsdpzT14Tl7n_rIMyJnm-ynJV7qqrHwEKxrEon5xg-PZRijvezieDWvlXJTrcptw6sxNbkH_lzyovz18yCrYLFz2AtaoI7HFLMSNwPNrIRbgO5yTGqiEfjfmOX0pMEr3QIv04uhnJDLPdpu7f0nEKb6UWjEZ2M5j6BpxX6HksvtqEoRJCSr3l28jNcEwrLJoEp0mzrNWtCRBG8hi8JulRCiaYz6LkAMg_Ae3ixB68sjaa8SLo6sJsaQYWQL5qOxVM0ILMxfT5-rGHhKiTDPGBukCl_Gi3z7cProg8QtCSQTQ5QGxpVQsT_qXkEPWtWWphXFylV)


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