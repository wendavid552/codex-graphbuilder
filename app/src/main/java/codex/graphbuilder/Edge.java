package codex.graphbuilder;

import java.util.Objects;

/**
 * 表示图中的边
 */
public class Edge {
    private final String source;
    private final String target;
    private final EdgeType type;

    public Edge(String source, String target, EdgeType type) {
        this.source = source;
        this.target = target;
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public EdgeType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edge edge = (Edge) o;
        return Objects.equals(source, edge.source) &&
                Objects.equals(target, edge.target) &&
                type == edge.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, type);
    }

    @Override
    public String toString() {
        return source + " -[" + type + "]-> " + target;
    }

    public enum EdgeType {
        PACKAGE_CONTAINS,  // 包包含类
        CONTAINS_METHOD,   // 类包含方法
        CONTAINS_FIELD,    // 类包含字段
        EXTENDS,           // 继承关系
        IMPLEMENTS,        // 实现接口关系
        IMPORT             // 导入关系
    }
}
