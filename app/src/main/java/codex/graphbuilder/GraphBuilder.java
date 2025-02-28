package codex.graphbuilder;

import com.github.javaparser.JavaParser;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GraphBuilder {
    // 使用ConcurrentHashMap以支持并行处理
    private Set<String> packages = ConcurrentHashMap.newKeySet();
    private Set<String> classes = ConcurrentHashMap.newKeySet();
    private Set<String> methods = ConcurrentHashMap.newKeySet();
    private Set<String> fields = ConcurrentHashMap.newKeySet();
    private Set<Edge> edges = ConcurrentHashMap.newKeySet();

    // 存储节点的扩展属性
    private Map<String, Map<String, String>> nodeProperties = new ConcurrentHashMap<>();

    /**
     * 解析给定目录中的所有Java文件
     */
    public void parseDirectory(String directoryPath) {
        try {
            List<Path> javaFiles = Files.walk(Path.of(directoryPath))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            System.out.println("找到 " + javaFiles.size() + " 个Java文件");

            // 并行处理所有Java文件
            javaFiles.parallelStream().forEach(this::parseFile);

            System.out.println("解析完成，共发现：");
            System.out.println("- " + packages.size() + " 个包");
            System.out.println("- " + classes.size() + " 个类");
            System.out.println("- " + methods.size() + " 个方法");
            System.out.println("- " + fields.size() + " 个字段");
            System.out.println("- " + edges.size() + " 条边");
        } catch (IOException e) {
            System.err.println("解析目录时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 解析单个Java文件
     */
    private void parseFile(Path filePath) {
        try {
            System.out.println("解析文件: " + filePath);
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();

            // 提取包信息
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getName().asString())
                    .orElse("(default package)");
            packages.add(packageName);

            // 处理类和接口
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                String className = packageName + "." + classDecl.getNameAsString();
                classes.add(className);

                // 收集类签名
                String signature = extractClassSignature(classDecl);
                addNodeProperty(className, "signature", signature);

                // 收集位置信息
                extractLocationInfo(className, classDecl);

                // 添加包与类的包含关系
                edges.add(new Edge(packageName, className, Edge.EdgeType.PACKAGE_CONTAINS));

                // 处理导入语句
                for (ImportDeclaration importDecl : cu.getImports()) {
                    String importName = importDecl.getName().asString();
                    edges.add(new Edge(className, importName, Edge.EdgeType.IMPORT));
                }

                // 处理继承关系
                for (ClassOrInterfaceType extendedType : classDecl.getExtendedTypes()) {
                    String extendedTypeName = resolveTypeName(extendedType.getNameAsString(), cu);
                    edges.add(new Edge(className, extendedTypeName, Edge.EdgeType.EXTENDS));
                }

                // 处理接口实现
                for (ClassOrInterfaceType implementedType : classDecl.getImplementedTypes()) {
                    String implementedTypeName = resolveTypeName(implementedType.getNameAsString(), cu);
                    edges.add(new Edge(className, implementedTypeName, Edge.EdgeType.IMPLEMENTS));
                }

                // 处理方法
                classDecl.getMethods().forEach(method -> {
                    String methodName = className + "." + method.getNameAsString();
                    methods.add(methodName);
                    edges.add(new Edge(className, methodName, Edge.EdgeType.CONTAINS_METHOD));

                    // 收集方法签名
                    String methodSignature = extractMethodSignature(method);
                    addNodeProperty(methodName, "signature", methodSignature);

                    // 收集方法位置信息
                    extractLocationInfo(methodName, method);
                });

                // 处理字段
                classDecl.getFields().forEach(field -> {
                    field.getVariables().forEach(var -> {
                        String fieldName = className + "." + var.getNameAsString();
                        fields.add(fieldName);
                        edges.add(new Edge(className, fieldName, Edge.EdgeType.CONTAINS_FIELD));

                        // 收集字段签名
                        String fieldSignature = extractFieldSignature(field, var);
                        addNodeProperty(fieldName, "signature", fieldSignature);

                        // 收集字段位置信息
                        extractLocationInfo(fieldName, var);
                    });
                });
            });

            // 处理枚举
            cu.findAll(EnumDeclaration.class).forEach(enumDecl -> {
                String enumName = packageName + "." + enumDecl.getNameAsString();
                classes.add(enumName);

                // 收集枚举签名
                String enumSignature = extractEnumSignature(enumDecl);
                addNodeProperty(enumName, "signature", enumSignature);

                // 收集位置信息
                extractLocationInfo(enumName, enumDecl);

                edges.add(new Edge(packageName, enumName, Edge.EdgeType.PACKAGE_CONTAINS));

                // 处理枚举中的方法和字段
                enumDecl.getMethods().forEach(method -> {
                    String methodName = enumName + "." + method.getNameAsString();
                    methods.add(methodName);
                    edges.add(new Edge(enumName, methodName, Edge.EdgeType.CONTAINS_METHOD));

                    // 收集方法签名
                    String methodSignature = extractMethodSignature(method);
                    addNodeProperty(methodName, "signature", methodSignature);

                    // 收集方法位置信息
                    extractLocationInfo(methodName, method);
                });

                enumDecl.getFields().forEach(field -> {
                    field.getVariables().forEach(var -> {
                        String fieldName = enumName + "." + var.getNameAsString();
                        fields.add(fieldName);
                        edges.add(new Edge(enumName, fieldName, Edge.EdgeType.CONTAINS_FIELD));

                        // 收集字段签名
                        String fieldSignature = extractFieldSignature(field, var);
                        addNodeProperty(fieldName, "signature", fieldSignature);

                        // 收集字段位置信息
                        extractLocationInfo(fieldName, var);
                    });
                });
            });
        } catch (Exception e) {
            System.err.println("解析文件 " + filePath + " 时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 提取类签名
     */
    private String extractClassSignature(ClassOrInterfaceDeclaration classDecl) {
        StringBuilder signature = new StringBuilder();

        // 添加访问修饰符
        if (classDecl.getModifiers().isNonEmpty()) {
            classDecl.getModifiers().forEach(modifier ->
                    signature.append(modifier.toString()).append(" "));
        }

        // 添加类或接口关键字
        signature.append(classDecl.isInterface() ? "interface " : "class ");

        // 添加类名
        signature.append(classDecl.getNameAsString());

        // 添加类型参数（泛型）
        if (classDecl.getTypeParameters().isNonEmpty()) {
            signature.append("<");
            for (int i = 0; i < classDecl.getTypeParameters().size(); i++) {
                if (i > 0) {
                    signature.append(", ");
                }
                signature.append(classDecl.getTypeParameters().get(i).toString());
            }
            signature.append(">");
        }

        // 添加继承关系
        if (classDecl.getExtendedTypes().isNonEmpty()) {
            signature.append(" extends ");
            for (int i = 0; i < classDecl.getExtendedTypes().size(); i++) {
                if (i > 0) {
                    signature.append(", ");
                }
                signature.append(classDecl.getExtendedTypes().get(i).toString());
            }
        }

        // 添加实现的接口
        if (classDecl.getImplementedTypes().isNonEmpty()) {
            signature.append(" implements ");
            for (int i = 0; i < classDecl.getImplementedTypes().size(); i++) {
                if (i > 0) {
                    signature.append(", ");
                }
                signature.append(classDecl.getImplementedTypes().get(i).toString());
            }
        }

        return signature.toString();
    }

    /**
     * 提取枚举签名
     */
    private String extractEnumSignature(EnumDeclaration enumDecl) {
        StringBuilder signature = new StringBuilder();

        // 添加访问修饰符
        if (enumDecl.getModifiers().isNonEmpty()) {
            enumDecl.getModifiers().forEach(modifier ->
                    signature.append(modifier.toString()).append(" "));
        }

        // 添加enum关键字和名称
        signature.append("enum ").append(enumDecl.getNameAsString());

        // 添加实现的接口
        if (enumDecl.getImplementedTypes().isNonEmpty()) {
            signature.append(" implements ");
            for (int i = 0; i < enumDecl.getImplementedTypes().size(); i++) {
                if (i > 0) {
                    signature.append(", ");
                }
                signature.append(enumDecl.getImplementedTypes().get(i).toString());
            }
        }

        return signature.toString();
    }

    /**
     * 提取方法签名
     */
    private String extractMethodSignature(MethodDeclaration method) {
        StringBuilder signature = new StringBuilder();

        // 添加访问修饰符
        if (method.getModifiers().isNonEmpty()) {
            method.getModifiers().forEach(modifier ->
                    signature.append(modifier.toString()).append(" "));
        }

        // 添加类型参数（泛型）
        if (method.getTypeParameters().isNonEmpty()) {
            signature.append("<");
            for (int i = 0; i < method.getTypeParameters().size(); i++) {
                if (i > 0) {
                    signature.append(", ");
                }
                signature.append(method.getTypeParameters().get(i).toString());
            }
            signature.append("> ");
        }

        // 添加返回类型和方法名
        signature.append(method.getType().toString())
                .append(" ")
                .append(method.getNameAsString());

        // 添加参数列表
        signature.append("(");
        for (int i = 0; i < method.getParameters().size(); i++) {
            if (i > 0) {
                signature.append(", ");
            }
            Parameter param = method.getParameters().get(i);
            signature.append(param.toString());
        }
        signature.append(")");

        // 添加throws声明
        if (method.getThrownExceptions().isNonEmpty()) {
            signature.append(" throws ");
            for (int i = 0; i < method.getThrownExceptions().size(); i++) {
                if (i > 0) {
                    signature.append(", ");
                }
                signature.append(method.getThrownExceptions().get(i).toString());
            }
        }

        return signature.toString();
    }

    /**
     * 提取字段签名
     */
    private String extractFieldSignature(FieldDeclaration field, VariableDeclarator var) {
        StringBuilder signature = new StringBuilder();

        // 添加访问修饰符
        if (field.getModifiers().isNonEmpty()) {
            field.getModifiers().forEach(modifier ->
                    signature.append(modifier.toString()).append(" "));
        }

        // 添加类型和变量名
        signature.append(var.getType().toString())
                .append(" ")
                .append(var.getNameAsString());

        // 添加初始化值（如果有）
        var.getInitializer().ifPresent(init ->
                signature.append(" = ").append(init.toString()));

        return signature.toString();
    }

    /**
     * 提取位置信息
     */
    private void extractLocationInfo(String nodeName, Node node) {
        node.getRange().ifPresent(range -> {
            Position begin = range.begin;
            Position end = range.end;

            addNodeProperty(nodeName, "startLine", String.valueOf(begin.line));
            addNodeProperty(nodeName, "endLine", String.valueOf(end.line));
//            addNodeProperty(nodeName, "startColumn", String.valueOf(begin.column));
//            addNodeProperty(nodeName, "endColumn", String.valueOf(end.column));

            // 计算代码块长度（行数）
//            int lineCount = end.line - begin.line + 1;
//            addNodeProperty(nodeName, "lineCount", String.valueOf(lineCount));
        });
    }

    /**
     * 添加节点属性
     */
    private void addNodeProperty(String nodeName, String propertyName, String propertyValue) {
        nodeProperties.computeIfAbsent(nodeName, k -> new ConcurrentHashMap<>())
                .put(propertyName, propertyValue);
    }

    /**
     * 尝试解析完整类型名称
     */
    private String resolveTypeName(String typeName, CompilationUnit cu) {
        // 检查是否为已导入的类型
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getName().asString();
            if (importName.endsWith("." + typeName)) {
                return importName;
            }
            // 处理通配符导入
            if (importName.endsWith(".*")) {
                String packageName = importName.substring(0, importName.length() - 2);
                // 我们不能确定这是否是正确的完整路径，但这是最佳猜测
                return packageName + "." + typeName;
            }
        }

        // 如果找不到导入，假设它在同一个包中
        return cu.getPackageDeclaration()
                .map(pd -> pd.getName().asString() + "." + typeName)
                .orElse(typeName);
    }

    /**
     * 导出节点数据到CSV文件，符合Neo4j导入格式
     */
    public void exportNodesToCsv(String directory) {
        try {
            Files.createDirectories(Path.of(directory));

            // 导出包节点
            exportNodeTypeWithProperties(directory + "/packages.csv", packages, "Package", "Package");

            // 导出类节点
            exportNodeTypeWithProperties(directory + "/classes.csv", classes, "Class", "Class");

            // 导出方法节点
            exportNodeTypeWithProperties(directory + "/methods.csv", methods, "Method", "Method");

            // 导出字段节点
            exportNodeTypeWithProperties(directory + "/fields.csv", fields, "Field", "Field");

            System.out.println("已导出所有节点数据到 " + directory);
        } catch (IOException e) {
            System.err.println("导出节点数据时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 导出带属性的节点类型
     */
    private void exportNodeTypeWithProperties(String filePath, Set<String> nodes, String label, String idSpace) throws IOException {
        StringBuilder sb = new StringBuilder();
        // 基本CSV头部
        sb.append("nodeId:ID(").append(idSpace).append("),name");

        // 收集此节点类型的所有可能属性
        Set<String> propertyKeys = new HashSet<>();
        for (String node : nodes) {
            Map<String, String> props = nodeProperties.getOrDefault(node, Collections.emptyMap());
            propertyKeys.addAll(props.keySet());
        }

        // 添加所有属性作为列
        for (String key : propertyKeys) {
            sb.append(",").append(key);
        }

        // 添加标签列
        sb.append(",:LABEL\n");

        // 添加所有节点行
        for (String node : nodes) {
            // 基本ID和名称
            sb.append(escapeCSV(node)).append(",")
                    .append(escapeCSV(node));

            // 添加所有属性值
            Map<String, String> props = nodeProperties.getOrDefault(node, Collections.emptyMap());
            for (String key : propertyKeys) {
                String value = props.getOrDefault(key, "");
                sb.append(",").append(escapeCSV(value));
            }

            // 添加标签
            sb.append(",").append(label).append("\n");
        }

        Files.writeString(Path.of(filePath), sb.toString());
        System.out.println("已导出 " + nodes.size() + " 个 " + label + " 节点到 " + filePath);
    }

    /**
     * 导出关系数据到CSV格式，符合Neo4j导入格式
     */
    public void exportRelationshipsToCsv(String filePath) {
        try {
            StringBuilder sb = new StringBuilder();
            // 添加ID空间引用到START_ID和END_ID
            sb.append(":START_ID,:END_ID,:TYPE\n");

            for (Edge edge : edges) {
                // 确定起点和终点的ID空间
                String sourceIdSpace = determineIdSpace(edge.getSource());
                String targetIdSpace = determineIdSpace(edge.getTarget());

                // 添加带有ID空间的起点和终点
                sb.append(escapeCSV(edge.getSource())).append(",")
                        .append(escapeCSV(edge.getTarget())).append(",")
                        .append(edge.getType()).append("\n");
            }

            Files.writeString(Path.of(filePath), sb.toString());
            System.out.println("已导出 " + edges.size() + " 条关系到 " + filePath);
        } catch (IOException e) {
            System.err.println("导出关系数据时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 根据节点名称确定其所属的ID空间
     */
    private String determineIdSpace(String nodeName) {
        // 确定节点所属的类型（包、类、方法或字段）
        if (packages.contains(nodeName)) {
            return "Package";
        } else if (classes.contains(nodeName)) {
            return "Class";
        } else if (methods.contains(nodeName)) {
            return "Method";
        } else if (fields.contains(nodeName)) {
            return "Field";
        }
        // 如果找不到匹配的类型，默认返回空
        return "";
    }

    /**
     * 导出所有数据（节点和关系）为Neo4j导入格式
     */
    public void exportToNeo4j(String outputDirectory) {
        try {
            Files.createDirectories(Path.of(outputDirectory));

            // 导出节点
            exportNodesToCsv(outputDirectory);

            // 创建修正的关系文件，使用ID空间
            exportCorrectRelationships(outputDirectory);

            // 创建Neo4j导入命令示例文件
            createImportCommandFile(outputDirectory);

            System.out.println("已完成所有Neo4j格式数据导出到 " + outputDirectory);
        } catch (IOException e) {
            System.err.println("导出Neo4j数据时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 导出正确的关系数据，处理ID空间
     */
    private void exportCorrectRelationships(String outputDirectory) throws IOException {
        // 为不同类型的关系创建单独的文件
        Map<Edge.EdgeType, List<Edge>> relationshipsByType = edges.stream()
                .collect(Collectors.groupingBy(Edge::getType));

        for (Map.Entry<Edge.EdgeType, List<Edge>> entry : relationshipsByType.entrySet()) {
            String relType = entry.getKey().toString();
            String filePath = outputDirectory + "/" + relType.toLowerCase() + "_rels.csv";

            StringBuilder sb = new StringBuilder();
            // 添加带有ID空间的标题行
            sb.append(":START_ID,:END_ID,:TYPE\n");

            for (Edge edge : entry.getValue()) {
                String sourceIdSpace = determineIdSpace(edge.getSource());
                String targetIdSpace = determineIdSpace(edge.getTarget());

                // 仅当能确定ID空间时才添加关系
                if (!sourceIdSpace.isEmpty() && !targetIdSpace.isEmpty()) {
                    sb.append(escapeCSV(edge.getSource())).append(",")
                            .append(escapeCSV(edge.getTarget())).append(",")
                            .append(edge.getType()).append("\n");
                }
            }

            Files.writeString(Path.of(filePath), sb.toString());
            System.out.println("已导出 " + entry.getValue().size() + " 条 " + relType + " 关系到 " + filePath);
        }
    }

    /**
     * 创建支持ID空间的关系导出文件
     */
    private void exportRelationshipsWithIdSpaces(String outputDirectory) throws IOException {
        Map<Edge.EdgeType, List<Edge>> relationshipsByType = edges.stream()
                .collect(Collectors.groupingBy(Edge::getType));

        for (Map.Entry<Edge.EdgeType, List<Edge>> entry : relationshipsByType.entrySet()) {
            String relType = entry.getKey().toString();
            String filePath = outputDirectory + "/" + relType.toLowerCase() + "_rels.csv";

            // 收集此类型关系的有效关系（源和目标都有ID空间）
            List<Triple<String, String, String>> validRels = new ArrayList<>();

            for (Edge edge : entry.getValue()) {
                String sourceIdSpace = determineIdSpace(edge.getSource());
                String targetIdSpace = determineIdSpace(edge.getTarget());

                if (!sourceIdSpace.isEmpty() && !targetIdSpace.isEmpty()) {
                    validRels.add(new Triple<>(edge.getSource(), edge.getTarget(), sourceIdSpace + "," + targetIdSpace));
                }
            }

            // 按ID空间组合分组
            Map<String, List<Triple<String, String, String>>> relsByIdSpaces = validRels.stream()
                    .collect(Collectors.groupingBy(Triple::getThird));

            // 为每个ID空间组合创建一个文件
            for (Map.Entry<String, List<Triple<String, String, String>>> idSpaceGroup : relsByIdSpaces.entrySet()) {
                String[] idSpaces = idSpaceGroup.getKey().split(",");
                String sourceIdSpace = idSpaces[0];
                String targetIdSpace = idSpaces[1];

                String idSpaceFilePath = outputDirectory + "/" + relType.toLowerCase() + "_" +
                        sourceIdSpace.toLowerCase() + "_to_" +
                        targetIdSpace.toLowerCase() + ".csv";

                StringBuilder sb = new StringBuilder();
                // 使用正确的ID空间引用
                sb.append(":START_ID(" + sourceIdSpace + "),:END_ID(" + targetIdSpace + "),:TYPE\n");

                for (Triple<String, String, String> rel : idSpaceGroup.getValue()) {
                    sb.append(escapeCSV(rel.getFirst())).append(",")
                            .append(escapeCSV(rel.getSecond())).append(",")
                            .append(relType).append("\n");
                }

                Files.writeString(Path.of(idSpaceFilePath), sb.toString());
                System.out.println("已导出 " + idSpaceGroup.getValue().size() + " 条 " + relType +
                        " 关系 (从 " + sourceIdSpace + " 到 " + targetIdSpace + ") 到 " + idSpaceFilePath);
            }
        }
    }

    /**
     * 创建Neo4j导入命令示例文件
     */
    private void createImportCommandFile(String directory) throws IOException {
        StringBuilder command = new StringBuilder();
        command.append("# Neo4j数据导入命令示例\n");
        command.append("# 使用neo4j-admin import工具导入数据\n\n");

        // Neo4j 5.x语法
        command.append("# Neo4j 5.x\n");
        command.append("neo4j-admin database import full \\\n");
        command.append("  --nodes=").append(directory).append("/packages.csv \\\n");
        command.append("  --nodes=").append(directory).append("/classes.csv \\\n");
        command.append("  --nodes=").append(directory).append("/methods.csv \\\n");
        command.append("  --nodes=").append(directory).append("/fields.csv \\\n");

        // 递归查找目录中所有关系文件
        try {
            List<Path> relationshipFiles = Files.walk(Path.of(directory))
                    .filter(p -> p.toString().endsWith(".csv") && !p.getFileName().toString().matches("(packages|classes|methods|fields)\\.csv"))
                    .collect(Collectors.toList());

            for (Path relFile : relationshipFiles) {
                command.append("  --relationships=").append(relFile).append(" \\\n");
            }
        } catch (IOException e) {
            // 如果无法列出文件，则使用一个通用模板
            for (Edge.EdgeType type : Edge.EdgeType.values()) {
                command.append("  --relationships=").append(directory).append("/")
                        .append(type.toString().toLowerCase()).append("_*.csv \\\n");
            }
        }

        command.append("  --delimiter=\",\" \\\n");
        command.append("  --array-delimiter=\";\" \\\n");
        command.append("  --quote=\"\\\"\" \\\n");
        command.append("  --multiline-fields=true \\\n");
        command.append("  --ignore-empty-strings=true \\\n");
        command.append("  --id-type=string \\\n");
        command.append("  --database=java-knowledge\n\n");

        // Neo4j 4.x语法
        command.append("# Neo4j 4.x\n");
        command.append("neo4j-admin import \\\n");
        command.append("  --database=java-knowledge \\\n");
        command.append("  --nodes=").append(directory).append("/packages.csv \\\n");
        command.append("  --nodes=").append(directory).append("/classes.csv \\\n");
        command.append("  --nodes=").append(directory).append("/methods.csv \\\n");
        command.append("  --nodes=").append(directory).append("/fields.csv \\\n");

        // 再次添加关系文件，但使用Neo4j 4.x语法
        try {
            List<Path> relationshipFiles = Files.walk(Path.of(directory))
                    .filter(p -> p.toString().endsWith(".csv") && !p.getFileName().toString().matches("(packages|classes|methods|fields)\\.csv"))
                    .collect(Collectors.toList());

            for (Path relFile : relationshipFiles) {
                command.append("  --relationships=").append(relFile).append(" \\\n");
            }
        } catch (IOException e) {
            for (Edge.EdgeType type : Edge.EdgeType.values()) {
                command.append("  --relationships=").append(directory).append("/")
                        .append(type.toString().toLowerCase()).append("_*.csv \\\n");
            }
        }

        command.append("  --delimiter=\",\" \\\n");
        command.append("  --array-delimiter=\";\" \\\n");
        command.append("  --quote=\"\\\"\" \\\n");
        command.append("  --id-type=string\n");

        Files.writeString(Path.of(directory + "/import-command.txt"), command.toString());

        // 创建Cypher查询示例文件
        createCypherExamplesFile(directory);
    }

    /**
     * 创建Cypher查询示例文件
     */
    private void createCypherExamplesFile(String directory) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("// Neo4j Cypher查询示例\n\n");

        sb.append("// 1. 查找所有包\n");
        sb.append("MATCH (p:Package) RETURN p;\n\n");

        sb.append("// 2. 查找类及其包含的方法\n");
        sb.append("MATCH (c:Class)-[r:CONTAINS_METHOD]->(m:Method) RETURN c, r, m LIMIT 25;\n\n");

        sb.append("// 3. 查找继承关系\n");
        sb.append("MATCH (c1:Class)-[r:EXTENDS]->(c2:Class) RETURN c1, r, c2;\n\n");

        sb.append("// 4. 查找实现接口的类\n");
        sb.append("MATCH (c:Class)-[r:IMPLEMENTS]->(i:Class) RETURN c, r, i;\n\n");

        sb.append("// 5. 查找包含最多方法的类\n");
        sb.append("MATCH (c:Class)-[r:CONTAINS_METHOD]->(m:Method)\n");
        sb.append("WITH c, count(m) AS methodCount\n");
        sb.append("RETURN c.name AS class, methodCount\n");
        sb.append("ORDER BY methodCount DESC LIMIT 10;\n\n");

        sb.append("// 6. 显示整个继承树\n");
        sb.append("MATCH path = (c:Class)-[:EXTENDS*]->(parent:Class)\n");
        sb.append("WHERE NOT (parent)-[:EXTENDS]->() // 找到最顶层的父类\n");
        sb.append("RETURN path;\n\n");

        Files.writeString(Path.of(directory + "/cypher-examples.txt"), sb.toString());
    }

    /**
     * 转义CSV字段中的特殊字符
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }

        // 如果包含逗号、引号或换行符，需要用引号包围并转义内部引号
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }

    /**
     * 导出到CSV格式（旧版本保留，用于向后兼容）
     */
    public void exportToCsv(String filePath) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Source,Target,Type\n");

            for (Edge edge : edges) {
                sb.append(escapeCSV(edge.getSource())).append(",")
                        .append(escapeCSV(edge.getTarget())).append(",")
                        .append(edge.getType()).append("\n");
            }

            Files.writeString(Path.of(filePath), sb.toString());
            System.out.println("已导出图数据到 " + filePath);
        } catch (IOException e) {
            System.err.println("导出CSV时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Set<String> getPackages() {
        return packages;
    }

    public Set<String> getClasses() {
        return classes;
    }

    public Set<String> getMethods() {
        return methods;
    }

    public Set<String> getFields() {
        return fields;
    }

    public Set<Edge> getEdges() {
        return edges;
    }

    /**
     * 简单的Triple类，用于存储关系的源、目标和ID空间信息
     */
    private static class Triple<A, B, C> {
        private final A first;
        private final B second;
        private final C third;

        public Triple(A first, B second, C third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }

        public A getFirst() {
            return first;
        }

        public B getSecond() {
            return second;
        }

        public C getThird() {
            return third;
        }
    }
}
