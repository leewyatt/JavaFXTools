# JavaFX Tools

[![Version](https://img.shields.io/jetbrains/plugin/v/com.itcodebox.fxtools.id?label=version)](https://plugins.jetbrains.com/plugin/14287-javafx-tools)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/com.itcodebox.fxtools.id)](https://plugins.jetbrains.com/plugin/14287-javafx-tools)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

JavaFX 一站式开发工具包 — CSS 智能提示、Gutter 预览、FXML 代码辅助、Ikonli 图标浏览器、FxmlKit 集成。

同时支持 IntelliJ IDEA **社区版** 和 **旗舰版**，要求 **2024.2+**、Java **17+**。

<!-- 截图：插件整体概览，展示 .css 文件中的 gutter 图标 + 补全弹窗 -->
![概览](images/overview.png)

---

## 功能特性

### CSS 智能

**属性补全与文档**
- 210+ 个内置 `-fx-*` 属性，提供类型感知的值补全
- 第三方库自动检测 — ControlsFX、GemsFX、JFoenix 的 CSS 属性，依赖在类路径上即可用
- 快速文档 (F1)，展示多库来源的属性说明
- CSS 变量跨文件补全与解析
- CSS 过渡属性支持 (`transition`、`transition-property`、`transition-duration` 等)

<!-- 截图：CSS 补全弹窗，显示 -fx-* 属性及类型信息 -->
![CSS 补全](images/css-completion.png)

**Gutter 预览**
- 颜色预览 — hex、rgb、rgba、hsl、hsla、命名颜色、`derive()`
- 渐变色预览 — `linear-gradient()`、`radial-gradient()`，圆形图标
- SVG 路径预览 — `-fx-shape` 渲染为缩放路径图标
- 特效预览 — `dropshadow()`、`innershadow()`，含模糊可视化
- Ikonli 图标预览 — `-fx-icon-code` 渲染为 SVG gutter 图标
- CSS 变量解析 — 变量链式解析（最深 10 层）至最终颜色/渐变/SVG
- **多值 Paint 支持** — `-fx-background-color` 和 `-fx-border-color` 每个 paint 段显示一个图标（最多 4 个）

<!-- 截图：gutter 中并排显示颜色方块、渐变圆、SVG 路径、特效图标 -->
![Gutter 预览](images/gutter-previews.png)

**点击编辑**
- 点击颜色图标 → 打开内嵌 PaintPicker，实时写回
- 点击渐变图标 → 打开 PaintPicker 渐变模式
- 点击特效图标 → 打开 Effect Editor (DropShadow / InnerShadow，4 种模糊类型)
- 点击 SVG 图标 → 打开路径预览，带尺寸控制
- 所有编辑支持单次 Ctrl+Z 撤销

<!-- 截图：从 gutter 点击打开的 PaintPicker 弹窗，显示色轮 + 渐变色标 -->
![PaintPicker](images/paintpicker.png)

**内联 CSS (Java & FXML)**
- Java `setStyle("...")` — gutter 预览 + 点击编辑 + 自动弹出补全
- FXML `style="..."` — 同样的预览和编辑支持
- Text Block 支持 — 每行独立显示 gutter 图标
- `SVGPath.setContent("...")` — 只读 SVG 预览
- Ctrl+Click 从内联 CSS 变量跳转到 `.css` 定义处

<!-- 截图：Java 代码中 setStyle() 显示内联 gutter 图标 -->
![内联 CSS](images/inline-css.png)

---

### Ikonli 图标集成

**图标浏览器 ToolWindow**
- 84 个图标包，55,000+ 个图标，来自 [Ikonli](https://github.com/kordamp/ikonli) 库
- 跨包模糊关键词搜索
- 图标包筛选器
- 详情面板，展示预览、图标名、包名、许可证信息
- 一键复制：SVG 路径 / Java 代码 / CSS 代码 / Maven / Gradle 坐标

<!-- 截图：图标浏览器 ToolWindow，展示图标网格 + 详情面板 -->
![图标浏览器](images/icon-browser.png)

**代码辅助**
- CSS 中 `-fx-icon-code` 值补全，每个候选项带 SVG 预览
- FXML 中 `<FontIcon iconLiteral="..."/>` 补全
- Java 枚举常量 gutter 图标（如 `FontAwesome.HOME` 行内显示图标）
- 类路径感知：仅项目类路径中存在的图标包才会出现在补全中

<!-- 截图：-fx-icon-code 补全弹窗，显示图标字面量及 SVG 预览 -->
![图标补全](images/icon-completion.png)

---

### FXML 代码辅助

**导航**
- View ↔ FXML ↔ CSS 双向 gutter 导航
- Controller ↔ FXML 导航（适用于所有 JavaFX 项目，不限于 FxmlKit）
- `@FxmlPath` 注解：Ctrl+Click 跳转、补全、重命名重构
- 资源路径导航 — `<Image url="..."/>`、`<fx:include source="..."/>`
- `%key` 国际化导航到 `.properties` 文件（需要 `com.intellij.properties` 插件）

**代码检查与快速修复** (13 项检查)
- 缺少 FXML / Controller / CSS 文件 → 创建文件 Quick Fix
- Controller 中缺少 fx:id 字段 → 创建字段 Quick Fix，自动推断类型
- 缺少事件处理方法 → 创建方法 Quick Fix（33 种事件类型）
- @FXML 字段类型不匹配 → 修改类型 Quick Fix
- 未使用的 @FXML 字段和方法检测
- 无效资源路径、未使用的 CSS 选择器、国际化 key 校验

<!-- 截图：FXML 编辑器中显示检查警告及 Quick Fix 弹窗 -->
![FXML 检查](images/fxml-inspections.png)

---

### FxmlKit 集成

基于约定的 JavaFX MVC 结构化模式。

**New → FxmlKit View 创建向导**
- 一个对话框创建 View + ViewProvider + Controller + FXML + CSS 文件
- 分段式 View 类型选择器 (View / ViewProvider)
- 可选的国际化资源包配置，支持 Locale 选择
- 实时文件树预览，展示即将生成的文件结构

<!-- 截图：New FxmlKit View 对话框，显示文件卡片和文件树预览 -->
![FxmlKit 向导](images/fxmlkit-wizard.png)

**属性生成** (Alt+Insert / Cmd+N)
- 10 种属性类型：String、Integer、Long、Float、Double、Boolean、Object、List、Map、Set
- ReadOnly 包装器生成
- 延迟初始化选项
- CSS Styleable 属性生成，自动创建 `CssMetaData` 样板代码
- 对话框内实时代码预览

---

### 字体文件操作

- **右键 .ttf/.otf** → 复制字体族名 / 复制 @font-face CSS
- **智能粘贴**：@font-face 代码块粘贴到 CSS 文件时自动计算相对路径
- 支持多文件选中

---

## 安装

**从 JetBrains Marketplace 安装：**

1. IntelliJ IDEA → Settings → Plugins → Marketplace
2. 搜索 **"JavaFX Tools"**
3. 点击 Install，重启 IDE

**手动安装：**

1. 从 [Releases](https://github.com/niceboylee/JavaFXTools/releases) 下载最新 `.zip`
2. Settings → Plugins → ⚙️ → Install Plugin from Disk → 选择 `.zip` 文件

---

## 兼容性

| 要求 | 版本 |
|------|------|
| IntelliJ IDEA | 2024.2+（社区版或旗舰版） |
| Java | 17+ |
| JavaFX SDK | 不需要（插件使用 Swing/IntelliJ 平台 UI） |

**第三方 CSS 库支持：**

插件自动检测项目类路径上的以下库，并提供其 CSS 属性：

| 库 | 标记类 | 支持内容 |
|---|--------|---------|
| ControlsFX | `org.controlsfx.control.GridView` | ControlsFX CSS 属性 |
| GemsFX | `com.dlsc.gemsfx.DialogPane` | GemsFX CSS 属性 |
| JFoenix | `com.jfoenix.controls.JFXButton` | JFoenix Material CSS 属性 |
| Ikonli | `org.kordamp.ikonli.Ikon` | `-fx-icon-code`、`-fx-icon-size`、`-fx-icon-color` |

---

## 从源码构建

```bash
# 克隆
git clone https://github.com/niceboylee/JavaFXTools.git
cd JavaFXTools

# 构建
./gradlew buildPlugin

# 启动沙箱 IDE（加载插件）
./gradlew runIde
```

需要 Gradle 8.14 和 JBR 21（在 `gradle.properties` 中配置）。

---

## 致谢

- 内嵌的 PaintPicker（颜色与渐变编辑器）改写自 Gluon 的 [Scene Builder](https://github.com/gluonhq/scenebuilder) 中的 PaintPicker 组件（原版为 JavaFX 实现，本项目改写为 Swing）。感谢 Scene Builder 团队提供的优秀原始实现。
- Ikonli 相关图标数据来自 [Ikonli](https://github.com/kordamp/ikonli) 库。感谢 Andres Almiray 为 JavaFX 生态创建并维护了如此丰富的图标包集合。
- 图标浏览器中收录的 84 个图标包源自 100 多个开源图标库（如 FontAwesome、Material Design Icons、Weather Icons 等）。衷心感谢每一个图标项目背后的作者和社区。完整的图标库名称及链接列表将在后续版本中整理补充。

---

## 开源许可

[MIT License](LICENSE) — Copyright (c) 2022 LeeWyatt
