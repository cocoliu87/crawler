# UberCrawl Front End

You can develop ubercrawl locally or via docker.

## Locally

### 1. First, check you have Node and NPM installed in your machine.

- Node 18
- npm (latest version, or anything above 8.x)

If you don't have it, find a good tutorial on how
to install it in your machine.

### 2. Make sure you have next installed

Run this in your machine:

```
$ npm install next --global
```

### 3. Quick start

- Make sure your Node.js and npm versions are up to date. Preferably node 18 or higher.

- Install dependencies: `npm install`

- Start the server: `npm run dev`

- Views are on: `localhost:3000`

## Docker

### 1. Check you have docker installed in your machine

To check, run this command:

```bash
$ docker --version
Docker version 24.0.6, build ed223bc
```

Also check if you have docker compose:

```bash
$ docker-compose --version
Docker Compose version v2.23.0-desktop.1
```

Documentation for installing Docker:

https://docs.docker.com/engine/install/

### 2. Quick Start

Once you have the two tools ready, simply run this

To start the frontend in dev mode:

```bash
$ ./uc start frontend
```

To stop the frontend in dev mode:

```bash
$ ./uc stop frontend
```

To run the frontend in build mode:

```bash
$ ./uc start frontend-build
```

To stop the frontend build mode:

```bash
$ ./uc stop frontend-build
```

