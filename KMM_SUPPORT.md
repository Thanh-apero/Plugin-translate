# 🌐 KMM (Kotlin Multiplatform Mobile) Support

Plugin hiện đã hỗ trợ **Kotlin Multiplatform Mobile** projects với các cấu trúc module khác nhau.

## 🏗️ Supported KMM Structures

### 📱 Traditional Android
```
module/
├── src/main/res/values/strings.xml
└── src/main/res/values-vi/strings.xml
```

### 🌐 KMM Common Resources
```
shared/
├── src/commonMain/resources/strings.xml
└── src/commonMain/resources/values/strings.xml
```

### 📱 KMM Android-specific
```
shared/
├── src/androidMain/res/values/strings.xml
└── src/androidMain/res/values-vi/strings.xml
```

### 📲 KMM Android App
```
androidApp/
├── src/main/res/values/strings.xml
└── src/main/res/values-vi/strings.xml
```

## 🎯 Detection Logic

Plugin tự động detect và phân loại modules:

| Module Type | Icon | Description |
|-------------|------|-------------|
| Android | 📱 | Traditional Android module |
| KMM Common | 🌐 | Shared common resources |
| KMM Android | 📱 | Android-specific in shared |
| KMM App | 📲 | Android app module in KMM |

## 🚀 Workflow

### 1. **Auto Detection**
- Plugin scan toàn bộ project
- Detect tất cả module types
- Hiển thị trong dropdown với icons phân biệt

### 2. **Translation**
- Chọn module bất kỳ (Android hoặc KMM)
- Plugin tự động detect structure
- Translate và save vào đúng locations

### 3. **Add New Strings**
- Support tất cả module types
- Tự động tạo values folders nếu chưa có
- Handle KMM common resources properly

## 🔧 Technical Details

### Common Resources Handling
KMM common resources có thể có 2 structures:
1. `resources/values/strings.xml` (Android-style)
2. `resources/strings.xml` (flat structure)

Plugin detect và handle cả 2 cases.

### Output Paths
- **Android**: `module/src/main/res/values-{lang}/`
- **KMM Common**: `shared/src/commonMain/resources/values-{lang}/`
- **KMM Android**: `shared/src/androidMain/res/values-{lang}/`
- **KMM App**: `androidApp/src/main/res/values-{lang}/`

## 📦 Project Example

Typical KMM project structure được support:

```
MyKMMProject/
├── shared/
│   ├── src/
│   │   ├── commonMain/
│   │   │   └── resources/
│   │   │       └── values/strings.xml       # 🌐 KMM Common
│   │   └── androidMain/
│   │       └── res/
│   │           └── values/strings.xml       # 📱 KMM Android
├── androidApp/
│   └── src/main/res/
│       └── values/strings.xml               # 📲 KMM App
└── iosApp/
    └── iosApp/InfoPlist.strings            # (not supported yet)
```

## ✅ Verification

Sử dụng button **"ℹ️ Project Info"** để verify detection:
- Xem tất cả modules được detect
- Check module types và paths
- Confirm strings.xml locations

## 🎉 Benefits

1. **Unified workflow** cho cả Android và KMM
2. **Automatic detection** không cần config
3. **Type-aware translation** theo module structure  
4. **Visual indicators** để phân biệt module types
5. **Full KMM lifecycle support** từ development đến production 