#![allow(non_snake_case)]
#![cfg_attr(all(windows, not(test)), windows_subsystem = "windows")]
mod schedule;
#[cfg(test)]
mod tests;

use schedule::state_at;

use windows_sys::Win32::Foundation::*;
use windows_sys::Win32::Graphics::Gdi::*;
use windows_sys::Win32::Graphics::GdiPlus::*;
use windows_sys::Win32::System::LibraryLoader::GetModuleHandleW;
use windows_sys::Win32::System::Registry::{RegGetValueW, RRF_RT_REG_DWORD, HKEY_CURRENT_USER};
use windows_sys::Win32::System::SystemInformation::GetLocalTime;
use windows_sys::Win32::UI::Controls::*;
use windows_sys::Win32::UI::WindowsAndMessaging::*;

static mut HINST: HINSTANCE = std::ptr::null_mut();
static mut TOOLTIP_HWND: HWND = std::ptr::null_mut();
static mut TRACKED: bool = false;
static mut TOOL: TTTOOLINFOW = TTTOOLINFOW {
    cbSize: 0,
    uFlags: 0,
    hwnd: std::ptr::null_mut(),
    uId: 0,
    rect: RECT { left: 0, top: 0, right: 0, bottom: 0 },
    hinst: std::ptr::null_mut(),
    lpszText: std::ptr::null_mut(),
    lParam: 0,
    lpReserved: std::ptr::null_mut(),
};
static mut TIP_TEXT: [u16; 1024] = [0; 1024];

#[repr(C)]
struct TRACKMOUSEEVENT {
    cbSize: u32,
    dwFlags: u32,
    hwndTrack: HWND,
    dwHoverTime: u32,
}

const TME_HOVER: u32 = 0x0000_0001;
const TME_LEAVE: u32 = 0x0000_0002;

extern "system" {
    fn TrackMouseEvent(lpEventTrack: *mut TRACKMOUSEEVENT) -> BOOL;
}

#[repr(C)]
struct INITCOMMONCONTROLSEX {
    dwSize: u32,
    dwICC: u32,
}

const ICC_WIN95_CLASSES: u32 = 0x0000_00FF;

#[link(name = "comctl32")]
extern "system" {
    fn InitCommonControlsEx(picce: *mut INITCOMMONCONTROLSEX) -> BOOL;
}

const PADDING_X: i32 = 10;
const FONT_HEIGHT: i32 = -15;

fn wide(s: &str) -> Vec<u16> {
    s.encode_utf16().chain(std::iter::once(0)).collect()
}

fn log(line: &str) {
    use std::io::Write;
    if let std::result::Result::Ok(mut f) = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(std::env::temp_dir().join("train-win-widget.log"))
    {
        let _ = writeln!(f, "{}", line);
    }
}

fn guarded_update(hwnd: HWND) {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        update(hwnd);
    }));
    if result.is_err() {
        log("panic in update");
    }
}

unsafe fn system_light_theme() -> bool {
    let mut value: u32 = 0;
    let mut size: u32 = 4;
    let status = RegGetValueW(
        HKEY_CURRENT_USER,
        wide("Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize").as_ptr(),
        wide("SystemUsesLightTheme").as_ptr(),
        RRF_RT_REG_DWORD,
        std::ptr::null_mut(),
        &mut value as *mut u32 as *mut std::ffi::c_void,
        &mut size,
    );
    status == 0 && value == 1
}

unsafe fn text_color(urgency: u8) -> u32 {
    let light = system_light_theme();
    match urgency {
        2 => 0xFFF87171,
        1 => 0xFFFBBF24,
        _ if light => 0xFF1A1D21,
        _ => 0xFFECEFF3,
    }
}

unsafe fn window_rect_of(class: &str) -> Option<RECT> {
    let hwnd = FindWindowW(wide(class).as_ptr(), std::ptr::null());
    if hwnd.is_null() {
        return None;
    }
    let mut rect = RECT { left: 0, top: 0, right: 0, bottom: 0 };
    if GetWindowRect(hwnd, &mut rect) == 0 {
        return None;
    }
    Some(rect)
}

struct PaintContext {
    hdc: HDC,
    hbmp: HBITMAP,
    hfont: HFONT,
    graphics: *mut GpGraphics,
    font: *mut GpFont,
    brush: *mut GpSolidFill,
    old_bmp: HGDIOBJ,
}

