# XML Translator Plugin - Installation & Usage Guide

## 🚀 Installation

### Method 1: From Plugin File (Recommended)

1. **Download the plugin**:
    - Get the file `xml-translator-plugin-1.0.0.zip` from the `build/distributions/` directory

2. **Install in Android Studio**:
    - Open Android Studio
    - Go to `File → Settings` (Windows/Linux) or `Android Studio → Preferences` (Mac)
    - Navigate to `Plugins`
    - Click the gear icon (⚙️) at the top
    - Select `Install Plugin from Disk...`
    - Choose the downloaded `xml-translator-plugin-1.0.0.zip` file
    - Click `OK`

3. **Restart Android Studio** to activate the plugin

### Method 2: Build from Source

```bash
git clone <your-repository-url>
cd xml-translator-plugin
./build.sh
```

Then follow Method 1 with the generated ZIP file.

## 🎯 Usage

### Opening the Plugin

After installation, you can access the XML Translator in several ways:

1. **Tool Window**: `View → Tool Windows → XML Translator`
2. **Main Menu**: `Tools → XML Translator`
3. **Right-click**: Right-click on any `strings.xml` file → `Translate XML File`

## 📝 Feature 1: Translate Complete XML Files

### Step-by-Step Guide

1. **Select the "Translate XML File" tab**

2. **Choose Input File**:
    - Click `Browse` next to "Input XML file"
    - Select your source `strings.xml` file (usually in `app/src/main/res/values/`)

3. **Choose Output Directory**:
    - Click `Browse` next to "Output directory"
    - Select your app's `res` directory (usually `app/src/main/res/`)

4. **Add Target Languages**:
    - **Quick Selection**: Click any language button (Vietnamese, Chinese, Korean, etc.)
    - **Manual Input**: Type language code (e.g., `vi`, `zh`, `ko`) and click `Add`
    - **Multiple Languages**: Add as many languages as you need

5. **Start Translation**:
    - Click `Translate All`
    - Monitor progress in the status area
    - Wait for completion message

### Example Output Structure

```
app/src/main/res/
├── values/
│   └── strings.xml (original)
├── values-vi/
│   └── strings.xml (Vietnamese)
├── values-zh/
│   └── strings.xml (Chinese)
└── values-ko/
    └── strings.xml (Korean)
```

## ➕ Feature 2: Add & Translate Individual Strings

### Step-by-Step Guide

1. **Select the "Add New String" tab**

2. **Add Strings**:
    - **Single String**:
        - Enter `Name`: e.g., `welcome_message`
        - Enter `Text`: e.g., `Welcome to our app!`
        - Click `Add to List`

    - **Bulk Input** (Advanced):
        - Paste XML strings in the text area:
      ```xml
      <string name="hello">Hello World</string>
      <string name="goodbye">Goodbye</string>
      ```
        - Click `Parse XML Strings`

3. **Select Output Directory**:
    - Click `Browse` next to "Resource directory"
    - Choose your app's `res` directory

4. **Choose Target Folders**:
    - The plugin automatically detects existing `values-*` folders
    - All folders are selected by default
    - Uncheck any folders you don't want to update

5. **Translate**:
    - Click `Translate All`
    - The plugin will add/update strings in all selected folders
    - Monitor progress in the status area

## 🌍 Supported Languages

The plugin supports 25+ languages with their respective codes:

| Language | Code | Language | Code |
|----------|------|----------|------|
| Vietnamese | `vi` | Arabic | `ar` |
| Chinese | `zh` | Bengali | `bn` |
| Korean | `ko` | Greek | `el` |
| Japanese | `ja` | Hindi | `hi` |
| Italian | `it` | Indonesian | `in` |
| French | `fr` | Marathi | `mr` |
| German | `de` | Malay | `ms` |
| Spanish | `es` | Portuguese | `pt` |
| Portuguese (Brazil) | `pt-rBR` | Russian | `ru` |
| Tamil | `ta` | Telugu | `te` |
| Thai | `th` | Turkish | `tr` |
| Filipino | `tl` | | |

## ⚙️ API Configuration

### Sử Dụng Ngay Lập Tức (Không Cần Cấu Hình)

