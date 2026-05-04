# 高級擬真 Fake GPS App 實作計劃

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推薦）或 superpowers:executing-plans 逐任務實現此計劃。步驟使用複選框（`- [ ]`）語法來跟踪進度。

**目標：** 構建一個具備搖桿、路徑導航及高擬真走路演算法（15-20km/h、轉彎減速、座標抖動）的 Android App。

**架構：** 採用 MVVM 架構。使用 `MockLocationManager` 封裝系統 API，`MovementEngine` 處理擬真位移邏輯，`JoystickView` 處理 UI 互動，`MapSupport` 處理路徑規劃。

**技術棧：** Kotlin, Android SDK, Google Maps SDK, Coroutines.

---

### 任務 1：專案初始化與 Mock Location 基礎
**文件：**
- 創建：`app/src/main/java/com/fakegps/core/MockLocationManager.kt`
- 修改：`app/src/main/AndroidManifest.xml`

- [ ] **步驟 1：添加權限聲明**
在 Manifest 中添加 `ACCESS_MOCK_LOCATION` (僅開發偵錯用) 與 `ACCESS_FINE_LOCATION`。

- [ ] **步驟 2：實現基礎 MockProvider**
編寫代碼初始化 `FusedLocationProviderClient` 並設置測試模式。

```kotlin
class MockLocationManager(private val context: Context) {
    fun setMockLocation(lat: Double, lng: Double, alt: Double) {
        // 使用 LocationManager.setTestProviderLocation 實作
    }
}
```

- [ ] **步驟 3：Commit**
`git commit -m "chore: project init and mock location base"`

### 任務 2：擬真位移引擎 (The Movement Engine)
**文件：**
- 創建：`app/src/main/java/com/fakegps/engine/MovementEngine.kt`
- 測試：`app/src/test/java/com/fakegps/engine/MovementEngineTest.kt`

- [ ] **步驟 1：編寫速度控制測試**
驗證速度是否嚴格維持在 15-20 km/h 且轉彎時降速。

- [ ] **步驟 2：實作平滑位移演算法**
計算兩點間的插值，並引入隨機抖動 (Gaussian Drift)。

```kotlin
fun calculateNextStep(current: LatLng, target: LatLng, speedKmh: Double): LatLng {
    // 實現 15-20km/h 的位移邏輯
    // 加入轉彎檢測與降速邏輯
}
```

- [ ] **步驟 3：Commit**
`git commit -m "feat: implement high-fidelity movement engine"`

### 任務 3：虛擬搖桿 UI 與互動
**文件：**
- 創建：`app/src/main/res/layout/layout_joystick.xml`
- 創建：`app/src/main/java/com/fakegps/ui/JoystickView.kt`

- [ ] **步驟 1：設計搖桿介面**
使用自定義 View 繪製圓形搖桿。

- [ ] **步驟 2：對接移動引擎**
將搖桿的偏移量轉換為 `MovementEngine` 的速度指令。

- [ ] **步驟 3：Commit**
`git commit -m "feat: add interactive joystick UI"`

### 任務 4：路徑導航功能
**文件：**
- 修改：`app/src/main/java/com/fakegps/ui/MainActivity.kt`
- 創建：`app/src/main/java/com/fakegps/map/RoutePlanner.kt`

- [ ] **步驟 1：整合 Google Maps**
在地圖上支援長按取點，並顯示規劃的路徑線。

- [ ] **步驟 2：實現自動行走邏輯**
遍歷路徑點，調用 `MovementEngine` 按順序前進。

- [ ] **步驟 3：Commit**
`git commit -m "feat: implement route navigation"`
