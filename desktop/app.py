from __future__ import annotations

import os
import shutil
import sys
import uuid
from pathlib import Path

ROOT = Path(os.environ.get("QWEN_VOICE_HOME") or (Path(sys.executable).resolve().parent if getattr(sys, "frozen", False) else Path(__file__).resolve().parent.parent)).resolve()
os.environ["HF_HOME"] = str(ROOT / "work" / "hf-cache")
os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["MODELSCOPE_CACHE"] = str(ROOT / "work" / "modelscope-cache")
os.environ["NUMBA_CACHE_DIR"] = str(ROOT / "work" / "numba-cache")
os.environ["TEMP"] = str(ROOT / "work" / "temp")
os.environ["TMP"] = str(ROOT / "work" / "temp")
sys.dont_write_bytecode = True

from PySide6.QtCore import QEvent, QLockFile, QObject, QPoint, QPointF, QRectF, Qt, QThread, QUrl, Signal, Slot
from PySide6.QtGui import QColor, QDesktopServices, QFont, QLinearGradient, QPainter, QPainterPath, QPen, QPixmap
from PySide6.QtMultimedia import QAudioOutput, QMediaPlayer
from PySide6.QtWidgets import (
    QAbstractButton, QApplication, QButtonGroup, QComboBox, QFileDialog, QFrame, QGraphicsEffect, QGridLayout,
    QHBoxLayout, QLabel, QLineEdit, QMainWindow, QMessageBox, QPlainTextEdit,
    QPushButton, QScrollArea, QSizePolicy, QSlider, QStackedWidget, QVBoxLayout,
    QWidget,
)

from engine import Engine
from storage import Storage


PRESETS = [
    ("Vivian", "明亮、略带棱角的年轻女声", "中文"),
    ("Serena", "温暖、柔和的年轻女声", "中文"),
    ("Uncle_Fu", "低沉醇厚的成熟男声", "中文"),
    ("Dylan", "清晰自然的年轻男声", "北京话"),
    ("Eric", "活泼、略带沙哑的男声", "四川话"),
    ("Ryan", "富有节奏感的男声", "英语"),
    ("Aiden", "清亮的美国男声", "英语"),
    ("Ono_Anna", "轻快活泼的女声", "日语"),
    ("Sohee", "温暖细腻的女声", "韩语"),
]

STYLE = """
QWidget { font-family: 'Microsoft YaHei UI', 'Microsoft YaHei', sans-serif; color: #172229; font-size: 13px; }
QMainWindow { background: transparent; }
QWidget#windowOuter { background: transparent; }
QWidget#root { background: #F4E6DD; }
QWidget#sidebar { background: #FAF0EC; border-right: 1px solid #ECDDD8; }
QWidget#content { background: #F8F1EE; }
QWidget#page, QStackedWidget { background: #F8F1EE; }
QFrame#card { background: #FFF9F7; border: 1px solid #EEE4E0; border-radius: 17px; }
QFrame#inner { background: #FAF4F2; border: 1px solid #EEE5E2; border-radius: 13px; }
QFrame#drop { background: #EEF9F6; border: 1px dashed #77CEC6; border-radius: 15px; }
QLabel { background: transparent; border: none; }
QLabel#pageTitle { color: #111B25; font-size: 30px; font-weight: 700; }
QLabel#pageEnglish { color: #68727A; font-size: 15px; }
QLabel#subtitle { color: #59666F; font-size: 14px; }
QLabel#cardTitle { color: #131E26; font-size: 17px; font-weight: 700; }
QLabel#sectionTitle { color: #18242B; font-size: 14px; font-weight: 700; }
QLabel#muted { color: #738087; font-size: 12px; }
QLabel#accent { color: #0DAE9F; font-weight: 700; }
QLabel#badge { color: #008C82; background: #D8F4EF; border-radius: 10px; padding: 4px 9px; font-size: 12px; }
QLabel#logo { background: #31C3B7; color: white; font-size: 23px; font-weight: 700; border-radius: 14px; }
QPushButton { background: #FBF6F4; color: #243037; border: 1px solid #E7DEDA; border-radius: 12px; padding: 8px 13px; font-weight: 600; }
QPushButton:hover { background: #EAF8F5; border-color: #9CDCD3; }
QPushButton:disabled { background: #F0EBE8; color: #A5AAA9; border-color: #E9E1DD; }
QPushButton#primary { background: #1AB5A8; color: white; border: none; font-weight: 700; }
QPushButton#primary:hover { background: #079D91; }
QPushButton#primary:disabled { background: #B2D7D0; color: #F8FBFA; }
QPushButton#nav { background: transparent; border: none; border-radius: 12px; text-align: left; padding: 11px 17px 11px 50px; font-size: 14px; }
QPushButton#nav:hover { background: #EDF8F5; }
QPushButton#nav:checked { color: #078F86; background: #C7EEE8; }
QPushButton#chip:checked { color: #008E83; border: 1px solid #22C1B4; background: #E5F8F4; }
QLineEdit, QPlainTextEdit, QComboBox { background: #FCFAF9; border: 1px solid #DDDAD9; border-radius: 11px; padding: 9px 11px; selection-background-color: #A5E9DF; }
QLineEdit:focus, QPlainTextEdit:focus, QComboBox:focus { border: 1px solid #45C4BE; }
QLineEdit:disabled, QPlainTextEdit:disabled { color: #9BA4A3; background: #F2EEEC; }
QComboBox { min-height: 20px; }
QSlider::groove:horizontal { height: 5px; background: #E3DEDB; border-radius: 2px; }
QSlider::sub-page:horizontal { background: #45C4BE; border-radius: 2px; }
QSlider::handle:horizontal { background: white; border: 1px solid #D9D6D4; width: 17px; margin: -6px 0; border-radius: 8px; }
QScrollArea { border: none; background: transparent; }
QScrollBar:vertical { background: transparent; width: 9px; margin: 4px; }
QScrollBar::handle:vertical { background: #D7CECA; min-height: 25px; border-radius: 4px; }
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical { height: 0; }
QWidget#windowTitleBar { background: #FAF0EC; }
"""