unsafe fn open_paint(hwnd: HWND, w: i32, h: i32, color: u32) -> PaintContext {
    let hdc_win = GetDC(hwnd);
    let hdc = CreateCompatibleDC(hdc_win);
    let bmi = BITMAPINFO {
        bmiHeader: BITMAPINFOHEADER {
            biSize: std::mem::size_of::<BITMAPINFOHEADER>() as u32,
            biWidth: w,
            biHeight: -h,
            biPlanes: 1,
            biBitCount: 32,
            biCompression: BI_RGB,
            biSizeImage: 0,
            biXPelsPerMeter: 0,
            biYPelsPerMeter: 0,
            biClrUsed: 0,
            biClrImportant: 0,
        },
        bmiColors: [RGBQUAD {
            rgbBlue: 0,
            rgbGreen: 0,
            rgbRed: 0,
            rgbReserved: 0,
        }],
    };
    let mut bits: *mut std::ffi::c_void = std::ptr::null_mut();
    let hbmp = CreateDIBSection(
        hdc_win,
        &bmi,
        DIB_RGB_COLORS,
        &mut bits,
        std::ptr::null_mut(),
        0,
    );
    let old_bmp = SelectObject(hdc, hbmp as HGDIOBJ);

    let mut graphics: *mut GpGraphics = std::ptr::null_mut();
    GdipCreateFromHDC(hdc, &mut graphics);
    GdipSetTextRenderingHint(graphics, 4);

    let hfont = CreateFontW(
        FONT_HEIGHT,
        0,
        0,
        0,
        600,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        wide("Segoe UI").as_ptr(),
    );
    SelectObject(hdc, hfont as HGDIOBJ);
    let mut font: *mut GpFont = std::ptr::null_mut();
    GdipCreateFontFromDC(hdc, &mut font);

    let mut brush: *mut GpSolidFill = std::ptr::null_mut();
    GdipCreateSolidFill(color, &mut brush);

    ReleaseDC(hwnd, hdc_win);
    PaintContext {
        hdc,
        hbmp,
        hfont,
        graphics,
        font,
        brush,
        old_bmp,
    }
}

unsafe fn close_paint(pc: PaintContext) {
    GdipDeleteFont(pc.font);
    GdipDeleteBrush(pc.brush as *mut GpBrush);
    GdipDeleteGraphics(pc.graphics);
    SelectObject(pc.hdc, pc.old_bmp);
    DeleteObject(pc.hfont as HGDIOBJ);
    DeleteObject(pc.hbmp as HGDIOBJ);
    DeleteDC(pc.hdc);
}

unsafe fn measure_text(hwnd: HWND, text: &str) -> (i32, i32) {
    let pc = open_paint(hwnd, 8, 8, 0xFF000000);
    let wtext = wide(text);
    let layout = RectF { X: 0.0, Y: 0.0, Width: 10000.0, Height: 1000.0 };
    let mut bounds = RectF { X: 0.0, Y: 0.0, Width: 0.0, Height: 0.0 };
    GdipMeasureString(
        pc.graphics,
        wtext.as_ptr(),
        -1,
        pc.font,
        &layout,
        std::ptr::null_mut(),
        &mut bounds,
        std::ptr::null_mut(),
        std::ptr::null_mut(),
    );
    close_paint(pc);
    (bounds.Width.ceil() as i32, bounds.Height.ceil() as i32)
}

unsafe fn render(hwnd: HWND, text: &str, color: u32, w: i32, h: i32) {
    let pc = open_paint(hwnd, w, h, color);
    let wtext = wide(text);
    let layout = RectF {
        X: 0.0,
        Y: 0.0,
        Width: w as f32,
        Height: h as f32,
    };
    GdipDrawString(
        pc.graphics,
        wtext.as_ptr(),
        -1,
        pc.font,
        &layout,
        std::ptr::null_mut(),
        pc.brush as *mut GpBrush,
    );

    let size = SIZE { cx: w, cy: h };
    let pt_src = POINT { x: 0, y: 0 };
    let blend = BLENDFUNCTION {
        BlendOp: AC_SRC_OVER as u8,
        BlendFlags: 0,
        SourceConstantAlpha: 255,
        AlphaFormat: AC_SRC_ALPHA as u8,
    };
    let hdc_win = GetDC(hwnd);
    UpdateLayeredWindow(
        hwnd,
        hdc_win,
        std::ptr::null_mut(),
        &size,
        pc.hdc,
        &pt_src,
        0,
        &blend,
        ULW_ALPHA,
    );
    ReleaseDC(hwnd, hdc_win);
    close_paint(pc);
}

