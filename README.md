# Collatz Conjecture — Parallel Computing (Docker)

Обчислення гіпотези Колатца для чисел від 1 до 10 000 000  
з використанням чотирьох підходів паралельних обчислень на Java.

## Підходи

| # | Підхід | Ідея |
|---|--------|------|
| 1 | Sequential | Еталон, один потік |
| 2 | Manual Threads (static split) | N потоків, кожен отримує рівний діапазон |
| 3 | **ThreadPool + dynamic queue** | Атомарна черга чанків — жоден потік не простоює |
| 4 | ForkJoinPool (work-stealing) | Потоки крадуть завдання з черг одне одного |

## Запуск у Docker

### Вимоги
- [Docker Desktop](https://www.docker.com/products/docker-desktop/)

### 1. Клонувати репозиторій

```bash
git clone <URL>
cd collatz
```

### 2. Варіант A — через docker compose (рекомендовано)

```bash
docker compose up --build
```

### 2. Варіант B — вручну

```bash
# Зібрати образ
docker build -t collatz .

# Запустити контейнер
docker run --rm collatz
```

### Керування кількістю потоків

За замовчуванням `THREAD_COUNT = availableProcessors()`. Можна задати руками:

```bash
# через docker run
docker run --rm -e THREAD_COUNT=8 collatz

# через docker compose — розкоментувати блок environment у docker-compose.yml
```

## Очікуваний вивід

```
╔══════════════════════════════════════════════════════════════╗
║        Collatz Conjecture — Parallel Computing               ║
╚══════════════════════════════════════════════════════════════╝
  Numbers      : 1 .. 10 000 000
  CPU cores    : 12
  THREAD_COUNT : 12
  CHUNK_SIZE   : 50 000

[1/4] Sequential...
[2/4] Manual Threads (static distribution)...
[3/4] ThreadPool + dynamic queue (no idle threads)...
[4/4] ForkJoinPool (work-stealing)...

┌─────────────────────────────────────────────────────────────────────────────┐
│  Approach                                                Time   Avg steps   Speedup│
├─────────────────────────────────────────────────────────────────────────────┤
  Sequential (1 thread)                                2114 ms   avg=155.27   x1.00
  Manual Threads (12 th, static split)                  304 ms   avg=155.27   x6.95
  ThreadPool + dynamic queue (12 th, chunk=50000)        244 ms   avg=155.27   x8.66
  ForkJoinPool / work-stealing (12 th)                  300 ms   avg=155.27   x7.05
└─────────────────────────────────────────────────────────────────────────────┘
```

## Структура проекту

```
collatz/
├── src/
│   └── CollatzParallel.java   # Вихідний код
├── Dockerfile                 # Multi-stage build (compile + run)
├── docker-compose.yml         # Зручний запуск
├── .dockerignore
└── README.md
```

## Dockerfile — multi-stage build

```
Stage 1 (builder): eclipse-temurin:21-jdk-alpine  → компілює .java → .class
Stage 2 (runtime): eclipse-temurin:21-jre-alpine  → запускає .class
```

Фінальний образ містить лише JRE (~100 MB), без JDK.
