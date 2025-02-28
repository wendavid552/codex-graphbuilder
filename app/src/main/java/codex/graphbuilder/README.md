# Java代码知识图谱生成器

这个工具可以分析Java代码库，并生成一个知识图谱，展示代码中的包、类、方法和字段之间的关系。

## 导出格式说明

该工具使用标准的Neo4j批量导入CSV格式，包括：

1. **节点文件**：
   - 包含 `:ID(IdSpace)` - 节点ID，带有ID空间标识
   - 包含 `name:string` - 节点名称
   - 包含 `:LABEL` - 节点标签

2. **关系文件**：
   - 包含 `:START_ID(IdSpace)` - 关系起点ID，带有ID空间标识
   - 包含 `:END_ID(IdSpace)` - 关系终点ID，带有ID空间标识
   - 包含 `:TYPE` - 关系类型

## 导入到Neo4j

### 前提条件

- 安装Neo4j数据库 (4.x 或更高版本)
- 确保Neo4j bin目录在系统路径中
- 确保数据库未运行或创建新数据库

### 导入步骤

1. 运行此程序，生成CSV文件到`neo4j-import`目录
2. 使用生成的导入命令导入数据

```bash
# 对于Neo4j 5.x
neo4j-admin database import full \
  --nodes=packages.csv \
  --nodes=classes.csv \
  --nodes=methods.csv \
  --nodes=fields.csv \
  --relationships=import_*.csv \
  --relationships=package_contains_*.csv \
  --relationships=extends_*.csv \
  --relationships=implements_*.csv \
  --relationships=contains_method_*.csv \
  --relationships=contains_field_*.csv \
  --delimiter="," \
  --array-delimiter=";" \
  --quote="\"" \
  --multiline-fields=true \
  --id-type=string \
  --database=java-knowledge

# 对于Neo4j 4.x
neo4j-admin import \
  --database=java-knowledge \
  --nodes=packages.csv \
  --nodes=classes.csv \
  --nodes=methods.csv \
  --nodes=fields.csv \
  --relationships=import_*.csv \
  --relationships=package_contains_*.csv \
  --relationships=extends_*.csv \
  --relationships=implements_*.csv \
  --relationships=contains_method_*.csv \
  --relationships=contains_field_*.csv \
  --delimiter="," \
  --array-delimiter=";" \
  --quote="\"" \
  --id-type=string
```

3. 启动Neo4j数据库
4. 在浏览器中打开Neo4j Browser (`http://localhost:7474`)
5. 连接到数据库

### 示例Cypher查询

```cypher
// 查找所有包
MATCH (p:Package) RETURN p;

// 查找类及其包含的方法
MATCH (c:Class)-[r:CONTAINS_METHOD]->(m:Method) RETURN c, r, m LIMIT 25;

// 查找继承关系
MATCH (c1:Class)-[r:EXTENDS]->(c2:Class) RETURN c1, r, c2;

// 查找实现接口的类
MATCH (c:Class)-[r:IMPLEMENTS]->(i:Class) RETURN c, r, i;

// 查找包含最多方法的类
MATCH (c:Class)-[r:CONTAINS_METHOD]->(m:Method)
WITH c, count(m) AS methodCount
RETURN c.name AS class, methodCount
ORDER BY methodCount DESC LIMIT 10;

// 显示整个继承树
MATCH path = (c:Class)-[:EXTENDS*]->(parent:Class)
WHERE NOT (parent)-[:EXTENDS]->() // 找到最顶层的父类
RETURN path;
```

## 数据模型

图谱中包含以下节点和关系：

### 节点类型
- `Package`: Java包
- `Class`: Java类或接口
- `Method`: 类中的方法
- `Field`: 类中的字段

### 关系类型
- `IMPORT`: 包导入关系
- `PACKAGE_CONTAINS`: 包含关系（包包含类）
- `EXTENDS`: 继承关系
- `IMPLEMENTS`: 实现接口关系
- `CONTAINS_METHOD`: 类包含方法关系
- `CONTAINS_FIELD`: 类包含字段关系

## ID空间说明

为了确保节点ID在各自的域中是唯一的，我们使用了以下ID空间：

- `Package`: 用于包节点
- `Class`: 用于类和接口节点
- `Method`: 用于方法节点
- `Field`: 用于字段节点

这确保了即使不同类型的节点具有相同的名称，它们也能在图数据库中正确地区分。