unsafe fn set_tip_text(text: &str) {
    let mut i = 0;
    for (dest, src) in TIP_TEXT.iter_mut().zip(text.encode_utf16()) {
        *dest = src;
        i += 1;
        if i >= TIP_TEXT.len() - 1 {
            break;
        }
    }
    TIP_TEXT[i.min(TIP_TEXT.len() - 1)] = 0;
    TOOL.lpszText = TIP_TEXT.as_mut_ptr();
}

unsafe fn update(hwnd: HWND) {
    let mut st = SYSTEMTIME {
        wYear: 0,
        wMonth: 0,
        wDayOfWeek: 0,
        wDay: 0,
        wHour: 0,
        wMinute: 0,
        wSecond: 0,
        wMilliseconds: 0,
    };
    GetLocalTime(&mut st);
    let now = st.wHour as u32 * 3600 + st.wMinute as u32 * 60 + st.wSecond as u32;
    let state = state_at(now, st.wDayOfWeek);

    let line = state.line();
    set_tip_text(&state.tooltip());
    if !TOOLTIP_HWND.is_null() {
        SendMessageW(
            TOOLTIP_HWND,
            TTM_UPDATETIPTEXTW,
            0,
            &mut TOOL as *mut TTTOOLINFOW as LPARAM,
        );
    }

    let (text_w, text_h) = measure_text(hwnd, &line);
    let w = text_w + PADDING_X * 2;
    let h = (text_h + 6).max(20);

    let tb = window_rect_of("Shell_TrayWnd");
    let (x, y) = match tb {
        Some(tb) => {
            let tb_h = tb.bottom - tb.top;
            let y = tb.top + (tb_h - h) / 2;
            let x = match window_rect_of("TrayNotifyWnd") {
                Some(tray) => tray.left - w - 14,
                None => tb.right - w - 320,
            };
            (x, y)
        }
        None => (40, 40),
    };

    SetWindowPos(
        hwnd,
        HWND_TOPMOST,
        x,
        y,
        w,
        h,
        SWP_NOACTIVATE,
    );
    render(hwnd, &line, text_color(state.urgency()), w, h);
}

unsafe fn show_tooltip(hwnd: HWND) {
    if TOOLTIP_HWND.is_null() {
        return;
    }
    let mut rect = RECT { left: 0, top: 0, right: 0, bottom: 0 };
    GetWindowRect(hwnd, &mut rect);
    let lparam = ((rect.top as isize - 90) << 16) | (rect.left as isize & 0xffff);
    SendMessageW(TOOLTIP_HWND, TTM_TRACKPOSITION, 0, lparam);
    SendMessageW(
        TOOLTIP_HWND,
        TTM_TRACKACTIVATE,
        1,
        &mut TOOL as *mut TTTOOLINFOW as LPARAM,
    );
}

unsafe fn hide_tooltip() {
    if TOOLTIP_HWND.is_null() {
        return;
    }
    SendMessageW(
        TOOLTIP_HWND,
        TTM_TRACKACTIVATE,
        0,
        &mut TOOL as *mut TTTOOLINFOW as LPARAM,
    );
}

unsafe fn show_menu(hwnd: HWND) {
    let menu = CreatePopupMenu();
    if menu.is_null() {
        return;
    }
    AppendMenuW(menu, MF_STRING, 1, wide("Exit").as_ptr());
    let mut pt = POINT { x: 0, y: 0 };
    GetCursorPos(&mut pt);
    SetForegroundWindow(hwnd);
    TrackPopupMenu(
        menu,
        TPM_RIGHTBUTTON,
        pt.x,
        pt.y,
        0,
        hwnd,
        std::ptr::null(),
    );
    PostMessageW(hwnd, WM_NULL, 0, 0);
    DestroyMenu(menu);
}

