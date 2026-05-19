# ─── Stage 1: Compile ───────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY src/CollatzParallel.java .
RUN javac CollatzParallel.java

# ─── Stage 2: Run ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/*.class .

CMD ["java", "CollatzParallel"]
