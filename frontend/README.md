# Frontend (Battleship)

Angular frontend for the Battleship project.

## Requirements

- Node.js (see `frontend/package.json` for the required npm version)
- npm

## Install

```bash
cd frontend
npm install
```

## Development server

```bash
cd frontend
npm start
```

Open `http://localhost:4200`.

### API proxy

To use the local backend during development, run:

```bash
cd frontend
npx ng serve --proxy-config proxy.conf.json
```

The proxy maps:

- `/api` -> `http://localhost:8080`
- `/ws`  -> `http://localhost:8080`

## Build

```bash
cd frontend
npm run build
```

## Tests (Karma + Jasmine)

```bash
cd frontend
npm test
```

Headless run:

```bash
cd frontend
npx ng test --watch=false --browsers=ChromeHeadless
```

### Test setup

`src/test-setup.ts` is loaded by `tsconfig.spec.json` and provides a global shim
needed by SockJS during tests.

## Project structure

```
src/
  app/
    pages/
    tests/
  types/
```
