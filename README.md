#  nap511
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/zerorooot/nap511) 
[![Latest Release](https://img.shields.io/github/v/release/zerorooot/nap511?label=Latest%20Release)](https://github.com/zerorooot/nap511/releases)
[![License](https://img.shields.io/github/license/zerorooot/nap511.svg)](https://github.com/zerorooot/nap511/blob/main/LICENSE)


一个Android自用的[115网盘](https://115.com/)客户端，用于[Jetpack Compose](https://developer.android.com/jetpack/compose)练手

# 截图

<table>
  <tr style="text-align: center; vertical-align: middle;">
    <td><a href="./assets/01.jpg"><img src="./assets/01.jpg?raw=true" width="300" alt="Screenshot 001"/></a></td>
    <td><a href="./assets/02.jpg"><img src="./assets/02.jpg?raw=true" width="300" alt="Screenshot 002"/></a></td>
    <td><a href="./assets/03.jpg"><img src="./assets/03.jpg?raw=true" width="300" alt="Screenshot 003"/></a></td>
  </tr>
  <tr style="text-align: center; vertical-align: middle;">
    <td><a href="./assets/04.jpg"><img src="./assets/04.jpg?raw=true" width="300" alt="Screenshot 004"/></a></td>
    <td><a href="./assets/05.jpg"><img src="./assets/05.jpg?raw=true" width="300" alt="Screenshot 005"/></a></td>
    <td><a href="./assets/06.jpg"><img src="./assets/06.jpg?raw=true" width="300" alt="Screenshot 006"/></a></td>
  </tr>
</table>


# 功能说明

本工具提供以下核心功能模块：

1. **登录模块**
    - 支持网页端账号密码登录、Cookie 登录，以及主动登出。

2. **网盘文件管理**
    - 基础操作：剪切、删除、重命名、新建文件夹
    - 批量操作：多选
    - 其他：回收站、获取下载链接、文件搜索、在线解压

3. **离线任务管理**
    - 查看离线列表，支持跳转至对应网盘文件夹
    - 对离线视频文件可在线查看
    - 支持单个删除及清空全部离线任务

4. **文件预览**
    - 支持小文本、音频、照片、视频等常见格式的在线查看

5. **离线下载方式**
    - 支持磁力链接离线下载
    - 支持种子文件离线下载

6. **自定义设置**
    - 可调整单次文件请求数量
    - 可设置默认离线保存位置
    - 等等

7. **快捷跳转与唤起**
    - 磁力链接自动唤起
    - 支持 URL Scheme 唤起（格式：`nap511://command/addTask?param=${encodeURIComponent(text)}`）

8. **外挂字幕**
    - 自动匹配网盘同目录字幕，也可浏览网盘其它目录或选择本机字幕文件。
    - 在线字幕默认使用视频名搜索；在「字幕 → 字幕源 → 在线字幕（迅雷）」中可手动输入片名、剧名或集数，点击「搜索」或键盘搜索键提交。
    - 支持清空关键词、一键「用视频名搜索」；重开弹窗保留本次播放的关键词与搜索结果。
    - 支持 SRT、ASS/SSA、WebVTT；兼容 UTF-8、带 BOM 的 UTF-16/UTF-32 和 GBK/GB18030 编码。ASS/SSA 转为纯文本 SRT，不保留原字幕的特效、定位与绘图。
    - 可调整字幕字号、颜色、字体、粗体、底色及同步延迟。

9. **播放器操作**
    - 长按画面临时使用 2× / 3× 倍速，松手、取消手势或离开播放页恢复原速。
    - 常规速度支持 0.5×～3×；双击左 / 右侧快退 / 快进（10 / 15 / 30 秒），双击中间暂停 / 继续。
    - 「选集」浏览同目录视频，按集数自然排序，支持关键词筛选、上一集 / 下一集和自动连播开关（默认关闭）。
    - 从网盘历史进度续播，已接近片尾的视频从头开始；换集同步更新播放记录及字幕，不沿用上一集字幕。
    - 锁定控制防误触，按返回键先解锁；支持 15 / 30 / 60 分钟定时暂停、本集结束后停止和从头播放。
    - 窄屏底部操作区可横向滚动，进度条和横竖屏按钮保持独立可用。

> **不支持功能**：
> - 文件的上传与下载
> - 两步验证（2FA）
> - 安全密钥相关操作

# 下载

https://github.com/zerorooot/nap511/releases
## 本仓库构建

`main` 更新仅触发 `.github/workflows/release.yml`：同一普通 Runner 内运行 JVM 单测、构建并验证签名后发布 Release APK。Debug 工作流仅保留手动触发，不运行模拟器或额外虚拟机。

Release 使用 GitHub Secrets 中的固定签名。首次从不同签名的 Debug / 上游版本切换时可能需要卸载旧版，请先保留所需设置；后续本仓库 Release 可覆盖升级。

Windows 开发时可运行 `python tools/check_subtitle_icu.py`，用系统 ICU 检查字幕正则，避免 JVM 宽松语法掩盖 Android 上的正则错误。该检查不消耗 GitHub Actions 额度，也不替代真机播放验证。
