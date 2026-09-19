package com.jelly.farmhelperv3.util.helper;

import org.lwjgl.glfw.GLFW;
import com.jelly.farmhelperv3.util.Input;

import java.awt.event.KeyEvent;

public class KeyCodeConverter {
    // hopefully all of them are correct - yuro
    public static int convertToAwtKeyCode(int lwjglKeyCode) {
        switch (lwjglKeyCode) {
            case GLFW.GLFW_KEY_LEFT_ALT: return KeyEvent.VK_ALT;
            case GLFW.GLFW_KEY_0: return KeyEvent.VK_0;
            case GLFW.GLFW_KEY_1: return KeyEvent.VK_1;
            case GLFW.GLFW_KEY_2: return KeyEvent.VK_2;
            case GLFW.GLFW_KEY_3: return KeyEvent.VK_3;
            case GLFW.GLFW_KEY_4: return KeyEvent.VK_4;
            case GLFW.GLFW_KEY_5: return KeyEvent.VK_5;
            case GLFW.GLFW_KEY_6: return KeyEvent.VK_6;
            case GLFW.GLFW_KEY_7: return KeyEvent.VK_7;
            case GLFW.GLFW_KEY_8: return KeyEvent.VK_8;
            case GLFW.GLFW_KEY_9: return KeyEvent.VK_9;
            case GLFW.GLFW_KEY_A: return KeyEvent.VK_A;
            case GLFW.GLFW_KEY_KP_ADD: return KeyEvent.VK_ADD;
                        case GLFW.GLFW_KEY_B: return KeyEvent.VK_B;
            case GLFW.GLFW_KEY_C: return KeyEvent.VK_C;
                                                case GLFW.GLFW_KEY_COMMA: return KeyEvent.VK_COMMA;
                        case GLFW.GLFW_KEY_D: return KeyEvent.VK_D;
            case GLFW.GLFW_KEY_KP_DECIMAL: return KeyEvent.VK_DECIMAL;
            case GLFW.GLFW_KEY_DELETE: return KeyEvent.VK_DELETE;
            case GLFW.GLFW_KEY_KP_DIVIDE: return KeyEvent.VK_DIVIDE;
            case GLFW.GLFW_KEY_DOWN: return KeyEvent.VK_DOWN;
            case GLFW.GLFW_KEY_E: return KeyEvent.VK_E;
            case GLFW.GLFW_KEY_END: return KeyEvent.VK_END;
            case GLFW.GLFW_KEY_EQUAL:
            case GLFW.GLFW_KEY_KP_EQUAL: return KeyEvent.VK_EQUALS;
            case GLFW.GLFW_KEY_ESCAPE: return KeyEvent.VK_ESCAPE;
            case GLFW.GLFW_KEY_F: return KeyEvent.VK_F;
            case GLFW.GLFW_KEY_F1: return KeyEvent.VK_F1;
            case GLFW.GLFW_KEY_F10: return KeyEvent.VK_F10;
            case GLFW.GLFW_KEY_F11: return KeyEvent.VK_F11;
            case GLFW.GLFW_KEY_F12: return KeyEvent.VK_F12;
            case GLFW.GLFW_KEY_F13: return KeyEvent.VK_F13;
            case GLFW.GLFW_KEY_F14: return KeyEvent.VK_F14;
            case GLFW.GLFW_KEY_F15: return KeyEvent.VK_F15;
            case GLFW.GLFW_KEY_F16: return KeyEvent.VK_F16;
            case GLFW.GLFW_KEY_F17: return KeyEvent.VK_F17;
            case GLFW.GLFW_KEY_F18: return KeyEvent.VK_F18;
            case GLFW.GLFW_KEY_F19: return KeyEvent.VK_F19;
            case GLFW.GLFW_KEY_F2: return KeyEvent.VK_F2;
            case GLFW.GLFW_KEY_F3: return KeyEvent.VK_F3;
            case GLFW.GLFW_KEY_F4: return KeyEvent.VK_F4;
            case GLFW.GLFW_KEY_F5: return KeyEvent.VK_F5;
            case GLFW.GLFW_KEY_F6: return KeyEvent.VK_F6;
            case GLFW.GLFW_KEY_F7: return KeyEvent.VK_F7;
            case GLFW.GLFW_KEY_F8: return KeyEvent.VK_F8;
            case GLFW.GLFW_KEY_F9: return KeyEvent.VK_F9;
            case GLFW.GLFW_KEY_G: return KeyEvent.VK_G;
            case GLFW.GLFW_KEY_H: return KeyEvent.VK_H;
            case GLFW.GLFW_KEY_HOME: return KeyEvent.VK_HOME;
            case GLFW.GLFW_KEY_I: return KeyEvent.VK_I;
            case GLFW.GLFW_KEY_INSERT: return KeyEvent.VK_INSERT;
            case GLFW.GLFW_KEY_J: return KeyEvent.VK_J;
            case GLFW.GLFW_KEY_K: return KeyEvent.VK_K;
                                    case GLFW.GLFW_KEY_L: return KeyEvent.VK_L;
            case GLFW.GLFW_KEY_LEFT: return KeyEvent.VK_LEFT;
            case GLFW.GLFW_KEY_M: return KeyEvent.VK_M;
            case GLFW.GLFW_KEY_MINUS: return KeyEvent.VK_MINUS;
            case GLFW.GLFW_KEY_KP_MULTIPLY: return KeyEvent.VK_MULTIPLY;
            case GLFW.GLFW_KEY_N: return KeyEvent.VK_N;
            case GLFW.GLFW_KEY_KP_0: return KeyEvent.VK_NUMPAD0;
            case GLFW.GLFW_KEY_KP_1: return KeyEvent.VK_NUMPAD1;
            case GLFW.GLFW_KEY_KP_2: return KeyEvent.VK_NUMPAD2;
            case GLFW.GLFW_KEY_KP_3: return KeyEvent.VK_NUMPAD3;
            case GLFW.GLFW_KEY_KP_4: return KeyEvent.VK_NUMPAD4;
            case GLFW.GLFW_KEY_KP_5: return KeyEvent.VK_NUMPAD5;
            case GLFW.GLFW_KEY_KP_6: return KeyEvent.VK_NUMPAD6;
            case GLFW.GLFW_KEY_KP_7: return KeyEvent.VK_NUMPAD7;
            case GLFW.GLFW_KEY_KP_8: return KeyEvent.VK_NUMPAD8;
            case GLFW.GLFW_KEY_KP_9: return KeyEvent.VK_NUMPAD9;
            case GLFW.GLFW_KEY_O: return KeyEvent.VK_O;
            case GLFW.GLFW_KEY_P: return KeyEvent.VK_P;
            case GLFW.GLFW_KEY_PAUSE: return KeyEvent.VK_PAUSE;
            case GLFW.GLFW_KEY_PERIOD: return KeyEvent.VK_PERIOD;
            case GLFW.GLFW_KEY_Q: return KeyEvent.VK_Q;
            case GLFW.GLFW_KEY_R: return KeyEvent.VK_R;
            case GLFW.GLFW_KEY_RIGHT: return KeyEvent.VK_RIGHT;
            case GLFW.GLFW_KEY_S: return KeyEvent.VK_S;
            case GLFW.GLFW_KEY_SEMICOLON: return KeyEvent.VK_SEMICOLON;
            case GLFW.GLFW_KEY_SLASH: return KeyEvent.VK_SLASH;
            case GLFW.GLFW_KEY_SPACE: return KeyEvent.VK_SPACE;
                        case GLFW.GLFW_KEY_KP_SUBTRACT: return KeyEvent.VK_SUBTRACT;
            case GLFW.GLFW_KEY_T: return KeyEvent.VK_T;
            case GLFW.GLFW_KEY_TAB: return KeyEvent.VK_TAB;
            case GLFW.GLFW_KEY_U: return KeyEvent.VK_U;
            case GLFW.GLFW_KEY_UP: return KeyEvent.VK_UP;
            case GLFW.GLFW_KEY_V: return KeyEvent.VK_V;
            case GLFW.GLFW_KEY_W: return KeyEvent.VK_W;
            case GLFW.GLFW_KEY_X: return KeyEvent.VK_X;
            case GLFW.GLFW_KEY_Y: return KeyEvent.VK_Y;
            case GLFW.GLFW_KEY_Z: return KeyEvent.VK_Z;
            case GLFW.GLFW_KEY_APOSTROPHE: return KeyEvent.VK_QUOTE;
            case GLFW.GLFW_KEY_MENU: return KeyEvent.VK_CONTEXT_MENU;
            case GLFW.GLFW_KEY_BACKSPACE: return KeyEvent.VK_BACK_SPACE;
            case GLFW.GLFW_KEY_BACKSLASH: return KeyEvent.VK_BACK_SLASH;
            case GLFW.GLFW_KEY_CAPS_LOCK: return KeyEvent.VK_CAPS_LOCK;
            case GLFW.GLFW_KEY_GRAVE_ACCENT: return KeyEvent.VK_BACK_QUOTE;
            case GLFW.GLFW_KEY_LEFT_BRACKET: return KeyEvent.VK_OPEN_BRACKET;
            case GLFW.GLFW_KEY_LEFT_CONTROL: return KeyEvent.VK_CONTROL;
            case GLFW.GLFW_KEY_LEFT_SHIFT: return KeyEvent.VK_SHIFT;
            case GLFW.GLFW_KEY_LEFT_SUPER:
            case GLFW.GLFW_KEY_RIGHT_SUPER: return KeyEvent.VK_WINDOWS;
            case GLFW.GLFW_KEY_PAGE_DOWN: return KeyEvent.VK_PAGE_DOWN;
                        case GLFW.GLFW_KEY_NUM_LOCK: return KeyEvent.VK_NUM_LOCK;
                        case GLFW.GLFW_KEY_KP_ENTER: return KeyEvent.VK_ENTER;
            case GLFW.GLFW_KEY_PAGE_UP: return KeyEvent.VK_PAGE_UP;
            case GLFW.GLFW_KEY_RIGHT_BRACKET: return KeyEvent.VK_CLOSE_BRACKET;
            case GLFW.GLFW_KEY_SCROLL_LOCK: return KeyEvent.VK_SCROLL_LOCK;
            case GLFW.GLFW_KEY_PRINT_SCREEN: return KeyEvent.VK_PRINTSCREEN;
                        default: return KeyEvent.VK_UNDEFINED;
        }
    }
}