🎉 **Plugin đã bao gồm API keys sẵn có** - bạn có thể sử dụng ngay mà không cần cấu hình gì thêm!

### Tùy Chỉnh API Keys (Tùy Chọn)

Nếu bạn muốn sử dụng API keys riêng hoặc có nhiều keys để tăng tốc độ:

#### Cách 1: Biến Môi Trường (Khuyến Nghị cho Dev)

1. **Lấy API Key:**
   - Truy cập https://aistudio.google.com/app/apikey
   - Đăng nhập với tài khoản Google
   - Tạo API key mới

2. **Thiết Lập Biến Môi Trường:**

   **Trên macOS/Linux:**
   ```bash
   export GOOGLE_AI_API_KEY="your_api_key_here"
   
   # Để lưu vĩnh viễn, thêm vào ~/.bashrc hoặc ~/.zshrc:
   echo 'export GOOGLE_AI_API_KEY="your_api_key_here"' >> ~/.zshrc
   ```

   **Trên Windows:**
   ```cmd
   set GOOGLE_AI_API_KEY=your_api_key_here
   
   # Hoặc thiết lập qua System Properties > Environment Variables
   ```

3. **Khởi Động Lại IDE** sau khi thiết lập biến môi trường

#### Cách 2: Thêm API Keys Bổ Sung

1. Mở `Settings/Preferences → Tools → XML Translator`
2. Giữ "Use default API keys" được chọn
3. Nhập thêm API keys vào ô "Additional API Keys" để tăng tốc độ

### Nhiều API Keys (Tăng Hiệu Suất)

Để tăng tốc độ và độ tin cậy, bạn có thể thiết lập nhiều API keys:

```bash
export GOOGLE_AI_API_KEY="key_1"
export GOOGLE_AI_API_KEY_1="key_2"  
export GOOGLE_AI_API_KEY_2="key_3"
```

### Cách Hoạt Động API Keys

- **Default keys**: Luôn được load từ plugin hoặc environment variables
- **Additional keys**: Được thêm vào kèm theo default keys (không thay thế)
- **Rotation**: Plugin tự động xoay vòng giữa các keys để tối ưu hiệu suất

### Rate Limiting

The plugin includes built-in rate limiting:

- 2-second delay between language translations
- Automatic retry on API failures
- Multiple API key rotation for better reliability

## 🔧 Troubleshooting

### Common Issues

1. **"Translation API request failed"**:
    - Check your internet connection
    - The API might be temporarily unavailable
    - Try again after a few minutes

2. **"No translatable strings found"**:
    - Ensure your XML file contains `<string>` elements
    - Check that strings don't have `translatable="false"`

3. **"Plugin not showing in menu"**:
    - Restart Android Studio
    - Check if plugin is enabled in Settings → Plugins

4. **"Build failed" when compiling plugin**:
    - Ensure Java 17+ is installed
    - Check internet connection for dependency downloads

### Plugin Logs

To check plugin logs:

1. Go to `Help → Show Log in Explorer/Finder`
2. Look for entries related to "XML Translator"

## 🎨 XML Formatting

The plugin automatically handles:

- **XML Escaping**: Special characters like `&`, `'`, `"` are properly escaped
- **HTML Tags**: Preserves formatting tags like `<b>`, `<i>`, etc.
- **Line Breaks**: Maintains `\n` and `\r` characters in translations
- **Whitespace**: Preserves intended spacing and indentation

## 📊 Performance Tips

1. **Batch Processing**: Process multiple strings at once for efficiency
2. **Language Selection**: Only select languages you actually need
3. **Internet Connection**: Ensure stable internet for API calls
4. **Large Files**: For files with 100+ strings, expect 2-3 minutes per language

## 🔒 Security & Privacy

- API calls are made directly to Google's servers
- No data is stored or logged by the plugin
- Your source strings are only sent for translation purposes
- Consider using your own API keys for sensitive projects

## 📞 Support

If you encounter issues:

1. Check this guide first
2. Look at the GitHub Issues page
3. Create a new issue with:
    - Android Studio version
    - Plugin version
    - Error message (if any)
    - Steps to reproduce

---

**Happy translating! 🌍✨**