class RoundedClipEffect(QGraphicsEffect):
    """Clip the whole widget tree with a device-scaled antialiased vector path."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.radius = 19.0
        self._mask_key = None
        self._mask = None

    def set_radius(self, radius):
        if self.radius != radius:
            self.radius = radius
            self._mask_key = None
            self.update()

    def draw(self, painter):
        offset = QPoint()
        source = self.sourcePixmap(Qt.CoordinateSystem.LogicalCoordinates, offset, QGraphicsEffect.PixmapPadMode.NoPad)
        if source.isNull():
            return
        dpr = source.devicePixelRatio()
        width, height = source.width() / dpr, source.height() / dpr
        key = (source.width(), source.height(), dpr, self.radius)
        if self._mask_key != key:
            mask = QPixmap(source.size())
            mask.setDevicePixelRatio(dpr)
            mask.fill(Qt.GlobalColor.transparent)
            p = QPainter(mask)
            p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
            p.setPen(Qt.PenStyle.NoPen)
            p.setBrush(Qt.GlobalColor.white)
            path = QPainterPath()
            path.addRoundedRect(QRectF(0, 0, width, height), self.radius, self.radius)
            p.drawPath(path)
            p.end()
            self._mask_key, self._mask = key, mask
        result = QPixmap(source.size())
        result.setDevicePixelRatio(dpr)
        result.fill(Qt.GlobalColor.transparent)
        p = QPainter(result)
        p.drawPixmap(QPointF(0, 0), source)
        p.setCompositionMode(QPainter.CompositionMode.CompositionMode_DestinationIn)
        p.drawPixmap(QPointF(0, 0), self._mask)
        p.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver)
        if self.radius:
            p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
            p.setBrush(Qt.BrushStyle.NoBrush)
            p.setPen(QPen(QColor(255, 255, 255, 150), 1.0))
            p.drawRoundedRect(QRectF(0.5, 0.5, width - 1.0, height - 1.0), self.radius, self.radius)
        p.end()
        painter.drawPixmap(offset, result)


class ShadowBackdrop(QWidget):
    """Paint a soft vector shadow on the translucent window, without bitmap assets."""

    margin = 14

    def paintEvent(self, event):
        p = QPainter(self)
        p.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source)
        p.fillRect(self.rect(), Qt.GlobalColor.transparent)
        p.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver)
        if self.window().isMaximized():
            return
        p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        p.setPen(Qt.PenStyle.NoPen)
        base = QRectF(self.rect()).adjusted(self.margin, self.margin, -self.margin, -self.margin)
        for spread in range(12, 0, -1):
            alpha = max(1, round(8 * ((13 - spread) / 12) ** 2))
            p.setBrush(QColor(52, 74, 72, alpha))
            p.drawRoundedRect(base.adjusted(-spread, -spread, spread, spread), 19 + spread, 19 + spread)


class TrafficButton(QAbstractButton):
    """A vector-painted title-bar button, with native keyboard focus and tooltips."""

    COLORS = {
        'windowClose': ('#FF5F57', '#E34A42'),
        'windowMinimize': ('#FFBD2E', '#DEA123'),
        'windowMaximize': ('#28C840', '#1FAB34'),
    }

    def __init__(self, name, parent=None):
        super().__init__(parent)
        self.setObjectName(name)
        self.setFixedSize(14, 14)
        self.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setAttribute(Qt.WidgetAttribute.WA_Hover, True)

    def paintEvent(self, event):
        fill, stroke = self.COLORS[self.objectName()]
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        p.setBrush(QColor(stroke if self.underMouse() or self.isDown() else fill))
        p.setPen(QPen(QColor(stroke), 0.8))
        p.drawEllipse(QRectF(self.rect()).adjusted(0.6, 0.6, -0.6, -0.6))
        if self.hasFocus():
            p.setBrush(Qt.BrushStyle.NoBrush)
            p.setPen(QPen(QColor('#4C7772'), 1.0, Qt.PenStyle.DotLine))
            p.drawEllipse(QRectF(self.rect()).adjusted(1.5, 1.5, -1.5, -1.5))


class WindowTitleBar(QWidget):
    """A drag surface that retains the familiar three window controls."""

    def __init__(self, window):
        super().__init__(window)
        self._drag_offset = None
        self.setObjectName("windowTitleBar")
        self.setFixedHeight(34)
        row = QHBoxLayout(self)
        row.setContentsMargins(20, 0, 0, 0)
        row.setSpacing(8)
        for name, tooltip, action in (
            ("windowClose", "关闭窗口", window.close),
            ("windowMinimize", "最小化", window.showMinimized),
            ("windowMaximize", "最大化或还原", window.toggle_maximized),
        ):
            control = TrafficButton(name, self)
            control.setToolTip(tooltip)
            control.setAccessibleName(tooltip)
            control.clicked.connect(action)
            row.addWidget(control)
        row.addStretch()

    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            self._drag_offset = event.globalPosition().toPoint() - self.window().frameGeometry().topLeft()
            event.accept()
            return
        super().mousePressEvent(event)

    def mouseMoveEvent(self, event):
        if self._drag_offset is not None and event.buttons() & Qt.MouseButton.LeftButton:
            if not self.window().isMaximized():
                self.window().move(event.globalPosition().toPoint() - self._drag_offset)
            event.accept()
            return
        super().mouseMoveEvent(event)

    def mouseReleaseEvent(self, event):
        self._drag_offset = None
        super().mouseReleaseEvent(event)

    def mouseDoubleClickEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            self.window().toggle_maximized()
            event.accept()
            return
        super().mouseDoubleClickEvent(event)


class ResizeEdge(QWidget):
    """Transparent edge grip using the system window manager for resizing."""

    def __init__(self, parent, edges, cursor):
        super().__init__(parent)
        self.edges = edges
        self.setCursor(cursor)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)

    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            handle = self.window().windowHandle()
            if handle and handle.startSystemResize(self.edges):
                event.accept()
                return
        super().mousePressEvent(event)


class ContentBackdrop(QWidget):
    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        painter.fillRect(self.rect(), QColor("#F8F1EE"))
        gradient = QLinearGradient(0, 0, self.width(), 0)
        gradient.setColorAt(0, QColor("#F8F1EE"))
        gradient.setColorAt(0.52, QColor("#D7F1EB"))
        gradient.setColorAt(1, QColor("#F8ECE8"))
        painter.fillRect(0, 0, self.width(), 155, gradient)
        path = QPainterPath()
        path.moveTo(self.width() * 0.22, 115)
        path.cubicTo(self.width() * 0.40, 110, self.width() * 0.40, 15, self.width() * 0.57, 48)
        path.cubicTo(self.width() * 0.70, 70, self.width() * 0.70, 155, self.width() * 0.90, 150)
        path.lineTo(self.width() * 0.22, 155)
        path.closeSubpath()
        painter.fillPath(path, QColor(147, 223, 210, 35))


def paint_line_icon(p, kind, color="#16B6A9"):
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    p.setBrush(Qt.BrushStyle.NoBrush)
    pen = QPen(QColor(color), 1.8)
    pen.setCapStyle(Qt.PenCapStyle.RoundCap)
    pen.setJoinStyle(Qt.PenJoinStyle.RoundJoin)
    p.setPen(pen)
    if kind == "wave":
        for x, h in ((3, 7), (7, 13), (11, 20), (15, 13), (19, 7)):
            p.drawLine(QPointF(x, 12 - h / 2), QPointF(x, 12 + h / 2))
    elif kind == 0:
        p.drawRoundedRect(QRectF(8, 2, 8, 13), 4, 4)
        p.drawArc(QRectF(5, 5, 14, 14), 180 * 16, 180 * 16)
        p.drawLine(12, 19, 12, 22)
        p.drawLine(8, 22, 16, 22)
    elif kind in (1, 2):
        p.drawEllipse(QRectF(4, 3, 7, 7))
        p.drawArc(QRectF(2, 11, 12, 11), 0, 180 * 16)
        if kind == 1:
            p.drawEllipse(QRectF(14, 5, 6, 6))
            p.drawArc(QRectF(13, 12, 9, 9), 0, 180 * 16)
        else:
            p.drawLine(17, 10, 17, 19)
            p.drawLine(13, 14, 21, 14)
    elif kind == 3:
        p.drawEllipse(QRectF(3, 3, 18, 18))
        p.drawLine(12, 6, 12, 12)
        p.drawLine(12, 12, 16, 15)
    else:
        p.drawEllipse(QRectF(7, 7, 10, 10))
        p.drawEllipse(QRectF(10, 10, 4, 4))
        for x1, y1, x2, y2 in ((12, 2, 12, 5), (12, 19, 12, 22), (2, 12, 5, 12),
                                (19, 12, 22, 12), (5, 5, 7, 7), (17, 17, 19, 19),
                                (19, 5, 17, 7), (5, 19, 7, 17)):
            p.drawLine(x1, y1, x2, y2)


class VectorIconLabel(QLabel):
    def __init__(self, kind, color="#16B6A9", role=None):
        super().__init__()
        self.kind, self.color = kind, color
        if role:
            self.setObjectName(role)

    def paintEvent(self, event):
        super().paintEvent(event)
        p = QPainter(self)
        side = min(self.width(), self.height(), 26)
        p.translate((self.width() - side) / 2, (self.height() - side) / 2)
        p.scale(side / 24.0, side / 24.0)
        paint_line_icon(p, self.kind, self.color)


class VectorNavButton(QPushButton):
    def __init__(self, text, index):
        super().__init__(text)
        self.icon_kind = index
        self.setObjectName('nav')
        self.setCursor(Qt.CursorShape.PointingHandCursor)

    def paintEvent(self, event):
        super().paintEvent(event)
        p = QPainter(self)
        p.translate(17, (self.height() - 23) / 2)
        p.scale(23 / 24.0, 23 / 24.0)
        paint_line_icon(p, self.icon_kind, '#059F94')


def label(text="", role=None, wrap=False):
    w = QLabel(text)
    w.setTextFormat(Qt.TextFormat.PlainText)
    if role:
        w.setObjectName(role)
    w.setWordWrap(wrap)
    return w


def button(text, role=None, clicked=None):
    w = QPushButton(text)
    w.setCursor(Qt.CursorShape.PointingHandCursor)
    if role:
        w.setObjectName(role)
    if clicked:
        w.clicked.connect(clicked)
    return w


def card(title=None, note=None):
    frame = QFrame()
    frame.setObjectName("card")
    layout = QVBoxLayout(frame)
    layout.setContentsMargins(20, 18, 20, 18)
    layout.setSpacing(12)
    if title:
        layout.addWidget(label(title, "cardTitle"))
    if note:
        layout.addWidget(label(note, "muted", True))
    return frame, layout


def clear_layout(layout):
    while layout.count():
        item = layout.takeAt(0)
        if item.widget():
            widget = item.widget()
            widget.hide()
            widget.setParent(None)
            widget.deleteLater()
        elif item.layout():
            clear_layout(item.layout())


def format_time(ms):
    sec = max(0, int(ms / 1000))
    return f"{sec // 60:02d}:{sec % 60:02d}"


class GenerateWorker(QObject):
    progress = Signal(str)
    result = Signal(object)
    error = Signal(str)
    finished = Signal()

    def __init__(self, engine, args):
        super().__init__()
        self.engine = engine
        self.args = args

    @Slot()
    def run(self):
        try:
            result = self.engine.synthesize(**self.args, progress=self.progress.emit)
            self.result.emit(result)
        except Exception as exc:
            self.error.emit(str(exc) or exc.__class__.__name__)
        finally:
            self.finished.emit()


class MainWindow(QMainWindow):
    def __init__(self, root=ROOT):
        super().__init__()
        self.setWindowFlag(Qt.WindowType.FramelessWindowHint, True)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, True)
        self.resize_grips = []
        self.root = Path(root)
        self.storage = Storage(root=self.root)
        self.engine = Engine(root=self.root)
        self.settings = self.storage.load_settings()
        self.voice = {"kind": "preset", "id": "Serena", "name": "Serena"}
        self.voice_audio = None
        self.history_selected = None
        self.current_audio = None
        self.thread = None
        self.worker = None
        self.pending = None
        self.job_mode = None
        self.preview_name = None
        self.unsaved_result = None
        self.unsaved_metadata = None
        self.audio_output = QAudioOutput(self)
        self.player = QMediaPlayer(self)
        self.player.setAudioOutput(self.audio_output)
        self.player.positionChanged.connect(self.update_player_time)
        self.player.durationChanged.connect(self.update_player_duration)
        self.player.errorOccurred.connect(self.player_error)
        self.player.playbackStateChanged.connect(self.update_play_button)
        self.setWindowTitle("Qwen-voice · AI Voice Studio")
        self.resize(1450, 960)
        self.setMinimumSize(970, 680)
        self.setStyleSheet(STYLE)
        self.build_ui()
        self.refresh_library()
        self.refresh_history()
        self.sync_voice()
        self.show_page(0)

    def toggle_maximized(self):
        if self.isMaximized():
            self.showNormal()
        else:
            self.showMaximized()

    def resizeEvent(self, event):
        super().resizeEvent(event)
        self.update_window_frame()

    def changeEvent(self, event):
        super().changeEvent(event)
        if event.type() == QEvent.Type.WindowStateChange:
            self.update_window_frame()

    def update_window_frame(self):
        if not getattr(self, "resize_grips", None) or not hasattr(self, "rounded_effect"):
            return
        width, height = self.width(), self.height()
        if self.isMaximized():
            self.shadow_layout.setContentsMargins(0, 0, 0, 0)
            self.rounded_effect.set_radius(0)
            self.shadow_root.update()
            for grip in self.resize_grips:
                grip.hide()
            return
        margin = ShadowBackdrop.margin
        self.shadow_layout.setContentsMargins(margin, margin, margin, margin)
        self.rounded_effect.set_radius(19)
        self.shadow_root.update()
        border = 8
        outer = margin - border // 2
        right = width - margin - border // 2
        bottom = height - margin - border // 2
        rectangles = (
            (margin + border, outer, width - 2 * (margin + border), border),
            (margin + border, bottom, width - 2 * (margin + border), border),
            (outer, margin + border, border, height - 2 * (margin + border)),
            (right, margin + border, border, height - 2 * (margin + border)),
            (outer, outer, border + 10, border + 10),
            (right - 10, outer, border + 10, border + 10),
            (outer, bottom - 10, border + 10, border + 10),
            (right - 10, bottom - 10, border + 10, border + 10),
        )
        for grip, rect in zip(self.resize_grips, rectangles):
            grip.setGeometry(*rect)
            grip.show()
            grip.raise_()

    def build_ui(self):
        root = ShadowBackdrop(objectName="windowOuter")
        self.shadow_root = root
        self.setCentralWidget(root)
        self.shadow_layout = QVBoxLayout(root)
        self.shadow_layout.setContentsMargins(root.margin, root.margin, root.margin, root.margin)
        self.shadow_layout.setSpacing(0)
        surface = QWidget(root, objectName="root")
        self.rounded_effect = RoundedClipEffect(surface)
        surface.setGraphicsEffect(self.rounded_effect)
        self.shadow_layout.addWidget(surface)
        frame = QVBoxLayout(surface)
        frame.setContentsMargins(0, 0, 0, 0)
        frame.setSpacing(0)
        frame.addWidget(WindowTitleBar(self))
        body_widget = QWidget(surface)
        frame.addWidget(body_widget, 1)
        shell = QHBoxLayout(body_widget)
        shell.setContentsMargins(0, 0, 0, 0)
        shell.setSpacing(0)

        edge = Qt.Edge
        cursor = Qt.CursorShape
        for edges, shape in (
            (edge.TopEdge, cursor.SizeVerCursor),
            (edge.BottomEdge, cursor.SizeVerCursor),
            (edge.LeftEdge, cursor.SizeHorCursor),
            (edge.RightEdge, cursor.SizeHorCursor),
            (edge.TopEdge | edge.LeftEdge, cursor.SizeFDiagCursor),
            (edge.TopEdge | edge.RightEdge, cursor.SizeBDiagCursor),
            (edge.BottomEdge | edge.LeftEdge, cursor.SizeBDiagCursor),
            (edge.BottomEdge | edge.RightEdge, cursor.SizeFDiagCursor),
        ):
            self.resize_grips.append(ResizeEdge(root, edges, shape))

        sidebar = QWidget(objectName="sidebar")
        sidebar.setFixedWidth(205)
        left = QVBoxLayout(sidebar)
        left.setContentsMargins(16, 30, 16, 20)
        left.setSpacing(10)
        brand = QHBoxLayout()
        logo = VectorIconLabel("wave", "#FFFFFF", "logo")
        logo.setAlignment(Qt.AlignmentFlag.AlignCenter)
        logo.setFixedSize(45, 45)
        brand.addWidget(logo)
        names = QVBoxLayout()
        names.setSpacing(1)
        names.addWidget(label("Qwen-voice", "sectionTitle"))
        names.addWidget(label("AI Voice Studio", "muted"))
        brand.addLayout(names)
        brand.addStretch()
        left.addLayout(brand)
        left.addSpacing(32)
        self.nav_group = QButtonGroup(self)
        self.nav_group.setExclusive(True)
        for index, name in enumerate(["语音合成", "音色库", "音色克隆", "项目记录", "设置"]):
            nav = VectorNavButton(name, index)
            nav.setCheckable(True)
            self.nav_group.addButton(nav, index)
            nav.clicked.connect(lambda checked=False, i=index: self.show_page(i))
            left.addWidget(nav)
        left.addStretch()
        left.addWidget(label("本地运行 · 语音创作", "muted"))
        shell.addWidget(sidebar)

        content = ContentBackdrop(objectName="content")
        body = QVBoxLayout(content)
        body.setContentsMargins(24, 24, 24, 20)
        body.setSpacing(16)
        header = QHBoxLayout()
        header_text = QVBoxLayout()
        title_row = QHBoxLayout()
        accent = VectorIconLabel("wave")
        accent.setFixedSize(28, 32)
        title_row.addWidget(accent)
        self.page_title = label("", "pageTitle")
        self.page_en = label("", "pageEnglish")
        title_row.addWidget(self.page_title)
        title_row.addWidget(self.page_en)
        title_row.addStretch()
        header_text.addLayout(title_row)
        self.page_subtitle = label("", "subtitle", True)
        header_text.addWidget(self.page_subtitle)
        header.addLayout(header_text, 1)
        self.format_badge = label("WAV   |   24 kHz   |   16 bit", "badge")
        header.addWidget(self.format_badge, 0, Qt.AlignmentFlag.AlignTop)
        body.addLayout(header)
        self.pages = QStackedWidget()
        body.addWidget(self.pages, 1)
        shell.addWidget(content, 1)
        self.pages.addWidget(self.scroll_page(self.make_synthesis()))
        self.pages.addWidget(self.scroll_page(self.make_library()))
        self.pages.addWidget(self.scroll_page(self.make_clone()))
        self.pages.addWidget(self.scroll_page(self.make_history()))
        self.pages.addWidget(self.scroll_page(self.make_settings()))

    def scroll_page(self, page):
        page.setObjectName("page")
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.setWidget(page)
        return scroll

    def show_page(self, index):
        titles = [
            ("语音合成", "Text to Speech", "输入文案、选择音色与模型，生成自然清晰的语音。"),
            ("音色库", "Voice Library", "浏览预设音色和您保存的参考音色。"),
            ("音色克隆", "Voice Cloning", "上传参考音频并填写对应文字，保存后用于生成语音。"),
            ("项目记录", "Projects", "查看、试听和复用已生成的语音。"),
            ("设置", "Settings", "配置音频导出格式与本地保存位置。"),
        ]
        title, en, subtitle = titles[index]
        self.page_title.setText(title)
        self.page_en.setText(en)
        self.page_subtitle.setText(subtitle)
        self.nav_group.button(index).setChecked(True)
        self.pages.setCurrentIndex(index)
        rate = int(self.settings.get("sample_rate", 24000)) // 1000
        self.format_badge.setText(f"WAV   |   {rate} kHz   |   16 bit")
        if index == 1:
            self.refresh_library()
        elif index == 3:
            self.refresh_history()

    def make_synthesis(self):
        page = QWidget()
        outer = QHBoxLayout(page)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.setSpacing(15)
        left = QVBoxLayout()
        left.setSpacing(15)
        text_card, text_layout = card("▤   输入文本")
        self.input_text = QPlainTextEdit()
        self.input_text.setPlaceholderText("在这里输入需要合成的文本内容…")
        self.input_text.setMinimumHeight(155)
        self.input_text.textChanged.connect(self.update_text_count)
        text_layout.addWidget(self.input_text)
        text_bottom = QHBoxLayout()
        self.text_count = label("0 / 5000", "muted")
        text_bottom.addWidget(self.text_count)
        text_bottom.addStretch()
        text_bottom.addWidget(button("粘贴", clicked=self.paste_text))
        text_bottom.addWidget(button("清空", clicked=self.input_text.clear))
        text_layout.addLayout(text_bottom)
        left.addWidget(text_card)

        config_card, config = card("☷   合成配置")
        config.addWidget(label("选择音色  Voice Preset", "sectionTitle"))
        preset_row = QHBoxLayout()
        self.quick_buttons = {}
        for name in ("Serena", "Vivian", "Uncle_Fu", "Dylan"):
            b = button(name, "chip")
            b.setCheckable(True)
            b.clicked.connect(lambda checked=False, n=name: self.select_preset(n))
            self.quick_buttons[name] = b
            preset_row.addWidget(b)
        preset_row.addWidget(button("更多 →", clicked=lambda: self.show_page(1)))
        config.addLayout(preset_row)
        config.addSpacing(5)
        config.addWidget(label("模型选择  Model", "sectionTitle"))
        model_row = QHBoxLayout()
        self.model_buttons = QButtonGroup(self)
        for i, model in enumerate(("0.6B", "1.7B")):
            b = button(model, "chip")
            b.setCheckable(True)
            self.model_buttons.addButton(b, i)
            model_row.addWidget(b)
            b.clicked.connect(self.sync_voice)
        self.model_buttons.button(0).setChecked(True)
        self.model_note = label("0.6B：资源占用较少    1.7B：支持风格描述", "muted", True)
        model_row.addWidget(self.model_note, 2)
        config.addLayout(model_row)
        speed_row = QHBoxLayout()
        speed_row.addWidget(label("语速  Speed", "sectionTitle"))
        self.speed = QSlider(Qt.Orientation.Horizontal)
        self.speed.setRange(70, 140)
        self.speed.setValue(100)
        self.speed.setSingleStep(5)
        self.speed.setPageStep(5)
        self.speed.valueChanged.connect(self.update_speed)
        speed_row.addWidget(self.speed, 1)
        self.speed_value = label("1.00×", "badge")
        speed_row.addWidget(self.speed_value)
        config.addLayout(speed_row)
        config.addWidget(label("情感与风格描述  Style", "sectionTitle"))
        self.style_input = QLineEdit()
        self.style_input.setPlaceholderText("例如：温柔、平静地讲述（仅 1.7B 预设音色）")
        config.addWidget(self.style_input)
        self.generate_button = button("✦    生成语音", "primary", self.generate)
        self.generate_button.setMinimumHeight(48)
        config.addWidget(self.generate_button)
        self.status = label("准备就绪", "muted", True)
        config.addWidget(self.status)
        self.retry_save_button = button("重试保存生成的音频", clicked=self.retry_save)
        self.retry_save_button.hide()
        config.addWidget(self.retry_save_button)
        left.addWidget(config_card)
        left.addStretch()
        outer.addLayout(left, 6)

        right = QVBoxLayout()
        right.setSpacing(15)
        current_card, current = card("♙   当前音色")
        self.current_voice = label("", "sectionTitle")
        current.addWidget(self.current_voice)
        self.current_voice_detail = label("", "muted", True)
        current.addWidget(self.current_voice_detail)
        current.addWidget(button("更换音色  →", clicked=lambda: self.show_page(1)), 0, Qt.AlignmentFlag.AlignRight)
        right.addWidget(current_card)
        player_card, player = card("♬   试听输出")
        playback = QHBoxLayout()
        self.play_button = button("▶", clicked=self.play_current)
        self.play_button.setFixedSize(46, 40)
        playback.addWidget(self.play_button)
        self.seek = QSlider(Qt.Orientation.Horizontal)
        self.seek.setRange(0, 0)
        self.seek.sliderMoved.connect(self.player.setPosition)
        playback.addWidget(self.seek, 1)
        self.player_time = label("00:00 / 00:00", "muted")
        playback.addWidget(self.player_time)
        player.addLayout(playback)
        self.output_detail = label("生成后可在此试听", "muted")
        player.addWidget(self.output_detail)
        actions = QHBoxLayout()
        self.export_button = button("↓   保存 WAV", "primary", self.export_current)
        self.export_button.setEnabled(False)
        actions.addWidget(self.export_button, 1)
        actions.addWidget(button("重新生成", clicked=self.generate))
        player.addLayout(actions)
        right.addWidget(player_card)
        recent_card, recent = card("◷   最近生成")
        self.recent_layout = QVBoxLayout()
        self.recent_layout.setSpacing(7)
        recent.addLayout(self.recent_layout)
        recent.addWidget(button("查看全部  →", clicked=lambda: self.show_page(3)), 0, Qt.AlignmentFlag.AlignRight)
        right.addWidget(recent_card)
        right.addStretch()
        outer.addLayout(right, 4)
        return page

    def make_library(self):
        page = QWidget()
        outer = QVBoxLayout(page)
        outer.setContentsMargins(0, 0, 0, 0)
        frame, layout = card()
        toolbar = QHBoxLayout()
        self.library_search = QLineEdit()
        self.library_search.setPlaceholderText("搜索音色名称或描述…")
        self.library_search.textChanged.connect(self.refresh_library)
        toolbar.addWidget(self.library_search, 1)
        self.voice_filter = QComboBox()
        self.voice_filter.addItems(["全部音色", "预设音色", "我的克隆"])
        self.voice_filter.currentIndexChanged.connect(self.refresh_library)
        toolbar.addWidget(self.voice_filter)
        toolbar.addWidget(button("＋  克隆声音", "primary", lambda: self.show_page(2)))
        layout.addLayout(toolbar)
        self.preview_status = label("预设试听使用 0.6B 模型，首次生成需要等待。", "muted", True)
        layout.addWidget(self.preview_status)
        self.library_items = QVBoxLayout()
        self.library_items.setSpacing(12)
        layout.addLayout(self.library_items)
        layout.addStretch()
        outer.addWidget(frame)
        return page

    def make_clone(self):
        page = QWidget()
        outer = QHBoxLayout(page)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.setSpacing(15)
        form, form_layout = card("☁   参考音频上传")
        drop = QFrame(objectName="drop")
        drop_layout = QVBoxLayout(drop)
        drop_layout.setContentsMargins(20, 22, 20, 22)
        self.reference_label = label("选择一段清晰的参考音频", "sectionTitle", True)
        self.reference_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        drop_layout.addWidget(self.reference_label)
        self.reference_note = label("请选择清晰的 WAV 本地音频", "muted", True)
        self.reference_note.setAlignment(Qt.AlignmentFlag.AlignCenter)
        drop_layout.addWidget(self.reference_note)
        choose = button("选择音频文件", clicked=self.choose_reference)
        drop_layout.addWidget(choose, 0, Qt.AlignmentFlag.AlignHCenter)
        form_layout.addWidget(drop)
        controls = QHBoxLayout()
        self.reference_play = button("▶   试听参考音频", clicked=self.play_reference)
        self.reference_play.setEnabled(False)
        controls.addWidget(self.reference_play)
        controls.addStretch()
        form_layout.addLayout(controls)
        form_layout.addWidget(label("参考音频的逐字稿", "sectionTitle"))
        self.reference_text = QPlainTextEdit()
        self.reference_text.setPlaceholderText("请输入参考音频中说出的完整文字…")
        self.reference_text.setMaximumHeight(110)
        form_layout.addWidget(self.reference_text)
        form_layout.addWidget(label("音色名称", "sectionTitle"))
        self.clone_name = QLineEdit()
        self.clone_name.setMaxLength(50)
        self.clone_name.setPlaceholderText("例如：我的声音、旁白音色")
        form_layout.addWidget(self.clone_name)
        form_layout.addWidget(button("✦   保存参考音色", "primary", self.save_clone))
        form_layout.addWidget(label("保存参考录音和文字后，可在语音合成中选择它。", "muted", True))
        form_layout.addStretch()
        outer.addWidget(form, 6)
        side = QVBoxLayout()
        side.setSpacing(15)
        guide, guide_layout = card("ⓘ   录音准备说明")
        for title, description in [
            ("使用清晰的参考音频", "尽量选择安静、无明显背景音乐的录音。"),
            ("准确填写对应文字", "文字应与参考录音中的内容一致。"),
            ("使用 0.6B Base 模型", "参考音色在生成语音时加载声音克隆模型。"),
        ]:
            guide_layout.addWidget(label(title, "sectionTitle"))
            guide_layout.addWidget(label(description, "muted", True))
        side.addWidget(guide)
        saved, saved_layout = card("◎   已保存的参考音色")
        self.saved_voices = QVBoxLayout()
        saved_layout.addLayout(self.saved_voices)
        side.addWidget(saved)
        side.addStretch()
        outer.addLayout(side, 4)
        return page

    def make_history(self):
        page = QWidget()
        outer = QHBoxLayout(page)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.setSpacing(15)
        records, records_layout = card("◷   历史记录")
        self.history_search = QLineEdit()
        self.history_search.setPlaceholderText("搜索文本内容或音色…")
        self.history_search.textChanged.connect(self.refresh_history)
        records_layout.addWidget(self.history_search)
        self.history_items = QVBoxLayout()
        self.history_items.setSpacing(7)
        records_layout.addLayout(self.history_items)
        records_layout.addStretch()
        outer.addWidget(records, 6)
        detail, d = card("▤   项目详情")
        self.detail_title = label("选择一条记录", "sectionTitle", True)
        self.detail_meta = label("生成后，记录将显示在这里。", "muted", True)
        self.detail_text = label("", wrap=True)
        self.detail_text.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        d.addWidget(self.detail_title)
        d.addWidget(self.detail_meta)
        d.addWidget(self.detail_text)
        self.detail_play = button("▶   试听音频", clicked=self.play_history)
        self.detail_reuse = button("复用文案与音色", clicked=self.reuse_history)
        self.detail_export = button("↓   导出 WAV", "primary", self.export_history)
        self.detail_delete = button("删除项目记录", clicked=self.delete_history)
        for b in (self.detail_play, self.detail_reuse, self.detail_export, self.detail_delete):
            b.setEnabled(False)
            d.addWidget(b)
        d.addStretch()
        outer.addWidget(detail, 4)
        return page

    def make_settings(self):
        page = QWidget()
        outer = QVBoxLayout(page)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.setSpacing(15)
        output, output_layout = card("♬   音频导出", "设置生成语音的默认格式。48 kHz 由模型的 24 kHz 输出转换。")
        row = QHBoxLayout()
        row.addWidget(label("音频格式", "sectionTitle"))
        row.addWidget(label("WAV · 16 bit", "badge"))
        row.addSpacing(15)
        row.addWidget(label("采样率", "sectionTitle"))
        self.rate = QComboBox()
        self.rate.addItem("24 kHz（原生）", 24000)
        self.rate.addItem("48 kHz（转换）", 48000)
        self.rate.setCurrentIndex(1 if int(self.settings.get("sample_rate", 24000)) == 48000 else 0)
        row.addWidget(self.rate)
        row.addStretch()
        output_layout.addLayout(row)
        outer.addWidget(output)

        paths = QHBoxLayout()
        output_path, op = card("▣   生成文件保存路径", "生成的 WAV 文件默认保存在这里。")
        self.output_path = QLineEdit(str(self.settings.get("output_dir", self.root / "outputs")))
        self.output_path.setReadOnly(True)
        self.output_path.setMinimumWidth(0)
        self.output_path.setCursorPosition(0)
        row = QHBoxLayout()
        row.addWidget(self.output_path, 1)
        row.addWidget(button("更改", clicked=self.choose_output_dir))
        op.addLayout(row)
        paths.addWidget(output_path, 1)
        models, ml = card("◇   模型路径", "本地已下载的模型存放位置。")
        model_path = QLineEdit(str(self.root / "work" / "modelscope-cache" / "models"))
        model_path.setReadOnly(True)
        model_path.setMinimumWidth(0)
        model_path.setCursorPosition(0)
        model_path.setToolTip(model_path.text())
        ml.addWidget(model_path)
        paths.addWidget(models, 1)
        outer.addLayout(paths)

        info, i = card("ⓘ   应用与运行环境")
        i.addWidget(label("Qwen-voice  ·  本地生成  ·  NVIDIA CUDA", "sectionTitle"))
        i.addWidget(label("模型按需加载；克隆音色使用 0.6B Base。", "muted", True))
        outer.addWidget(info)
        save_row = QHBoxLayout()
        save_row.addStretch()
        save_row.addWidget(button("保存设置", "primary", self.save_settings))
        outer.addLayout(save_row)
        outer.addStretch()
        return page

    def update_text_count(self):
        text = self.input_text.toPlainText()
        self.text_count.setText(f"{len(text)} / 5000")
        self.text_count.setStyleSheet("color: #B33B38;" if len(text) > 5000 else "")

    def paste_text(self):
        self.input_text.insertPlainText(QApplication.clipboard().text())

    def update_speed(self, value):
        self.speed_value.setText(f"{value / 100:.2f}×")

    def selected_model(self):
        return "1.7B" if self.model_buttons.checkedId() == 1 else "0.6B"

    def select_preset(self, name):
        self.voice = {"kind": "preset", "id": name, "name": name}
        self.sync_voice()

    def select_voice(self, voice):
        self.voice = dict(voice)
        if self.voice.get("kind") == "clone":
            self.model_buttons.button(0).setChecked(True)
        self.sync_voice()
        self.show_page(0)

    def sync_voice(self):
        cloned = self.voice.get("kind") == "clone"
        self.model_buttons.button(1).setEnabled(not cloned)
        if cloned:
            self.model_buttons.button(0).setChecked(True)
        name = self.voice.get("name", self.voice.get("id", ""))
        self.current_voice.setText(name)
        self.current_voice_detail.setText("已保存的参考音色 · 使用 0.6B Base 模型" if cloned else "Qwen 预设音色 · 使用 CustomVoice 模型")
        self.model_note.setText("克隆音色使用已下载的 0.6B Base" if cloned else "0.6B：资源占用较少    1.7B：支持风格描述")
        style_enabled = not cloned and self.selected_model() == "1.7B"
        self.style_input.setEnabled(style_enabled)
        self.style_input.setToolTip("仅 1.7B 预设音色支持情感与风格描述" if not style_enabled else "")
        for n, b in self.quick_buttons.items():
            b.setChecked(not cloned and self.voice.get("id") == n)

    def refresh_library(self, *args):
        if not hasattr(self, "library_items"):
            return
        clear_layout(self.library_items)
        term = self.library_search.text().strip().lower()
        mode = self.voice_filter.currentIndex()
        try:
            clones = self.storage.list_voices()
        except Exception as exc:
            self.library_items.addWidget(label(f"读取音色失败：{exc}", "muted", True))
            return
        if mode in (0, 1):
            self.library_items.addWidget(label("▧   预设音色", "cardTitle"))
            grid = QGridLayout()
            grid.setSpacing(10)
            matched = [(n, d, lang) for n, d, lang in PRESETS if term in f"{n} {d} {lang}".lower()]
            for index, (name, description, language) in enumerate(matched):
                frame = QFrame(objectName="inner")
                l = QVBoxLayout(frame)
                l.setContentsMargins(14, 12, 14, 12)
                l.addWidget(label(name, "sectionTitle"))
                l.addWidget(label(f"{description} · {language}", "muted", True))
                chosen = self.voice.get("kind") == "preset" and self.voice.get("id") == name
                controls = QHBoxLayout()
                controls.addStretch()
                controls.addWidget(button("▶ 试听", clicked=lambda checked=False, n=name: self.preview_preset(n)))
                b = button("✓ 已选择" if chosen else "选择音色", "primary" if chosen else None)
                b.clicked.connect(lambda checked=False, n=name: self.select_voice({"kind": "preset", "id": n, "name": n}))
                controls.addWidget(b)
                l.addLayout(controls)
                grid.addWidget(frame, index // 3, index % 3)
            if not matched:
                self.library_items.addWidget(label("没有匹配的预设音色。", "muted"))
            self.library_items.addLayout(grid)
        if mode in (0, 2):
            self.library_items.addSpacing(8)
            self.library_items.addWidget(label("♧   我的克隆", "cardTitle"))
            filtered = [v for v in clones if term in v.get("name", "").lower()]
            if not filtered:
                self.library_items.addWidget(label("还没有匹配的参考音色。可在“音色克隆”中添加。", "muted", True))
            for voice in filtered:
                row = QHBoxLayout()
                row.addWidget(label(voice.get("name", "参考音色"), "sectionTitle"), 1)
                row.addWidget(label("参考音色 · 0.6B Base", "muted"))
                row.addWidget(button("▶ 试听", clicked=lambda checked=False, v=voice: self.play_file(v.get("reference_path"))))
                row.addWidget(button("选择", clicked=lambda checked=False, v=voice: self.select_voice(v)))
                row.addWidget(button("删除", clicked=lambda checked=False, v=voice: self.delete_voice(v)))
                self.library_items.addLayout(row)
        self.refresh_saved_voices(clones)

    def refresh_saved_voices(self, voices):
        if not hasattr(self, "saved_voices"):
            return
        clear_layout(self.saved_voices)
        if not voices:
            self.saved_voices.addWidget(label("还没有保存参考音色。", "muted"))
        for voice in voices:
            row = QHBoxLayout()
            row.addWidget(label(voice.get("name", "参考音色"), "sectionTitle"), 1)
            row.addWidget(button("使用", clicked=lambda checked=False, v=voice: self.select_voice(v)))
            self.saved_voices.addLayout(row)

    def choose_reference(self):
        path, _ = QFileDialog.getOpenFileName(self, "选择参考音频", str(self.root), "WAV 音频 (*.wav)")
        if not path:
            return
        try:
            import soundfile as sf
            info = sf.info(path)
            if info.frames <= 0 or info.samplerate <= 0:
                raise ValueError("音频为空")
            self.voice_audio = path
            self.reference_label.setText(Path(path).name)
            self.reference_note.setText(f"{info.duration:.1f} 秒 · {info.samplerate // 1000} kHz · {info.channels} 声道")
            self.reference_play.setEnabled(True)
        except Exception as exc:
            QMessageBox.warning(self, "无法读取音频", f"请选择受支持的音频文件。\n\n{exc}")

    def save_clone(self):
        name = self.clone_name.text().strip()
        text = self.reference_text.toPlainText().strip()
        if not self.voice_audio or not name or not text:
            QMessageBox.information(self, "请补充信息", "请先选择参考音频，并填写对应文字和音色名称。")
            return
        try:
            voice = self.storage.import_voice(name, self.voice_audio, text, x_vector_only=False)
            self.voice_audio = None
            self.reference_label.setText("选择一段清晰的参考音频")
            self.reference_note.setText("请选择清晰的 WAV 本地音频")
            self.reference_play.setEnabled(False)
            self.clone_name.clear()
            self.reference_text.clear()
            self.voice = dict(voice)
            self.model_buttons.button(0).setChecked(True)
            self.sync_voice()
            self.refresh_library()
            QMessageBox.information(self, "已保存", "参考音色已保存并选中。请在语音合成页输入文字并生成。")
            self.show_page(0)
        except Exception as exc:
            QMessageBox.warning(self, "保存失败", str(exc))

    def delete_voice(self, voice):
        if self.thread is not None:
            QMessageBox.information(self, "正在生成", "请等待当前任务完成后再删除音色。")
            return
        answer = QMessageBox.question(self, "删除参考音色", "删除该音色及应用保存的参考音频副本？原始录音不会被删除。")
        if answer != QMessageBox.StandardButton.Yes:
            return
        try:
            source = self.player.source().toLocalFile()
            reference = voice.get("reference_path")
            if source and reference and Path(source).resolve() == Path(reference).resolve():
                self.player.stop()
                self.player.setSource(QUrl())
            self.storage.delete_voice(voice["id"])
            if self.voice.get("kind") == "clone" and self.voice.get("id") == voice["id"]:
                self.select_preset("Serena")
            self.refresh_library()
        except Exception as exc:
            QMessageBox.warning(self, "删除失败", str(exc))

    def play_file(self, path):
        if not path or not Path(path).is_file():
            QMessageBox.warning(self, "无法播放", "音频文件不存在或无法访问。")
            return
        self.player.stop()
        self.player.setSource(QUrl.fromLocalFile(str(Path(path).resolve())))
        self.player.play()

    def play_current(self):
        if not self.current_audio:
            QMessageBox.information(self, "暂无音频", "请先生成语音，或从项目记录选择一段音频。")
            return
        source = self.player.source().toLocalFile()
        same = bool(source) and Path(source).resolve() == Path(self.current_audio).resolve()
        if same and self.player.playbackState() == QMediaPlayer.PlaybackState.PlayingState:
            self.player.pause()
        elif same and self.player.playbackState() == QMediaPlayer.PlaybackState.PausedState:
            self.player.play()
        else:
            self.play_file(self.current_audio)

    def update_play_button(self, state):
        if not hasattr(self, "play_button"):
            return
        source = self.player.source().toLocalFile()
        same = bool(source and self.current_audio) and Path(source).resolve() == Path(self.current_audio).resolve()
        self.play_button.setText("Ⅱ" if same and state == QMediaPlayer.PlaybackState.PlayingState else "▶")

    def play_reference(self):
        self.play_file(self.voice_audio)

    def update_player_time(self, position):
        self.seek.setValue(position)
        self.player_time.setText(f"{format_time(position)} / {format_time(self.player.duration())}")

    def update_player_duration(self, duration):
        self.seek.setRange(0, duration)
        self.update_player_time(self.player.position())

    def player_error(self, error, message):
        if error != QMediaPlayer.Error.NoError:
            QMessageBox.warning(self, "播放失败", message or "请检查音频文件与播放设备。")

    def generate(self):
        if self.thread is not None:
            QMessageBox.information(self, "正在处理", "请等待当前任务完成。")
            return
        if self.unsaved_result is not None:
            QMessageBox.information(self, "请先保存", "上次生成的音频尚未保存。请先在合成页重试保存。")
            return
        text = self.input_text.toPlainText().strip()
        if not text:
            QMessageBox.information(self, "请输入文本", "请输入需要合成的内容。")
            return
        if len(self.input_text.toPlainText()) > 5000:
            QMessageBox.warning(self, "文本过长", "文本超过 5000 字，请缩短后再生成；原文已保留。")
            return
        model = self.selected_model()
        speed = self.speed.value() / 100.0
        style = self.style_input.text().strip() if self.style_input.isEnabled() else ""
        rate = int(self.settings.get("sample_rate", 24000))
        voice = dict(self.voice)
        self.pending = {"text": text, "voice_id": voice["id"], "voice_name": voice["name"],
                        "voice_kind": voice["kind"], "model_id": model, "speed": speed,
                        "style": style, "title": text[:24]}
        args = {"text": text, "voice": voice, "model_id": model, "speed": speed,
                "style": style, "sample_rate": rate}
        self.start_worker(args, "generation")

    def start_worker(self, args, mode):
        self.job_mode = mode
        self.thread = QThread(self)
        self.worker = GenerateWorker(self.engine, args)
        self.worker.moveToThread(self.thread)
        self.thread.started.connect(self.worker.run)
        self.worker.progress.connect(self.show_progress)
        self.worker.result.connect(self.generation_done)
        self.worker.error.connect(self.generation_error)
        self.worker.finished.connect(self.thread.quit)
        self.worker.finished.connect(self.worker.deleteLater)
        self.thread.finished.connect(self.thread.deleteLater)
        self.thread.finished.connect(self.generation_finished)
        self.generate_button.setEnabled(False)
        self.status.setText("正在准备模型…")
        self.thread.start()

    def show_progress(self, message):
        self.status.setText(message)
        if self.job_mode == "preview":
            self.preview_status.setText(message)

    def preview_preset(self, name):
        if self.thread is not None:
            QMessageBox.information(self, "正在处理", "请等待当前任务完成后再试听。")
            return
        if self.unsaved_result is not None:
            QMessageBox.information(self, "请先保存", "上次生成的音频尚未保存。请先在合成页重试保存。")
            return
        path = self.root / "work" / "previews" / f"preset-{name}.wav"
        if path.is_file():
            self.preview_status.setText(f"正在播放 {name} 的 0.6B 试听。")
            self.play_file(path)
            return
        texts = {
            "Ryan": "Hello, it is nice to meet you.",
            "Aiden": "Hello, it is nice to meet you.",
            "Ono_Anna": "こんにちは。お会いできてうれしいです。",
            "Sohee": "안녕하세요. 만나서 반갑습니다.",
        }
        args = {"text": texts.get(name, "你好，很高兴为你朗读这段文字。"),
                "voice": {"kind": "preset", "id": name, "name": name},
                "model_id": "0.6B", "speed": 1.0, "style": "", "sample_rate": 24000}
        self.preview_name = name
        self.preview_status.setText(f"正在准备 {name} 的试听，首次生成需要等待…")
        self.start_worker(args, "preview")

    @Slot(object)
    def generation_done(self, result):
        if self.job_mode == "preview":
            try:
                import soundfile as sf
                target = self.root / "work" / "previews" / f"preset-{self.preview_name}.wav"
                target.parent.mkdir(parents=True, exist_ok=True)
                temporary = target.with_name(f".{target.stem}-{uuid.uuid4().hex}.tmp.wav")
                try:
                    sf.write(str(temporary), result["audio"], result["sample_rate"], subtype="PCM_16")
                    os.replace(temporary, target)
                finally:
                    temporary.unlink(missing_ok=True)
                self.preview_status.setText(f"正在播放 {self.preview_name} 的 0.6B 试听。")
                self.status.setText("试听生成完成。")
                self.play_file(target)
            except Exception as exc:
                self.preview_status.setText("试听生成完成，但保存或播放失败。")
                QMessageBox.warning(self, "试听失败", str(exc))
            return
        self.unsaved_result = result
        self.unsaved_metadata = dict(self.pending)
        self.retry_save()

    def retry_save(self):
        if self.unsaved_result is None:
            return
        try:
            result = self.unsaved_result
            record = self.storage.save_generation(result["audio"], result["sample_rate"], self.unsaved_metadata)
            self.current_audio = record["audio_path"]
            self.update_play_button(self.player.playbackState())
            self.output_detail.setText(f"WAV · {result['sample_rate'] // 1000} kHz · 16 bit · {result['duration']:.1f} 秒")
            self.export_button.setEnabled(True)
            self.status.setText("生成完成，已保存到本地。")
            self.unsaved_result = None
            self.unsaved_metadata = None
            self.retry_save_button.hide()
            self.refresh_history()
        except Exception as exc:
            self.status.setText("音频已生成但尚未保存；请检查设置后重试保存。")
            self.retry_save_button.show()
            QMessageBox.warning(self, "保存失败", f"{exc}\n\n生成的音频暂保留在应用中，请检查输出路径后点击“重试保存”。")

    @Slot(str)
    def generation_error(self, message):
        if self.job_mode == "preview":
            self.preview_status.setText("试听生成失败，请检查模型文件与显存。")
        self.status.setText("生成失败，请检查模型文件与显存。")
        QMessageBox.warning(self, "生成失败", message)

    @Slot()
    def generation_finished(self):
        self.thread = None
        self.worker = None
        self.pending = None
        self.job_mode = None
        self.preview_name = None
        self.generate_button.setEnabled(True)

    def refresh_history(self, *args):
        if not hasattr(self, "history_items"):
            return
        clear_layout(self.history_items)
        clear_layout(self.recent_layout)
        try:
            records = self.storage.list_history()
        except Exception as exc:
            self.history_items.addWidget(label(f"读取记录失败：{exc}", "muted", True))
            return
        term = self.history_search.text().strip().lower()
        filtered = [r for r in records if term in f"{r.get('title','')} {r.get('text','')} {r.get('voice_name','')}".lower()]
        if not filtered:
            self.history_items.addWidget(label("暂无匹配的生成记录。", "muted"))
        if not records:
            self.recent_layout.addWidget(label("还没有生成记录。", "muted"))
        for record in filtered:
            frame = QFrame(objectName="inner")
            row = QHBoxLayout(frame)
            row.setContentsMargins(12, 9, 12, 9)
            desc = QVBoxLayout()
            desc.addWidget(label(record.get("title") or record.get("text", "")[:24], "sectionTitle", True))
            desc.addWidget(label(f"{record.get('voice_name', '')} · {record.get('model_id', '')} · {record.get('created_at', '')}", "muted", True))
            row.addLayout(desc, 1)
            row.addWidget(button("▶", clicked=lambda checked=False, r=record: self.play_file(r.get("audio_path"))))
            row.addWidget(button("详情", clicked=lambda checked=False, r=record: self.select_history(r)))
            self.history_items.addWidget(frame)
        for record in records[:3]:
            row = QHBoxLayout()
            row.addWidget(button("▶", clicked=lambda checked=False, r=record: self.play_file(r.get("audio_path"))))
            row.addWidget(label(record.get("title") or record.get("text", "")[:20], "sectionTitle", True), 1)
            self.recent_layout.addLayout(row)
        if self.history_selected:
            matched = next((r for r in records if r.get("id") == self.history_selected.get("id")), None)
            if matched:
                self.select_history(matched)
            else:
                self.clear_history_selection()

    def select_history(self, record):
        self.history_selected = record
        self.detail_title.setText(record.get("title") or record.get("text", "")[:24])
        self.detail_meta.setText(f"{record.get('voice_name', '')} · {record.get('model_id', '')}\n{record.get('created_at', '')}\nWAV · {int(record.get('sample_rate', 24000)) // 1000} kHz · {float(record.get('duration', 0)):.1f} 秒")
        self.detail_text.setText(record.get("text", ""))
        for b in (self.detail_play, self.detail_reuse, self.detail_export, self.detail_delete):
            b.setEnabled(True)

    def clear_history_selection(self):
        self.history_selected = None
        self.detail_title.setText("选择一条记录")
        self.detail_meta.setText("生成后，记录将显示在这里。")
        self.detail_text.clear()
        for b in (self.detail_play, self.detail_reuse, self.detail_export, self.detail_delete):
            b.setEnabled(False)

    def play_history(self):
        if self.history_selected:
            self.play_file(self.history_selected.get("audio_path"))

    def reuse_history(self):
        record = self.history_selected
        if not record:
            return
        if record.get("voice_kind") == "clone":
            voice = next((v for v in self.storage.list_voices() if v.get("id") == record.get("voice_id")), None)
            if not voice:
                QMessageBox.warning(self, "音色不存在", "该参考音色已删除，无法复用。")
                return
        else:
            voice = {"kind": "preset", "id": record.get("voice_id"), "name": record.get("voice_name")}
        self.input_text.setPlainText(record.get("text", ""))
        model = record.get("model_id", "0.6B")
        self.model_buttons.button(1 if model == "1.7B" and voice.get("kind") != "clone" else 0).setChecked(True)
        self.speed.setValue(round(float(record.get("speed", 1)) * 100))
        self.style_input.setText(record.get("style", ""))
        self.select_voice(voice)

    def export_history(self):
        if self.history_selected:
            self.export_file(self.history_selected.get("audio_path"))

    def export_current(self):
        self.export_file(self.current_audio)

    def export_file(self, source):
        if not source or not Path(source).is_file():
            QMessageBox.warning(self, "导出失败", "音频文件不存在。")
            return
        dest, _ = QFileDialog.getSaveFileName(self, "保存 WAV", str(self.root / "outputs" / Path(source).name), "WAV 音频 (*.wav)")
        if not dest:
            return
        try:
            if Path(dest).resolve() != Path(source).resolve():
                shutil.copy2(source, dest)
            QMessageBox.information(self, "保存完成", f"已保存到：\n{dest}")
        except Exception as exc:
            QMessageBox.warning(self, "导出失败", str(exc))

    def delete_history(self):
        record = self.history_selected
        if not record:
            return
        answer = QMessageBox.question(self, "删除项目记录", "删除这条项目记录？已生成的 WAV 文件会保留在输出目录。")
        if answer != QMessageBox.StandardButton.Yes:
            return
        try:
            self.storage.delete_history(record["id"])
            self.clear_history_selection()
            self.refresh_history()
        except Exception as exc:
            QMessageBox.warning(self, "删除失败", str(exc))

    def choose_output_dir(self):
        directory = QFileDialog.getExistingDirectory(self, "选择生成文件保存位置", self.output_path.text())
        if directory:
            if Path(directory).drive.lower() != self.root.drive.lower():
                QMessageBox.warning(self, "请选择同一磁盘", "为保持文件集中保存，请选择应用数据所在磁盘上的文件夹。")
                return
            self.output_path.setText(directory)

    def save_settings(self):
        if self.thread is not None:
            QMessageBox.information(self, "正在生成", "请等待当前任务完成后再更改输出设置。")
            return
        try:
            settings = dict(self.settings)
            settings["sample_rate"] = int(self.rate.currentData())
            settings["output_dir"] = self.output_path.text().strip()
            self.storage.save_settings(settings)
            self.settings = self.storage.load_settings()
            self.show_page(4)
            QMessageBox.information(self, "设置已保存", "新的导出设置将在下一次生成时生效。")
        except Exception as exc:
            QMessageBox.warning(self, "保存失败", str(exc))

    def closeEvent(self, event):
        if self.thread is not None:
            QMessageBox.information(self, "正在生成", "请等待当前语音生成完成后再关闭应用。")
            event.ignore()
            return
        if self.unsaved_result is not None:
            answer = QMessageBox.question(self, "仍有未保存音频", "上次生成的音频尚未保存。关闭后音频将丢失，仍要关闭吗？")
            if answer != QMessageBox.StandardButton.Yes:
                event.ignore()
                return
        self.player.stop()
        event.accept()


def main():
    app = QApplication(sys.argv)
    app.setFont(QFont("Microsoft YaHei UI", 10))
    lock_dir = ROOT / "work" / "temp"
    try:
        lock_dir.mkdir(parents=True, exist_ok=True)
    except OSError as exc:
        QMessageBox.critical(None, "无法启动", f"无法创建应用目录：{exc}")
        return 1
    lock = QLockFile(str(lock_dir / "qwen-voice.lock"))
    lock.setStaleLockTime(0)
    if not lock.tryLock(0):
        if lock.error() == QLockFile.LockError.LockFailedError:
            QMessageBox.information(None, "应用已运行", "Qwen-voice 已经打开，请使用现有窗口。")
        else:
            QMessageBox.critical(None, "无法启动", "无法创建应用锁文件，请检查应用目录权限和可用空间。")
        return 1
    try:
        window = MainWindow()
        window.show()
        return app.exec()
    finally:
        lock.unlock()


if __name__ == "__main__":
    sys.exit(main())
