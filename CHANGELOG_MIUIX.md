# Sesame-GR 仿MIUIX风格 更新日志

## 版本 — UI 全面重构

### 🎨 UI 全面改版
- **首页统计卡片**：重新设计为 HTML 风格 5×3 表格（今日/本月/今年 × 收/帮/浇/被水/浇水）, 15 个独立 TextView 精确布局
- **字体统一**：全局动态按钮统一使用 `sans-serif` + `Typeface.NORMAL` + 16sp, 覆盖设置、配置、日志等所有页面
- **Switch 样式重设**：白色滑块(#FFF) + 深蓝色(#3482FF) 开启态 / 浅灰色(#D0D0D0) 关闭态
- **导航栏**：修改为 4 个 Tab 底部导航栏, 使用自定义矢量图标（首页/日志/配置/设置）, 点击态纯黑高亮
- **导航栏切换**：4 Tab 底部导航栏, TabHome/TabLogs/TabSettings/TabOther 独立 ScrollView 切换
- **按钮样式**：主按钮蓝底白字, 次按钮灰底, 文字按钮透明背景
- **对话框圆角**：全局对话框使用 仿MIUIX风格 圆角卡片风格
- **设置页重构**：TabHost 替换为左侧竖排 Tab 按钮 + Fragment/FrameLayout 内容区
- **扩展功能页**：顶部间距修正, 与其他二级页面风格统一
- **二级配置界面**：与主界面风格统一, 使用 仿MIUIX风格 按钮样式

### 📝 日志模块增强
- **日志记录开关**：日志 Tab 新增 7 个 Switch 控制（森林/庄园/其他/全部/抓包/异常/运行记录）
- **跨 ClassLoader 持久化**：使用 `System.setProperty()` 即时共享 + 异步 Properties 文件持久化, 解决 Xposed 多 ClassLoader 隔离问题
- **开关联动**：关闭对应 Switch 时自动清除日志文件
- **日志过滤**：所有 `forest/farm/other/record/debug/error` 方法入口添加开关检查, 关闭时不再写入

### ⚙️ 功能改进
- **启动弹窗优化**：新增「不再显示」CheckBox, 使用 SharedPreferences 记录版本号, 同版本不重复弹出, 版本更新后重新显示
- **信息卡片**：新增模块版本/设备型号/架构信息展示

### 🐛 Bug 修复
- **配置图标重复**：移除重复的配置图标
- **文件关闭 EPERM 错误**：`FileUtil.close()` 中抑制 Xposed 进程内 EPERM 异常日志, 减少日志噪音

### 🔧 技术细节
- **新增文件**：
  - `activity_about.xml` — 关于页面布局
  - `dialog_startup_tips.xml` — 启动弹窗布局（含"不再显示"复选框）
  - `AboutActivity.java` — 关于页面 Activity
  - `ListViewerActivity.java` — 好友监视列表查看器
  - `LogDetailActivity.java` — 日志详情查看器
  - 12+ 个 MIUIX drawable 资源（按钮/卡片/开关/Section/状态徽章/对话框背景）
- **新增字符串**：`no_more_reminder`（中/英）
- **修改文件**：40 个文件, +2086 / -1323 行
