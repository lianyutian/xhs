# **仓库指南**

## **项目结构与模块组织**

本仓库是一个基于 Maven 的 Java 后端项目。当前根目录包含 [`pom.xml`](https://www.qianwen.com/Users/lm/code/project-code/xhs/xhs-backend/pom.xml)，Maven 包装器元数据位于 `.mvn/` 目录下，本地 IDE 设置位于 `.idea/` 目录下。

贡献者应将应用程序代码保存在 `src/main/java` 目录下，配置文件放在 `src/main/resources` 目录下，测试代码放在 `src/test/java` 目录下。请镜像 `pom.xml` 中声明的包名 `com.lianyutian.xhs`。例如，将一个服务类放在 `src/main/java/com/lianyutian/xhs/service/ExampleService.java`。

## **构建、测试和开发命令**

在仓库根目录下使用 Maven：

- `mvn clean compile`: 清理之前输出并编译项目。
- `mvn test`: 运行单元测试套件。
- `mvn clean package`: 在 `target/` 目录下构建可发布的制品。
- `mvn dependency:tree`: 在添加或升级库之前检查依赖关系解析。

如果后续添加了 Maven 包装器，请在文档和 CI 中优先使用 `./mvnw ...`。

## **编码风格与命名约定**

使用 4 格缩进和 UTF-8 源文件。遵循标准的 Java 命名规范：

- 类名: `PascalCase` (帕斯卡命名法)
- 方法和字段: `camelCase` (驼峰命名法)
- 常量: `UPPER_SNAKE_CASE` (大写下划线命名法)
- 包名: 小写字母，用域名分隔，例如 `com.lianyutian.xhs`

保持类的单一职责，避免在一个文件中混合控制器、服务和持久化逻辑。提交前请运行 IDE 的格式化功能；目前尚无专门的格式化或静态检查工具配置。

## **测试指南**

在 `src/test/java` 下添加测试，其包路径应与生产代码相匹配。单元测试类命名为 `*Test`，当需要外部服务或完整应用程序装配时，集成测试类命名为 `*IT`。

每当有新功能或错误修复时，都应在实践中包含测试。在开启拉取请求（Pull Request）之前，在本地运行 `mvn test`。

