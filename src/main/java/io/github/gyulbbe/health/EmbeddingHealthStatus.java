package io.github.gyulbbe.health;

public record EmbeddingHealthStatus(
        String status,
        Integer dimension,
        String message
) {
    public static EmbeddingHealthStatus up(int dimension) {
        return new EmbeddingHealthStatus("UP", dimension, null);
    }

    public static EmbeddingHealthStatus down(String message) {
        return new EmbeddingHealthStatus("DOWN", null, message);
    }
}