unsafe extern "system" fn wnd_proc(hwnd: HWND, msg: u32, wp: WPARAM, lp: LPARAM) -> LRESULT {
    match msg {
        WM_TIMER => {
            if wp == 1 {
                guarded_update(hwnd);
            } else {
                SetWindowPos(
                    hwnd,
                    HWND_TOPMOST,
                    0,
                    0,
                    0,
                    0,
                    SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE,
                );
            }
            0
        }
        WM_MOUSEMOVE => {
            if !TRACKED {
                let mut tme = TRACKMOUSEEVENT {
                    cbSize: std::mem::size_of::<TRACKMOUSEEVENT>() as u32,
                    dwFlags: TME_HOVER | TME_LEAVE,
                    hwndTrack: hwnd,
                    dwHoverTime: 300,
                };
                TrackMouseEvent(&mut tme);
                TRACKED = true;
            }
            0
        }
        WM_MOUSEHOVER => {
            show_tooltip(hwnd);
            0
        }
        WM_MOUSELEAVE => {
            hide_tooltip();
            TRACKED = false;
            0
        }
        WM_RBUTTONUP => {
            show_menu(hwnd);
            0
        }
        WM_COMMAND => {
            if (wp & 0xffff) == 1 {
                DestroyWindow(hwnd);
            }
            0
        }
        WM_DESTROY => {
            PostQuitMessage(0);
            0
        }
        _ => DefWindowProcW(hwnd, msg, wp, lp),
    }
}

unsafe fn create_tooltip(hwnd: HWND) {
    let mut icc = INITCOMMONCONTROLSEX {
        dwSize: std::mem::size_of::<INITCOMMONCONTROLSEX>() as u32,
        dwICC: ICC_WIN95_CLASSES,
    };
    InitCommonControlsEx(&mut icc);

    TOOLTIP_HWND = CreateWindowExW(
        0,
        TOOLTIPS_CLASSW,
        std::ptr::null(),
        WS_POPUP | TTS_ALWAYSTIP | TTS_NOPREFIX,
        CW_USEDEFAULT,
        CW_USEDEFAULT,
        CW_USEDEFAULT,
        CW_USEDEFAULT,
        hwnd,
        std::ptr::null_mut(),
        HINST,
        std::ptr::null(),
    );
    if TOOLTIP_HWND.is_null() {
        return;
    }
    SetWindowPos(
        TOOLTIP_HWND,
        HWND_TOPMOST,
        0,
        0,
        0,
        0,
        SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE,
    );
    TOOL.cbSize = std::mem::size_of::<TTTOOLINFOW>() as u32;
    TOOL.uFlags = TTF_TRACK | TTF_ABSOLUTE;
    TOOL.hwnd = hwnd;
    TOOL.uId = 1;
    SendMessageW(
        TOOLTIP_HWND,
        TTM_ADDTOOLW,
        0,
        &mut TOOL as *mut TTTOOLINFOW as LPARAM,
    );
}

fn main() {
    unsafe {
        HINST = GetModuleHandleW(std::ptr::null());
        let mut token: usize = 0;
        let input = GdiplusStartupInput {
            GdiplusVersion: 1,
            DebugEventCallback: 0,
            SuppressBackgroundThread: 0,
            SuppressExternalCodecs: 0,
        };
        GdiplusStartup(&mut token, &input, std::ptr::null_mut());

        let class_name = wide("TrainWinWidget");
        let wc = WNDCLASSW {
            style: 0,
            lpfnWndProc: Some(wnd_proc),
            cbClsExtra: 0,
            cbWndExtra: 0,
            hInstance: HINST,
            hIcon: std::ptr::null_mut(),
            hCursor: std::ptr::null_mut(),
            hbrBackground: std::ptr::null_mut(),
            lpszMenuName: std::ptr::null(),
            lpszClassName: class_name.as_ptr(),
        };
        RegisterClassW(&wc);

        let hwnd = CreateWindowExW(
            WS_EX_LAYERED | WS_EX_TOOLWINDOW | WS_EX_TOPMOST | WS_EX_NOACTIVATE,
            class_name.as_ptr(),
            std::ptr::null(),
            WS_POPUP,
            0,
            0,
            200,
            30,
            std::ptr::null_mut(),
            std::ptr::null_mut(),
            HINST,
            std::ptr::null(),
        );

        create_tooltip(hwnd);
        std::panic::set_hook(Box::new(|info| log(&format!("panic: {}", info))));
        log("starting");
        guarded_update(hwnd);
        ShowWindow(hwnd, SW_SHOWNOACTIVATE);
        SetTimer(hwnd, 1, 60_000, None);
        SetTimer(hwnd, 2, 500, None);

        let mut msg = MSG {
            hwnd: std::ptr::null_mut(),
            message: 0,
            wParam: 0,
            lParam: 0,
            time: 0,
            pt: POINT { x: 0, y: 0 },
        };
        while GetMessageW(&mut msg, std::ptr::null_mut(), 0, 0) > 0 {
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
        GdiplusShutdown(token);
    }
}
