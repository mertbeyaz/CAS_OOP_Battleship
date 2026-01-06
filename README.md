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

![PlantUML model](https://www.plantuml.com/plantuml/svg/bLRTZzeu47z7ud-mUBXjUzrLbxOlqTwg0T8IbGMKZwvwBoT3PeDlWuqIfmjRzNy_Uvo41CBIYuJmVFetu_6CFsMIfZ9V9zrEziZuXgPqJPO9pJ9RofGjSWdkKd2VFGDvr-rqEnVahrEV5dxLfi39gv5OKyPVdp7eTkWYaobTIhCf6T0C72wPt96VtGuXVxB88c7eZf1Ofa0bHBQqo4Wj0hO6vEURw9Z_IxitNYTYiTWHs4hWKSiUAWSopuXbz7oaK91eUYWYK39VfOF7o8xfMdE-w3zoGSH6Ci5fGbwzkxgwVeqgFo3D5DCYYT16DIneTHwALgiXqLiU0r4dyy3YGdk8H9vIZGR5cuJk50uT6ClapHp9MnGj14sZIZBGb15aAo4hlgy8mrv3bj4OAibmHUMTuHc5PPwcm6MGfzpiuqQaTd8fPFaQFA3Hhatyn3Y7z4XIIvkHdKvSda7ApRcK2QfPOu2dZPzO95VAad69z0fOl98rXdXIy1HjK1IriNm43NKXH0AqolWIrYeFjHIopRpatZP0DKGlb8HI2tORZAnO6amfKtPusaeqsrDb8QKwWg4GQSIukXK7UuiNjbnMHWSUYfDC988HqaEYB1mX3vEaGCGWJVOjSmeD6BKoEer6AacpxbM7YHfR_JAjwVBKfuLJvzuY7jOX2SGZy3CM--jgZD3c1PGeRi1K3cIOwRwa-2vNHwBEMJKCdYS9NIHms4ngb_OujXVRxZUQv41cYtzXAREpFNSVMQDda9kR7-X7e9_Rsv_xLd5_S4EGEJwy9Wy9W1p10-EiA4kJ3HtqyzNWYlIt_RVzxVktrziFwlUngkyxLIUCbvxg1RAln4DqHTwtjVrt3GzdSkRmV7qkL_jCcLwiG80o0vEm4iEzxGRkz8jU-auGplN22wVJWZUtGz_1FlVPdRh-s1aT3wVVTFyvd4mQnhLhtsuydddktxDfO4yqzStn0dTeJuvwWRd8xIxkNDz7Lsw08ORJh_hVddYEFVh-Zp-U1KTz3HjMgxUHumVUxBlZwQB8zBkYhzb3BtHzpQpOxpLxO0V1nF77xhnaVq1js_FSXlR7tplq0JmpMEKyrcyKEuvJY5LjPUVVAHV58u8c6V5KfxFAGQwQF0FLQjMWbUG9E2yKpL6PTU-wHyJb9Udf1qBFM5eanYklNIEZzDpj85ctwVVA-MUHOgYtQvMTreptGvqtFTFtBO9JlaLSxySMAOT-JL7h5H4qUuT5p6F418GuBiFr0oIguHjKJHZrqOT15NEpqdvJRLQ5ZbFORE3ZC-KF1DpjO5Hj5iLGEjOe4rKRKDrEFVfMRpXs381Ya9YkdqA2MMXBwU40iKYwfvH1uVt_c7GxdnGIVBJ_1m00)


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