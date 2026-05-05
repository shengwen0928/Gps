# 架構優化計畫：v3.0.0 全方位動態物理模擬 (極致真實)

## Objective (目標)
吸收 `Neo-Navigator.apk` 的核心黑科技，將目前的 Fake GPS 專案升級為具備「系統級權威性」與「完美物理學擬真」的頂級應用。解決 Google Maps 定位不同步、權限誤報以及超速問題，確保模擬數據在 Android Fused Location 引擎中擁有絕對主導權。

## 關鍵技術特徵 (Key Technical Features)
1.  **動態衛星鎖定 (Dynamic Satellite Lock)**：模擬真實 GPS 冷啟動過程，衛星數量隨時間動態增加（例如 0 -> 4 -> 8 -> 12）。
2.  **主從式多重 Provider (Primary-Replica Providers)**：以 `GPS_PROVIDER` 為核心主控，`NETWORK_PROVIDER` 與 `fused` 為靜默從屬，失敗時不中斷主流程。
3.  **加速度張量運算 (Acceleration Tensor)**：優化 `BehaviorEngine`，實作真正意義上的物理加減速曲線，消除等速移動的機器人特徵。
4.  **高頻訊號微量化 (High-Frequency Micro-Jitter)**：在 5Hz 更新率下，將抖動拆分為極微量的平滑曲線，避免累積造成的跳躍超速。

## Key Files & Context (關鍵檔案)
- `src/main/java/com/fakegps/engine/BehaviorEngine.kt`: 新增動態衛星模擬與加速度曲線邏輯。
- `src/main/java/com/fakegps/core/MockLocationManager.kt`: 實作靜默降級與動態衛星數注入。
- `src/main/java/com/fakegps/core/CoreLocationService.kt`: 重構位移主循環，整合所有物理參數，並維持 `WakeLock` 背景保活。
- `build.gradle`: 版本升級至 v3.0.0。

## Implementation Steps (實作步驟)

### Phase 1: 強化行為引擎 (`BehaviorEngine.kt`)
1.  **新增 `calculateSatellites` 函式**：根據行走時間，返回一個遞增的衛星數量（模擬訊號逐漸穩定）。
2.  **優化 `calculateEasingSpeed`**：引入更平滑的 S 型曲線（Smoothstep）來計算速度，取代簡單的線性插值。
3.  **精煉抖動算法**：將高斯抖動改為基於前一次位置的相對平滑偏移，避免高頻下的大幅震盪。

### Phase 2: 重構定位管理器 (`MockLocationManager.kt`)
1.  **實作靜默降級**：在 `setupMockProvider` 中，嚴格區分核心 (`GPS`) 與非核心 (`fused`, `NETWORK`)。只有 `GPS` 失敗才回傳 false，其餘異常一律吃掉，徹底解決誤報 Toast。
2.  **動態 Metadata 寫入**：在 `setMockLocation` 中，接收並寫入動態的衛星數量 (`satellites`)。

### Phase 3: 升級核心服務 (`CoreLocationService.kt`)
1.  **整合動態衛星**：在 5Hz 的主循環中，每秒向 `BehaviorEngine` 索取當前的模擬衛星數。
2.  **邏輯與繪圖分離**：確立「邏輯點」與「繪製點（含抖動）」的分離機制。路徑進度計算必須基於純淨的邏輯點，而發送給系統的則是包含微量抖動與動態海拔的繪製點。
3.  **發送全資料包**：呼叫 `updateLocationFull` 時，夾帶精確計算的 `lat`, `lng`, `altitude`, `speed`, `bearing`, 以及 `satellites`。

### Phase 4: 版本迭代
1.  修改 `build.gradle`，將版本號推進至 `v3.0.0`，`versionCode` 提升至 18。

## Verification & Testing (驗證與測試)
1.  **權限測試**：啟動 App 時，確認只在未設定「模擬位置應用程式」時才會報錯，正常授權下不應有任何 Toast 彈出。
2.  **同步測試**：開啟 Google Maps，關閉 Wi-Fi 掃描與高精確度定位，確認藍色箭頭隨 App 行走平滑移動，無延遲與跳回。
3.  **物理特徵驗證**：觀察起步時，藍點是否呈現緩慢加速；中途是否會偶發極短暫停頓；抵達終點前是否平滑減速。

---
**嚴格開發規範承諾：**
所有修改將只透過 `replace` 手術式指令完成，確保專案乾淨整潔。