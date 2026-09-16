# Auto_Vtb

[English](README_en.md) | [日本語](README_ja.md)

**Auto_Vtb** 是一个 2.5D 虚拟形象（VTuber）自动绑定与 Live2D 导出桌面应用。把分层 PSD 拖进应用，系统会自动完成图层语义识别、左右拆分、自适应网格剖分、变形器与九轴面部经纬网构建、头发多摆物理与果冻眼动力学，并立刻带待机动作、眨眼、口型与视线追踪动起来；内置实时微调（胸部晃动、表情预设、眼球移动、绿幕），并支持一键导出可在 Live2D Cubism Modeler 5 中二次编辑的 `.cmo3` 工程与运行时 `.moc3` 文件族。

---

## 核心特性

### 实时动态

- **身体 / 胸部微调**：胸部晃动幅度、胸部上下位置、手臂高度 / 手臂位置，全部以实时滑杆调整，所见即所得。
- **头发物理**：前发 / 后发独立摆动幅度与柔软度滑杆，发根固定、发梢柔软的多摆物理。
- **表情预设**：普通、微笑、微笑张嘴、惊讶、ジト目（半闭眼）、左眨眼、右眨眼 7 个一键预设；导出时随模型写出 Cubism 表情文件（`.exp3.json`）并注册到 `model3.json`，VTube Studio 等运行时可直接识别并绑定为热键。
- **眼球移动**：视线跟随 + 待机自然游移，另可手动眼 X / 眼 Y 滑杆微调。
- **绿幕背景**：棋盘 / 绿幕 / 深色一键切换，绿幕可直接配合 OBS 色键抠像。
- **三语界面**：简体中文 / English / 日本語 即时切换。

### 自动绑定流水线

- **自适应网格剖分**：基于可分离高斯平滑滤波与 95th 百分位自适应二值化消除边缘噪点；周期三次 Bézier 曲线拟合与曲率加权加密采样；受约束 Delaunay 剖分与 Lawson 翻边拓扑收敛。
- **变形器 (Warp) 生成**：
  - **眼 / 口变形**：眼睛与眉毛共享透视平面约束，瞳孔反向补偿，睫毛沿 Alpha 权重中线生成平滑闭眼 U 形曲线；嘴部向中线向心压缩闭合，牙齿与舌头自动以嘴部为剪切蒙版。
  - **九轴构建**：`AngleX (±45°) × AngleY (±30°)` 面部经纬网，结合 C1 连续水平展开/压缩曲线、垂直仰俯曲率与四角交叉修正项。
- **动画**：自动生成 6 秒无缝循环 `idle.motion3.json` 待机动作（呼吸、头部/身体摇摆、眨眼）。
- **物理**：前后发解耦独立跟随头壳，$v^3$ 立方发梢摆幅梯度多摆物理；眨眼驱动二阶阻尼弹簧的果冻眼挤压/回弹（`ParamEyeBallForm`）。

### Live2D 导出

一键同步导出可在 Live2D Cubism Modeler 5 中二次编辑的 `.cmo3` 完整工程与运行时 `.moc3` 文件族（`.model3.json`、`.cdi3.json`、`physics3.json`、`idle.motion3.json` 及纹理贴图集）；内置中立姿态保真、极限姿态完整性与变形器镜像对称性三道几何自检闸门。

### 本地 Agent / MCP 桥（可选）

应用启动后可在本机提供带 Bearer Token 的 Streamable HTTP MCP，供 ChatGPT/Codex、Gemini 等宿主读取工程与参数、渲染 PNG View、导入透明素材与编辑关键帧。此功能为可选项，不影响常规的自动绑定与导出使用。

---

## 文档索引

| 文档 | 描述 |
| :--- | :--- |
| [用户操作指南 (docs/zh/USER_GUIDE.md)](docs/zh/USER_GUIDE.md) | 桌面 GUI、历史树、日志坞、快捷键及 CLI 参数说明 |
| [Live2D SDK 配置指南 (docs/zh/CUBISM_SDK_SETUP.md)](docs/zh/CUBISM_SDK_SETUP.md) | 官方 Native SDK 许可政策与离屏硬件加速预览配置 |
| [PSD 图层规范与命名指南 (docs/zh/PSD_LAYER_SPEC.md)](docs/zh/PSD_LAYER_SPEC.md) | 语义标签、侧别规则、连通域拆分与分层规范 |
| [变形器与算法数学规范 (docs/zh/DEFORMER_AND_PARAMETER_SPEC.md)](docs/zh/DEFORMER_AND_PARAMETER_SPEC.md) | 变形器拓扑、九轴经纬网数学模型与动力学公式 |
| [工程文件格式 (docs/PROJECT_FORMAT.md)](docs/PROJECT_FORMAT.md) | 工程文件格式说明 |

---

## 快速上手

