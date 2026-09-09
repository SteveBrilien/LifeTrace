# LifeTrace 数据模型草案

版本：v0.1

## 1. DiaryEntry

| 字段 | 类型 | 说明 |
|---|---|---|
| id | UUID | 客户端生成的稳定标识 |
| body | String | 日记正文 |
| occurredAt | Instant | 事件发生时间 |
| createdAt | Instant | 创建时间 |
| updatedAt | Instant | 最后修改时间 |
| latitude | Double? | 可空纬度 |
| longitude | Double? | 可空经度 |
| locationAccuracyM | Float? | 定位精度 |
| placeLabel | String? | 用户可编辑的地点名称 |
| revision | Long | 本地版本号 |
| syncState | Enum | LOCAL、PENDING、SYNCED、FAILED |
| deletedAt | Instant? | 删除墓碑时间 |

## 2. DiaryPhoto

| 字段 | 类型 | 说明 |
|---|---|---|
| id | UUID | 图片标识 |
| entryId | UUID | 所属日记 |
| localOriginalPath | String | 应用私有目录中的原图 |
| localThumbnailPath | String | 缩略图 |
| mimeType | String | 文件类型 |
| width / height | Int | 尺寸 |
| capturedAt | Instant? | 原始拍摄时间 |
| sortOrder | Int | 图片顺序 |
| sha256 | String | 完整性与去重辅助 |
| remoteObjectId | String? | 服务端密文对象 ID |
| syncState | Enum | 同步状态 |

## 3. TrackPoint

| 字段 | 类型 | 说明 |
|---|---|---|
| id | UUID | 轨迹点标识 |
| sessionId | UUID | 所属轨迹会话 |
| recordedAt | Instant | 采样时间 |
| latitude / longitude | Double | 坐标 |
| accuracyM | Float | 水平精度 |
| altitudeM | Double? | 海拔 |
| speedMps | Float? | 速度 |
| bearingDeg | Float? | 航向 |
| provider | String? | 定位来源 |
| isOutlier | Boolean | 是否被过滤 |
| syncState | Enum | 同步状态 |

## 4. TrackSession

| 字段 | 类型 | 说明 |
|---|---|---|
| id | UUID | 会话标识 |
| localDate | LocalDate | 用户时区下的日期 |
| startedAt / endedAt | Instant | 起止时间 |
| status | Enum | ACTIVE、PAUSED、COMPLETED |
| distanceM | Double | 过滤后的估算距离 |
| pointCount | Int | 有效点数量 |

## 5. SyncOperation

| 字段 | 类型 | 说明 |
|---|---|---|
| id | UUID | 操作标识 |
| entityType | Enum | ENTRY、PHOTO、TRACK_POINT 等 |
| entityId | UUID | 目标记录 |
| operation | Enum | UPSERT、DELETE |
| revision | Long | 对应版本 |
| attemptCount | Int | 重试次数 |
| nextAttemptAt | Instant? | 下次重试时间 |
| lastErrorCode | String? | 脱敏错误码 |

## 6. 关键约束

- DiaryEntry 与 DiaryPhoto 为一对多，最多 9 张有效图片。
- 删除日记时在同一事务中标记图片与同步墓碑。
- TrackPoint 按 sessionId、recordedAt 建复合索引。
- 地图查询以时间范围为主，不依赖服务端空间查询。
- 所有时间在存储层使用 UTC，显示时转换为设备时区。
- 经纬度必须与 accuracy 一同保存，避免把低精度点当作确定位置。
