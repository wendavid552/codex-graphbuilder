/*
 * This project aimed at generating a knowlege graph from give Java code repository.
 * It parallelly parse (by JavaParser) each .java file inside the repository and generate a graph.
 * The graph is then exported as a .csv file, to be imported into a Neo4j database.
 */
package codex.graphbuilder;

import java.nio.file.Paths;

public class App {
    private static void parseFile(String path) {
        GraphBuilder graphBuilder = new GraphBuilder();
        graphBuilder.parseDirectory(path);

        // 导出到当前目录下的neo4j-import文件夹
        String outputDirectory = Paths.get("").toAbsolutePath().toString() + "/neo4j-import";
        graphBuilder.exportToNeo4j(outputDirectory);
    }

    public static void main(String[] args) {
        // 允许从命令行传入路径参数
        final String path = args.length > 0 ? args[0] : "/Users/xxx/Code/xxx";

        System.out.println("开始分析代码路径: " + path);
        parseFile(path);
        System.out.println("分析完成，数据已导出到neo4j-import目录");
    }
}