### 环境要求
- **Java Runtime**：仅从源码构建或启动时需要 JDK 21 或更高版本。
- **操作系统**：Windows 10/11 x64（可配置官方 Native SDK 获得与官方运行时一致的渲染对照），亦支持 Linux / macOS。
- **Live2D 官方 SDK 说明**：本项目源码**不包含且不分发** Live2D 官方 SDK 专有二进制，开箱即可使用内置渲染与全部导出功能；如需官方渲染一致性验证请参阅 [Live2D SDK 配置指南](docs/zh/CUBISM_SDK_SETUP.md)。

### 启动桌面应用 (GUI)

- **Windows 一键启动**：运行根目录下的 `run-gui.bat`。
- **Gradle 启动**：
  ```powershell
  # Windows
  .\gradlew.bat run

  # Linux / macOS
  ./gradlew run
  ```

#### 常用快捷键

| 操作 | 快捷键 / 鼠标指令 |
| :--- | :--- |
| **画布缩放** | 鼠标滚轮（以光标为中心，`0.05x ~ 64.0x`） |
| **画布平移** | 鼠标中键拖拽 或 左键拖拽空白 |
| **居中适配** | `F` / `Home` / `0` |
| **选择画元** | 鼠标左键单击画元 |
| **打开 PSD** | `Ctrl + O` |
| **重新分析** | `Ctrl + R` |
| **生成并导出** | `Ctrl + G` |
| **导出到...** | `Ctrl + Shift + G` |

---

### 命令行批处理 (CLI)

```powershell
# 基础运行
.\gradlew.bat run --args="--input ./sample.psd --output ./output"

# 进阶参数配置
.\gradlew.bat run --args="--input ./sample.psd --output ./output --atlas 8192 --mesh-spacing 48 --head-strength 1.2 --lang zh"
```

| 参数 | 类型 | 默认值 | 说明 |
| :--- | :---: | :---: | :--- |
| `--input <path>` | 路径 | *(必需)* | 输入分层 PSD 文件路径 |
| `--output <path>` | 路径 | `PSD同级/psd2live-output` | 导出模型文件族的输出目录路径 |
| `--lang <zh\|en\|ja>` | 字符串 | 系统语言 | 界面与日志语言（支持 `zh` / `en` / `ja`） |
| `--atlas <size>` | 整数 | `4096` | 贴图集尺寸（`256 ~ 16384`） |
| `--mesh-spacing <px>` | 整数 | `64` | 网格基础间距（像素） |
| `--head-strength <val>`| 浮点数 | `1.0` | 头部转动九轴形变幅度（`0.0 ~ 4.0`） |
| `--body-strength <val>`| 浮点数 | `1.0` | 身体与呼吸动作幅度（`0.0 ~ 4.0`） |
| `--no-physics` | 开关 | `false` | 不生成物理配置 |
| `--no-cmo3` | 开关 | `false` | 跳过 `.cmo3` 工程导出 |
| `--no-moc3` | 开关 | `false` | 跳过 `.moc3` 运行时导出 |

---

## PSD 命名速查表

> [!TIP]
> **PSD 原画制作与构图核心建议**：
> - **嘴巴张开且带描边更佳**：原画口部需绘制为最大张口状态；嘴唇外缘带清晰描边效果更佳，向心压缩闭合时能自然贴合成清晰唇线。
> - **睫毛仅限眼部上半部分**：`eyelash` 图层必须仅绘制上睫毛，严禁混入下睫毛或下眼眶线。
> - **初始头部允许自然倾斜**：系统会自动识别初始角度并以此作为中立原点展开确认转动范围。
> - **身体须保持正立（过于倾斜不受支持）**：躯干动作与胸腔呼吸起伏严格基于垂直坐标系构建。
> 
> 更多分层规则请参阅 [PSD 图层规范与命名指南 (docs/zh/PSD_LAYER_SPEC.md)](docs/zh/PSD_LAYER_SPEC.md)。

| 部件 | 推荐英文名 | 常用中文/日文别名 | 行为说明 |
| :--- | :--- | :--- | :--- |
| **头发** | `front hair`, `back hair` | 前发, 后发, 前髪, 後ろ髪 | 独立头壳跟随 + $v^3$ 发梢物理摆动 |
| **脸部** | `face`, `facedetail` | 脸, 脸部, 脸颊, 顔, 肌, 腮红 | 面部轮廓与细节 |
| **眼睛** | `eyewhite`, `eyelash`, `irides`, `eye_close` | 眼白, 睫毛, 瞳孔, 闭眼, 目, 瞳 | 自动左右拆分，瞳孔剪切，上睫毛平滑闭眼 |
| **眉毛** | `eyebrow` | 眉, 眉毛, まゆ | 自动左右拆分与透视联动 |
| **鼻子** | `nose` | 鼻, 鼻子 | 最大立体空间深度位移 |
| **嘴巴** | `mouth`, `mouth_open` | 嘴, 口, 嘴巴, 张嘴 | 最大张口原图，向中线向心压缩闭口 |
| **口腔内部件** | `tooth-t`, `tooth-b`, `tongue` | 上牙, 下牙, 舌头, 歯, 舌 | 可选部件，自动以 mouth 为剪切蒙版 |
| **耳朵** | `ears` | 耳, 耳朵 | 随头部转动负深度位移与透视淡出 |
| **身体** | `neck`, `topwear`, `bottomwear`, `legwear` | 脖子, 上衣, 裤子, 裙子, 身体 | 身体偏航、俯仰、倾斜与呼吸（身体须保持正立） |
| **饰品** | `headwear`, `earwear`, `neckwear`, `tail`, `wings` | 头饰, 耳饰, 项链, 尾巴, 翅膀 | 挂载于对应父级变形器 |

