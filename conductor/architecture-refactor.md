# 架構優化計劃：Foreground Service 集中管理 (MVVM + Service)

## Objective (目標)
解決 `MainActivity` 負擔過重 (Cognitive Overload) 以及與 `FloatingJoystickService` 重複初始化 `MockLocationManager` 和 `MovementEngine` 的問題。透過建立統一的 `CoreLocationService`，不僅能確保擬真位移引擎的唯一性，更能保障在背景執行時的穩定性，達到業界標準。

## Key Files & Context (關鍵檔案)
- `build.gradle`: 將加入 Coroutines 與 Lifecycle 相關依賴。
- `src/main/java/com/fakegps/core/CoreLocationService.kt` (New): 核心背景服務。
- `src/main/java/com/fakegps/ui/MainActivity.kt`: 負責 UI 呈現與地圖操作。
- `src/main/java/com/fakegps/ui/FloatingJoystickService.kt`: 搖桿 UI 控制。
- `src/main/AndroidManifest.xml`: 註冊 Service。

## Implementation Steps (實作步驟)

### Phase 1: 建立核心位置服務 (`CoreLocationService`)
1. 建立 `CoreLocationService` 繼承 `Service`，並實作 Foreground Service 的 Notification 機制。
2. 將 `MockLocationManager`、`MovementEngine` 與 `RoutePlanner` 集中在此初始化。
3. 實作 `LocalBinder`，以便 `MainActivity` 綁定並直接操作。
4. 使用 `CoroutineScope` (搭配 `kotlinx.coroutines.delay`) 取代傳統 `Handler.postDelayed` 來執行 15-20km/h 的自動行走迴圈。
5. 暴露 `StateFlow` 或提供 Callback 介面，廣播當前模擬座標，供 UI 層更新地圖 Marker。

### Phase 2: 重構 `MainActivity`
1. 將原本直接操作引擎的方法，改為透過 `bindService` 連接 `CoreLocationService` 後，呼叫 Service 提供的方法（例如：`startRoute(waypoints)`、`stopRoute()`、`setManualLocation(lat, lng)`）。
2. 收集來自 `CoreLocationService` 的位置更新流，即時刷新地圖上的 `userMarker`。
3. 清除所有與 `MockLocationManager`、`MovementEngine`、`Handler` 相關的屬性與邏輯，讓 `MainActivity` 回歸純 UI 組件的角色。

### Phase 3: 重構 `FloatingJoystickService`
1. 移除內部私自建立的 `MockLocationManager` 與 `MovementEngine`。
2. 透過發送 Intent （Action 指令）或同樣綁定 `CoreLocationService`，將搖桿計算出的位移偏移量傳遞給 `CoreLocationService` 進行抖動處理與座標寫入。
3. 簡化搖桿 Service 的負擔，僅處理 `WindowManager` 疊加層視圖。

## Verification & Testing (驗證)
- **編譯測試**: 確保 `build.gradle` 的依賴無衝突。
- **功能驗證**:
  1. 在 `MainActivity` 長按地圖點擊開始導航，App 退到背景後不中斷。
  2. 啟動懸浮搖桿時，不應與自動導航產生競爭。
  3. 確認雙方發送的座標皆經過 `MovementEngine.applyGaussianJitter`。
- **Brooks-Lint 複檢**: 確認 R1 (Cognitive Overload) 與 R3 (Knowledge Duplication) 皆已消除。