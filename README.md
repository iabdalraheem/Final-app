# Smart Chessboard — Android App

The companion Android app for **Smart Chessboard**, an electronic chessboard that automatically detects piece movement and syncs live over Bluetooth Low Energy (BLE).

> Part of the Smart Chessboard graduation project — Control and Automation Engineering, Technical Engineering Institute (Mechanical and Electrical), University of Aleppo.
> Full project repository: [smart-chessboard](https://github.com/iabdalraheem/smartchessboard) <!-- replace # with your main repo link -->

---

## 📱 About

This app connects to the Smart Chessboard hardware over BLE, displays the live board state in real time, and keeps a local history of completed matches.

## ✨ Features

- **Live board sync** — real-time updates via BLE as pieces move
- **Match history** — automatically saved and browsable, stored locally
- **Mock mode** — full UI/UX testable without physical hardware, using a simulated BLE service

## 🏗️ Architecture

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Pattern:** MVVM (ViewModel + StateFlow)
- **Local storage:** Room database
- **Connectivity:** BLE — Nordic UART Service

**BLE Configuration:**
| Item | UUID |
|---|---|
| Service | `12345678-1234-1234-1234-123456789abc` |
| Notify characteristic | `12345678-1234-1234-1234-123456789ab1` |
| Device name | `SmartChessboard` |

## 🚀 Getting Started

1. Clone the repository
2. Open the project in **Android Studio**
3. Let Gradle sync dependencies
4. Run on a device or emulator

### Testing without hardware
The app includes a `MockBleChessService` that simulates board data, so the full UI/UX can be tested without a physical Smart Chessboard connected.

## 📂 Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/...     # ViewModels, UI (Compose), Room entities
│   │   └── res/         # Resources
└── build.gradle.kts
```

## 👥 Team

- **Raheem** — App & Firmware Development
- **Abdullah Taweel**
- **Amro Qadi Riha**
- **Sedra Zrek**

Supervised by **Eng. Heba Kharma**

## 📄 License

*(Add your preferred license here — MIT, Apache 2.0, etc.)*