---

## 变形器层级与参数体系

```text
Root (Canvas Space)
 └─ DeformBodyXY (ParamBodyAngleX, ParamBodyAngleY)
     └─ DeformBodyZBreath (ParamBodyAngleZ, ParamBreath)
         └─ DeformHeadRotation (ParamAngleZ)
             └─ DeformHeadContainer (ParamAngleX, ParamAngleY 头壳跟随)
                 ├─ DeformFaceNinePose (ParamAngleX, ParamAngleY 九轴经纬网)
                 │   ├─ Eye / Iris / Brow / Nose / Mouth / Ear
                 │   └─ FaceDetails
                 ├─ HairFrontFollow → HairFrontPhysics (ParamHairFront)
                 ├─ HairBackFollow  → HairBackPhysics  (ParamHairBack)
                 └─ HeadAccessories
```

| 参数 ID | 名称 | 范围 | 默认值 | 作用说明 |
| :--- | :--- | :---: | :---: | :--- |
| `ParamAngleX` / `Y` / `Z` | 头部角度 X / Y / Z | `[-45..45]` / `[-30..30]` / `[-30..30]` | `0` | 头部偏航、仰俯与平面旋转 |
| `ParamEyeLOpen` / `ROpen` | 左/右眼 开闭 | `[0, 1]` | `1` | 睫毛平滑闭眼 U 形线 |
| `ParamEyeBallX` / `Y` | 视线 X / Y | `[-1, +1]` | `0` | 瞳孔注视追踪 |
| `ParamEyeBallForm` | 果冻眼 | `[-1, +1]` | `0` | 眨眼驱动瞳孔挤压回弹动力学 |
| `ParamBrowLY` / `RY` | 左/右眉 上下 | `[-1, +1]` | `0` | 眉毛上下移动 |
| `ParamMouthForm` | 嘴 变形 | `[-1, +1]` | `0` | 嘴角抬升/下压与宽度 |
| `ParamMouthOpenY` | 嘴 开闭 | `[0, 1]` | `0` | 完整张口 → 中线闭口缝平滑插值 |
| `ParamBodyAngleX` / `Y` / `Z`| 身体 X / Y / Z | `[-10, +10]` | `0` | 躯干偏航、俯仰与倾斜 |
| `ParamBreath` | 呼吸 | `[0, 1]` | `0` | 胸腔高斯呼吸起伏 |
| `ParamHairFront` / `Back` | 前/后发 摇摆 | `[-1, +1]` | `0` | 前后发多摆物理模拟 |

---

## 导出产物

```text
output_dir/
├── sample.cmo3                    # 可在 Live2D Modeler 5 中二次编辑的完整工程
├── sample.moc3                    # 运行时模型文件 (MOC5 基线)
├── sample.model3.json             # 运行时配置文件 (贴图、物理、动作接线)
├── sample.cdi3.json               # 显示名称元数据
├── sample.physics3.json           # 物理模拟配置 (头发多摆 + 果冻眼)
├── sample.idle.motion3.json       # 6 秒无缝循环平滑待机动作
├── sample.4096/texture_00.png     # 纹理贴图集
└── sample.psd2live.json           # 诊断报告与映射元数据
```

---

## 构建与测试

```powershell
# 编译并打包独立运行 ZIP
.\gradlew.bat clean test distZip

# 运行全套单元测试
.\gradlew.bat test
```

---

## 许可证与致谢

- **开源许可证**：本项目采用 [GNU General Public License v3.0 (GPL-3.0)](LICENSE)。
- **项目渊源**：自动绑定与 Live2D 导出流水线集成了开源项目 [psd2live](https://github.com/tsunehimatoi/psd2live)（GPL-3.0）的实现。
- **第三方参考与致谢**：详见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)（Umamo、Stretchy Studio、Model Context Protocol Kotlin SDK、Ktor 等）。

---

## 免责声明

- Auto_Vtb 是独立开发的开源项目，与 Live2D Inc. 及其关联方不存在任何隶属、授权或赞助关系。
- `Live2D`、`Cubism`、`.cmo3`、`.moc3` 等名称与文件扩展名仅用于格式兼容性说明，其商标与知识产权归各自权利人所有。本项目不包含且不分发 Live2D 官方 SDK。
- 本项目按“现状”提供，请在正式生产前备份原始 PSD 文件，并在目标软件中检查生成效果。
